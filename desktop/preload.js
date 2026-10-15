// Pont minimal entre la page de configuration et le processus principal (aucun accès Node pour l'interface web).
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('ltDesktop', {
  getConfig: () => ipcRenderer.invoke('config:get'),
  saveServer: (url) => ipcRenderer.invoke('config:save', url),
})
