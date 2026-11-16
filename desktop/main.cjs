const { app, BrowserWindow, dialog } = require('electron')
const path = require('node:path')
const { startGateway } = require('./gateway.cjs')
let window,
  gateway,
  closing = false
if (!app.requestSingleInstanceLock()) app.quit()
else {
  app.on('second-instance', () => {
    if (window) {
      if (window.isMinimized()) window.restore()
      window.show()
      window.focus()
    }
  })
  app
    .whenReady()
    .then(async () => {
      const argument = process.argv.find((a) => a.startsWith('--dev-backend='))
      if (app.isPackaged || !argument)
        throw new Error(
          'Desktop runtime packaging is not available in D1. Use the explicit development backend option.',
        )
      gateway = await startGateway({
        assets: path.join(__dirname, '../frontend/dist'),
        backend: argument.slice('--dev-backend='.length),
      })
      window = new BrowserWindow({
        title: 'Scarlet Nexus',
        width: 1280,
        height: 850,
        minWidth: 800,
        minHeight: 600,
        backgroundColor: '#121018',
        show: false,
        webPreferences: {
          nodeIntegration: false,
          contextIsolation: true,
          sandbox: true,
          webSecurity: true,
        },
      })
      window.removeMenu()
      window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
      window.webContents.on('will-navigate', (event, url) => {
        if (new URL(url).origin !== gateway.origin) event.preventDefault()
      })
      window.webContents.on('will-redirect', (event, url) => {
        if (new URL(url).origin !== gateway.origin) event.preventDefault()
      })
      window.webContents.on('will-attach-webview', (event) =>
        event.preventDefault(),
      )
      window.webContents.session.setPermissionRequestHandler(
        (_contents, _permission, callback) => callback(false),
      )
      window.webContents.session.setPermissionCheckHandler(() => false)
      window.once('ready-to-show', () => window.show())
      await window.loadURL(gateway.origin)
    })
    .catch((error) => {
      dialog.showErrorBox('Scarlet Nexus', error.message)
      app.quit()
    })
  app.on('window-all-closed', () => app.quit())
  app.on('before-quit', (event) => {
    if (!gateway || closing) return
    event.preventDefault()
    closing = true
    gateway.close().finally(() => app.quit())
  })
}
