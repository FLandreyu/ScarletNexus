const http = require('node:http')
const fs = require('node:fs/promises')
const path = require('node:path')
const { pipeline } = require('node:stream')

function localBackend(value) {
  const url = new URL(value)
  if (
    url.protocol !== 'http:' ||
    url.hostname !== '127.0.0.1' ||
    url.username ||
    url.password ||
    url.pathname !== '/' ||
    url.search ||
    url.hash
  )
    throw new Error('Expected an explicit loopback HTTP backend')
  return url
}

async function startGateway({ assets, backend }) {
  const upstream = localBackend(backend)
  const root = await fs.realpath(assets)
  const sockets = new Set()
  const server = http.createServer(async (req, res) => {
    try {
      const origin = `http://127.0.0.1:${server.address().port}`
      if (
        req.headers.host !== new URL(origin).host ||
        (req.headers.origin && req.headers.origin !== origin)
      ) {
        res.writeHead(403).end()
        return
      }
      const target = new URL(req.url, origin)
      if (target.origin !== origin) {
        res.writeHead(403).end()
        return
      }
      if (target.pathname.startsWith('/api/')) {
        const headers = { ...req.headers, host: upstream.host }
        for (const key of Object.keys(headers)) {
          if (
            key.startsWith('x-forwarded-') ||
            ['forwarded', 'connection', 'proxy-authorization'].includes(key)
          )
            delete headers[key]
        }
        if (headers.origin) headers.origin = upstream.origin
        const proxy = http.request(
          new URL(target.pathname + target.search, upstream),
          {
            method: req.method,
            headers,
            timeout: 135000,
          },
          (response) => {
            res.writeHead(response.statusCode, response.headers)
            pipeline(response, res, () => {})
          },
        )
        proxy.on('timeout', () => proxy.destroy())
        proxy.on('error', () => {
          if (!res.headersSent) res.writeHead(502)
          res.end()
        })
        res.on('close', () => proxy.destroy())
        pipeline(req, proxy, () => {})
        return
      }
      if (!['GET', 'HEAD'].includes(req.method)) {
        res.writeHead(405).end()
        return
      }
      if (
        target.pathname.startsWith('/actuator') ||
        target.pathname.startsWith('/v3/')
      ) {
        res.writeHead(404).end()
        return
      }
      const decoded = decodeURIComponent(target.pathname)
      if (
        decoded.includes('\\') ||
        decoded.includes('\0') ||
        decoded.split('/').some((p) => p === '..' || p.startsWith('.'))
      ) {
        res.writeHead(404).end()
        return
      }
      let file = path.join(root, decoded)
      try {
        file = await fs.realpath(file)
        if (!(await fs.stat(file)).isFile()) throw new Error('route')
      } catch {
        if (path.extname(decoded)) {
          res.writeHead(404).end()
          return
        }
        file = await fs.realpath(path.join(root, 'index.html'))
      }
      if (!file.startsWith(root + path.sep)) {
        res.writeHead(404).end()
        return
      }
      const type = {
        '.html': 'text/html; charset=utf-8',
        '.js': 'text/javascript',
        '.css': 'text/css',
        '.svg': 'image/svg+xml',
        '.png': 'image/png',
        '.woff2': 'font/woff2',
        '.woff': 'font/woff',
        '.ttf': 'font/ttf',
      }
      const content = await fs.readFile(file)
      res.writeHead(200, {
        'Content-Type': type[path.extname(file)] || 'application/octet-stream',
        'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff',
        'Content-Security-Policy':
          "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'",
      })
      res.end(req.method === 'HEAD' ? undefined : content)
    } catch {
      if (!res.headersSent) res.writeHead(400)
      res.end()
    }
  })
  server.on('connection', (socket) => {
    sockets.add(socket)
    socket.on('close', () => sockets.delete(socket))
  })
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  return {
    origin: `http://127.0.0.1:${server.address().port}`,
    close: () =>
      new Promise((resolve) => {
        server.close(resolve)
        for (const socket of sockets) socket.destroy()
      }),
  }
}
module.exports = { localBackend, startGateway }
