const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const os = require('node:os')
const path = require('node:path')
const http = require('node:http')
const { localBackend, startGateway } = require('../gateway.cjs')

test('rejects remote services, credentials and URL configuration injection', () => {
  for (const url of [
    'https://127.0.0.1',
    'http://example.com',
    'http://127.0.0.1.evil.test',
    'http://user:secret@127.0.0.1',
    'http://127.0.0.1/api/',
    'http://127.0.0.1/?a=b',
  ]) {
    assert.throws(() => localBackend(url))
  }
  assert.equal(localBackend('http://127.0.0.1:8089').port, '8089')
})

test('real loopback gateway serves SPA, isolates files and streams API without buffering', async (t) => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'scarlet-desktop-'))
  await fs.writeFile(
    path.join(directory, 'index.html'),
    '<html>Scarlet Nexus</html>',
  )
  await fs.writeFile(path.join(directory, '.secret'), 'not accessible')
  t.after(() => fs.rm(directory, { recursive: true, force: true }))
  const backend = http.createServer((req, res) => {
    assert.equal(req.headers['x-forwarded-for'], undefined)
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Set-Cookie': 'SESSION=test; HttpOnly; SameSite=Lax',
    })
    res.write('data: first\n\n')
    setTimeout(() => res.end('data: last\n\n'), 250)
  })
  await new Promise((resolve) => backend.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise((resolve) => backend.close(resolve)))
  const gateway = await startGateway({
    assets: directory,
    backend: `http://127.0.0.1:${backend.address().port}`,
  })
  t.after(() => gateway.close())
  const page = await fetch(gateway.origin + '/study/example')
  assert.equal(page.status, 200)
  assert.match(await page.text(), /Scarlet Nexus/)
  assert.match(page.headers.get('content-security-policy'), /object-src 'none'/)
  for (const url of [
    '/.secret',
    '/%5c.secret',
    '/actuator/health',
    '/v3/api-docs',
  ]) {
    assert.equal((await fetch(gateway.origin + url)).status, 404)
  }
  assert.equal(
    (await fetch(gateway.origin, { headers: { Origin: 'https://evil.test' } }))
      .status,
    403,
  )
  const hostileHost = await new Promise((resolve, reject) => {
    const request = http.get(
      gateway.origin,
      { headers: { Host: 'evil.test' } },
      (response) => {
        response.resume()
        resolve(response.statusCode)
      },
    )
    request.on('error', reject)
  })
  assert.equal(hostileHost, 403)
  assert.equal((await fetch(gateway.origin, { method: 'POST' })).status, 405)
  const response = await fetch(gateway.origin + '/api/stream', {
    headers: { 'X-Forwarded-For': 'evil' },
  })
  assert.match(response.headers.get('set-cookie'), /HttpOnly/)
  const reader = response.body.getReader()
  assert.match(new TextDecoder().decode((await reader.read()).value), /first/)
  assert.match(new TextDecoder().decode((await reader.read()).value), /last/)
  assert.equal((await reader.read()).done, true)
})
