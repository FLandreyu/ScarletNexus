const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("scarletSetup", {
  submit: (account) => ipcRenderer.invoke("scarlet-desktop:setup", account),
  onStatus: (callback) => {
    const listener = (_event, message) => callback(String(message));
    ipcRenderer.on("scarlet-desktop:status", listener);
    return () => ipcRenderer.removeListener("scarlet-desktop:status", listener);
  },
});
