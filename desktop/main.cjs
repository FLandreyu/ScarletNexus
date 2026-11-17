const { app, BrowserWindow, dialog, ipcMain } = require("electron");
const path = require("node:path");
const fs = require("node:fs/promises");
const { pathToFileURL } = require("node:url");
const { startRuntime, validateAdmin } = require("./runtime.cjs");
const { startGateway } = require("./gateway.cjs");
app.setName("ScarletNexus");
app.setAppUserModelId("dev.scarletnexus.desktop");
let window,
  gateway,
  runtime,
  starting,
  closing = false;

async function setupAccount() {
  return new Promise((resolve, reject) => {
    const setupUrl = pathToFileURL(path.join(__dirname, "setup.html")).href;
    window = new BrowserWindow({
      title: "Scarlet Nexus",
      width: 640,
      height: 800,
      resizable: false,
      webPreferences: {
        preload: path.join(__dirname, "setup-preload.cjs"),
        nodeIntegration: false,
        contextIsolation: true,
        sandbox: true,
        webSecurity: true,
      },
    });
    window.removeMenu();
    window.webContents.session.setPermissionRequestHandler(
      (_contents, _permission, callback) => callback(false),
    );
    window.webContents.session.setPermissionCheckHandler(() => false);
    window.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
    window.webContents.on("will-navigate", (event, url) => {
      if (url !== setupUrl) event.preventDefault();
    });
    let accepted = false;
    ipcMain.handle("scarlet-desktop:setup", (event, account) => {
      if (
        accepted ||
        event.sender !== window?.webContents ||
        event.senderFrame !== window.webContents.mainFrame ||
        event.senderFrame.url !== setupUrl
      )
        return { accepted: false, error: "设置窗口已经失效。" };
      try {
        validateAdmin(account);
      } catch {
        return {
          accepted: false,
          error: "请输入有效邮箱和12字符至72个UTF-8字节的单行密码。",
        };
      }
      accepted = true;
      resolve(account);
      return { accepted: true };
    });
    window.once("closed", () => {
      ipcMain.removeHandler("scarlet-desktop:setup");
      if (!accepted) reject(new Error("Setup cancelled"));
    });
    window.loadURL(setupUrl).catch(reject);
  });
}
if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on("second-instance", () => {
    if (window && !window.isDestroyed() && !closing) {
      if (window.isMinimized()) window.restore();
      window.show();
      window.focus();
    }
  });
  app
    .whenReady()
    .then(async () => {
      const argument = process.argv.find((a) => a.startsWith("--dev-backend="));
      let backend, assets;
      if (argument) {
        if (app.isPackaged)
          throw new Error(
            "Development backends are forbidden in packaged builds",
          );
        backend = argument.slice("--dev-backend=".length);
        assets = path.join(__dirname, "../frontend/dist");
      } else {
        const override = process.argv.find((a) =>
          a.startsWith("--runtime-dir="),
        );
        const storage = process.argv.find((a) => a.startsWith("--data-dir="));
        if (app.isPackaged && (override || storage))
          throw new Error(
            "Development path overrides are forbidden in packaged builds",
          );
        const bundle = override
          ? path.resolve(override.slice("--runtime-dir=".length))
          : path.join(process.resourcesPath, "runtime");
        const directory = storage
          ? path.resolve(storage.slice("--data-dir=".length))
          : path.join(app.getPath("userData"), "learning");
        if (app.isPackaged) {
          const relative = path.relative(
            path.dirname(process.execPath),
            directory,
          );
          if (
            !relative ||
            (!relative.startsWith(".." + path.sep) &&
              !path.isAbsolute(relative))
          )
            throw new Error(
              "Learning data must be outside the installation directory",
            );
        }
        let account;
        try {
          await fs.access(path.join(directory, "administrator.json"));
        } catch {
          account = await setupAccount();
        }
        starting = startRuntime({
          bundle,
          directory,
          admin: account,
          onStatus: (message) => {
            if (window && !window.isDestroyed())
              window.webContents.send("scarlet-desktop:status", message);
          },
          onFailure: () => {
            if (runtime && !closing) {
              dialog.showErrorBox(
                "Scarlet Nexus",
                "学习服务意外停止，请重新启动软件。数据已保留。",
              );
              app.quit();
            }
          },
        });
        runtime = await starting;
        starting = undefined;
        if (closing) return;
        backend = runtime.origin;
        assets = runtime.assets;
      }
      gateway = await startGateway({
        assets,
        backend,
      });
      const setupWindow = window;
      window = new BrowserWindow({
        title: "Scarlet Nexus",
        width: 1280,
        height: 850,
        minWidth: 800,
        minHeight: 600,
        backgroundColor: "#121018",
        show: false,
        webPreferences: {
          nodeIntegration: false,
          contextIsolation: true,
          sandbox: true,
          webSecurity: true,
        },
      });
      window.removeMenu();
      window.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
      window.webContents.on("will-navigate", (event, url) => {
        if (new URL(url).origin !== gateway.origin) event.preventDefault();
      });
      window.webContents.on("will-redirect", (event, url) => {
        if (new URL(url).origin !== gateway.origin) event.preventDefault();
      });
      window.webContents.on("will-attach-webview", (event) =>
        event.preventDefault(),
      );
      window.webContents.session.setPermissionRequestHandler(
        (_contents, _permission, callback) => callback(false),
      );
      window.webContents.session.setPermissionCheckHandler(() => false);
      window.once("ready-to-show", () => {
        window.show();
        if (setupWindow && !setupWindow.isDestroyed()) setupWindow.close();
      });
      await window.loadURL(gateway.origin);
    })
    .catch((error) => {
      if (!closing) dialog.showErrorBox("Scarlet Nexus", error.message);
      app.quit();
    });
  app.on("window-all-closed", () => app.quit());
  app.on("before-quit", (event) => {
    if (closing) return;
    closing = true;
    if (!gateway && !runtime && !starting) return;
    event.preventDefault();
    Promise.resolve()
      .then(() => gateway?.close())
      .then(async () => (runtime || (await starting))?.stop())
      .catch(() => {
        dialog.showErrorBox(
          "Scarlet Nexus",
          "服务未能正常关闭，请保留数据目录并查看本机日志。",
        );
      })
      .finally(() => app.quit());
  });
}
