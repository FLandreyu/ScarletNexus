const { _electron } = require('playwright')
const path = require('node:path')
const fs = require('node:fs/promises')
const assert = require('node:assert/strict')
process.chdir(path.resolve(__dirname, '../..'))
let origin
const profile = path.resolve(
  '.runtime/desktop-smoke-' + require('node:crypto').randomUUID(),
)
;(async () => {
  assert.ok(
    process.env.E2E_EMAIL?.endsWith('@example.test'),
    'Use an isolated example.test account',
  )
  assert.ok(process.env.E2E_PASSWORD_FILE, 'Set a test-only password file')
  const childEnv = { ...process.env }
  delete childEnv.ELECTRON_RUN_AS_NODE
  const application = await _electron.launch({
    executablePath: require('electron'),
    args: [
      path.resolve('desktop'),
      '--dev-backend=' + process.env.DESKTOP_TEST_BACKEND,
      '--user-data-dir=' + profile,
    ],
    env: childEnv,
    timeout: 60000,
  })
  try {
    const page = await application.firstWindow()
    page.setDefaultTimeout(30000)
    const errors = []
    page.on('pageerror', (e) => errors.push(e.message))
    await page.waitForURL('**/login')
    assert.equal(await page.evaluate(() => typeof require), 'undefined')
    const prefs = await application.evaluate(({ BrowserWindow }) => {
      const p =
        BrowserWindow.getAllWindows()[0].webContents.getLastWebPreferences()
      return {
        sandbox: p.sandbox,
        contextIsolation: p.contextIsolation,
        nodeIntegration: p.nodeIntegration,
      }
    })
    assert.deepEqual(prefs, {
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false,
    })
    const status = await page.evaluate(async () => {
      const r = await fetch('/api/v1/system/status')
      return { status: r.status, body: await r.json() }
    })
    assert.equal(status.status, 200)
    assert.equal(status.body.database, 'UP')
    await page.getByLabel('邮箱', { exact: true }).fill(process.env.E2E_EMAIL)
    await page
      .getByLabel('密码', { exact: true })
      .fill((await fs.readFile(process.env.E2E_PASSWORD_FILE, 'utf8')).trim())
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL('**/skills')
    origin = new URL(page.url()).origin
    await page.goto(origin + '/analytics')
    await page.waitForLoadState('networkidle')
    await page.screenshot({
      path: '.runtime/desktop-d1-analytics.png',
      fullPage: true,
    })
    assert.deepEqual(errors, [])
    await page.evaluate(() => {
      const a = document.createElement('a')
      a.id = 'external-smoke-link'
      a.href = 'https://example.com'
      a.textContent = 'external'
      document.body.append(a)
    })
    await page.locator('#external-smoke-link').click({ noWaitAfter: true })
    await page.waitForTimeout(300)
    assert.equal(new URL(page.url()).origin, origin)
    const second = require('node:child_process').spawn(
      require('electron'),
      [
        path.resolve('desktop'),
        '--dev-backend=' + process.env.DESKTOP_TEST_BACKEND,
        '--user-data-dir=' + profile,
      ],
      { env: childEnv, windowsHide: true, stdio: 'ignore' },
    )
    const secondCode = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        second.kill()
        reject(new Error('Second instance did not exit'))
      }, 10000)
      second.on('error', reject)
      second.on('exit', (code) => {
        clearTimeout(timer)
        resolve(code)
      })
    })
    assert.equal(secondCode, 0)
    assert.equal(
      await application.evaluate(
        ({ BrowserWindow }) => BrowserWindow.getAllWindows().length,
      ),
      1,
    )

    console.log(
      JSON.stringify({
        nativeElectronWindow: true,
        securityPreferences: prefs,
        realBackendStatus: status.body,
        authenticated: true,
        analyticsPage: true,
        externalNavigationBlocked: true,
        singleInstance: true,
        pageErrors: errors,
      }),
    )
  } finally {
    await application.close()
  }
  await assert.rejects(fetch(origin))
  console.log(JSON.stringify({ gatewayClosedAfterExit: true }))
})().catch((e) => {
  console.error(e)
  process.exitCode = 1
})
