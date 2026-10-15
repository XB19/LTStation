// Application de bureau du logiciel Station : ouvre l'interface /station du serveur LTStation dans une fenêtre dédiée.
const { app, BrowserWindow, Menu, ipcMain, shell } = require('electron')
const fs = require('fs')
const path = require('path')

const SMOKE = process.argv.includes('--smoke')
const configFile = () => path.join(app.getPath('userData'), 'config.json')

function readConfig() {
  try {
    return { serverUrl: 'http://localhost:8080', ...JSON.parse(fs.readFileSync(configFile(), 'utf8')) }
  } catch {
    return { serverUrl: 'http://localhost:8080' }
  }
}

function writeConfig(cfg) {
  fs.mkdirSync(path.dirname(configFile()), { recursive: true })
  fs.writeFileSync(configFile(), JSON.stringify(cfg, null, 2))
}

const stationUrl = (cfg) => cfg.serverUrl.replace(/\/+$/, '') + '/station/'

let win

function showSettings(error) {
  win.loadFile(path.join(__dirname, 'settings.html'), { query: { url: readConfig().serverUrl, error: error || '' } })
}

function createWindow() {
  win = new BrowserWindow({
    width: 1366,
    height: 860,
    minWidth: 1024,
    minHeight: 680,
    show: !SMOKE,
    title: 'LTStation Station',
    backgroundColor: '#0b2f2c',
    autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, offscreen: SMOKE },
  })
  win.maximize()

  // Fenêtres d'impression (tickets, rapports) ouvertes par window.open dans l'application ; liens externes dans le navigateur
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url === 'about:blank' || url.startsWith(readConfig().serverUrl)) {
      return { action: 'allow', overrideBrowserWindowOptions: { width: 480, height: 700, autoHideMenuBar: true } }
    }
    shell.openExternal(url)
    return { action: 'deny' }
  })

  win.webContents.on('did-fail-load', (_e, code, desc, url) => {
    if (url && !url.startsWith('file:')) showSettings(`Serveur injoignable (${desc || code}) : ${url}`)
  })

  win.loadURL(stationUrl(readConfig())).catch(() => {}) // l'échec est traité par did-fail-load

  if (SMOKE) {
    // Mode test : capture de la fenêtre quelques secondes après le démarrage, puis fermeture
    setTimeout(async () => {
      const img = await win.webContents.capturePage()
      const out = path.join(app.getPath('temp'), 'ltstation-desktop-smoke.png')
      fs.writeFileSync(out, img.toPNG())
      console.log('SMOKE OK', win.webContents.getURL(), out)
      app.quit()
    }, 6000)
  }
}

ipcMain.handle('config:get', () => readConfig())
ipcMain.handle('config:save', (_e, serverUrl) => {
  const url = String(serverUrl || '').trim()
  if (!/^https?:\/\/.+/i.test(url)) throw new Error('Adresse invalide (exemple : http://192.168.1.10:8080)')
  writeConfig({ ...readConfig(), serverUrl: url })
  win.loadURL(stationUrl(readConfig())).catch(() => {})
})

app.whenReady().then(() => {
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    {
      label: 'Station',
      submenu: [
        { label: 'Adresse du serveur…', click: () => showSettings() },
        { label: 'Recharger', accelerator: 'F5', click: () => win.loadURL(stationUrl(readConfig())).catch(() => {}) },
        { type: 'separator' },
        { label: 'Plein écran', role: 'togglefullscreen' },
        { label: 'Quitter', role: 'quit' },
      ],
    },
  ]))
  createWindow()
})

app.on('window-all-closed', () => app.quit())
