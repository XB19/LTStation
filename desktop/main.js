// Application de bureau du logiciel Station, comme Station.exe d'origine : elle démarre le serveur LTStation intégré
// (Java et serveur fournis avec l'application), ouvre l'interface /station dans sa fenêtre et arrête le serveur à la fermeture.
// Un serveur distant peut aussi être utilisé (menu Station → Adresse du serveur).
const { app, BrowserWindow, Menu, ipcMain, shell, dialog } = require('electron')
const { spawn, execFile } = require('child_process')
const fs = require('fs')
const http = require('http')
const path = require('path')

const SMOKE = process.argv.includes('--smoke')
const configFile = () => path.join(app.getPath('userData'), 'config.json')
/** Dossier de travail du serveur intégré : .env (base de données), station/ (réglages, impressions, exports), logs/. */
const dataDir = () => path.join(app.getPath('userData'), 'data')
const envFile = () => path.join(dataDir(), '.env')
const logFile = () => path.join(dataDir(), 'logs', 'server.log')

const DEFAULTS = { mode: 'local', serverUrl: 'http://localhost:8080', port: 8080 }

function readConfig() {
  try {
    return { ...DEFAULTS, ...JSON.parse(fs.readFileSync(configFile(), 'utf8')) }
  } catch {
    return { ...DEFAULTS }
  }
}

function writeConfig(cfg) {
  fs.mkdirSync(path.dirname(configFile()), { recursive: true })
  fs.writeFileSync(configFile(), JSON.stringify(cfg, null, 2))
}

const baseUrl = (cfg) => (cfg.mode === 'local' ? `http://localhost:${cfg.port}` : cfg.serverUrl.replace(/\/+$/, ''))
const stationUrl = (cfg) => baseUrl(cfg) + '/station/'

// ------------------------------------------------------------ fichier .env du serveur intégré

const ENV_KEYS = ['DB_HOST', 'DB_PORT', 'DB_USER', 'DB_PASSWORD', 'DB_NAME', 'STATION_DB_NAME']

function readEnv() {
  const out = {}
  try {
    for (const line of fs.readFileSync(envFile(), 'utf8').replace(/^﻿/, '').split('\n')) {
      const m = /^\s*([A-Z_]+)\s*=(.*)$/.exec(line.replace(/\r/g, ''))
      if (m) out[m[1]] = m[2].trim().replace(/\\\\/g, '\\')
    }
  } catch {
    // pas encore configuré
  }
  return out
}

function writeEnv(values) {
  const cur = readEnv()
  const next = { ...cur }
  for (const k of ENV_KEYS) {
    const v = values[k]
    // Mot de passe laissé vide = inchangé
    if (k === 'DB_PASSWORD' && !v && cur.DB_PASSWORD) continue
    if (v !== undefined) next[k] = String(v).trim()
  }
  fs.mkdirSync(dataDir(), { recursive: true })
  const body = ['# Base de données du serveur LTStation intégré (ce fichier reste sur ce poste)']
    .concat(Object.entries(next).map(([k, v]) => `${k}=${String(v).replace(/\\/g, '\\\\')}`))
  fs.writeFileSync(envFile(), body.join('\r\n') + '\r\n')
}

// ------------------------------------------------------------ serveur intégré

/** Java et serveur : dans les ressources de l'application installée, ou dans le projet en développement. */
function serverFiles() {
  const root = app.isPackaged ? process.resourcesPath : __dirname
  const java = path.join(root, app.isPackaged ? 'runtime' : 'build/runtime', 'bin', 'java.exe')
  const jar = app.isPackaged ? path.join(root, 'server', 'center.jar') : path.join(__dirname, '..', 'backend', 'target', 'center-1.0.0.jar')
  return { java, jar }
}

let server = null
let serverLog = ''

function probe(url) {
  return new Promise((resolve) => {
    const req = http.get(url, (res) => { res.resume(); resolve(res.statusCode > 0 && res.statusCode < 500) })
    req.on('error', () => resolve(false))
    req.setTimeout(2000, () => { req.destroy(); resolve(false) })
  })
}

function startServer(cfg) {
  const { java, jar } = serverFiles()
  if (!fs.existsSync(java) || !fs.existsSync(jar)) throw new Error(`Serveur intégré introuvable (${!fs.existsSync(java) ? java : jar})`)
  fs.mkdirSync(path.dirname(logFile()), { recursive: true })
  const out = fs.openSync(logFile(), 'a')
  serverLog = ''
  server = spawn(java, ['-Dfile.encoding=UTF-8', '-jar', jar], {
    cwd: dataDir(),
    env: { ...process.env, SERVER_PORT: String(cfg.port) },
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  const keep = (b) => {
    try { fs.writeSync(out, b) } catch { /* journal fermé */ }
    serverLog = (serverLog + b.toString()).slice(-6000)
  }
  server.stdout.on('data', keep)
  server.stderr.on('data', keep)
  server.on('exit', () => {
    server = null
    try { fs.closeSync(out) } catch { /* déjà fermé */ }
  })
}

function stopServer() {
  return new Promise((resolve) => {
    if (!server) return resolve()
    // Arrêt de java.exe et de ses sous-processus éventuels
    execFile('taskkill', ['/PID', String(server.pid), '/T', '/F'], () => resolve())
  })
}

/** Démarre (ou retrouve) le serveur intégré puis ouvre /station ; affiche la configuration si la base n'est pas renseignée. */
async function openLocal() {
  const cfg = readConfig()
  const env = readEnv()
  if (!env.DB_PASSWORD && !env.DB_USER) return showSetup()
  if (await probe(stationUrl(cfg))) return win.loadURL(stationUrl(cfg)).catch(() => {})
  showLoading('Démarrage du serveur…')
  try {
    if (!server) startServer(cfg)
  } catch (e) {
    return showSetup(e.message)
  }
  for (let i = 0; i < 180; i++) {
    if (!server) {
      const lines = serverLog.split(/\r?\n/).filter((l) => /ERROR|Exception|Caused by|FAILED/.test(l)).slice(-4).join('\n')
      return showSetup(`Le serveur s'est arrêté au démarrage. ${lines || 'Voir le journal du serveur.'}`)
    }
    if (await probe(stationUrl(cfg))) return win.loadURL(stationUrl(cfg)).catch(() => {})
    await new Promise((r) => setTimeout(r, 1000))
  }
  showSetup('Le serveur ne répond pas après 3 minutes. Voir le journal du serveur.')
}

// ------------------------------------------------------------ fenêtre

let win

function showSettings(error) {
  win.loadFile(path.join(__dirname, 'settings.html'), { query: { url: readConfig().serverUrl, error: error || '' } })
}

function showSetup(error) {
  win.loadFile(path.join(__dirname, 'setup.html'), { query: { error: error || '' } })
}

function showLoading(text) {
  win.loadFile(path.join(__dirname, 'loading.html'), { query: { text } })
}

function openStation() {
  const cfg = readConfig()
  if (cfg.mode === 'local') return openLocal()
  win.loadURL(stationUrl(cfg)).catch(() => {}) // l'échec est traité par did-fail-load
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
    if (url === 'about:blank' || url.startsWith(baseUrl(readConfig()))) {
      return { action: 'allow', overrideBrowserWindowOptions: { width: 480, height: 700, autoHideMenuBar: true } }
    }
    shell.openExternal(url)
    return { action: 'deny' }
  })

  win.webContents.on('did-fail-load', (_e, code, desc, url) => {
    if (!url || url.startsWith('file:')) return
    if (readConfig().mode === 'local') showSetup(`Serveur intégré injoignable (${desc || code})`)
    else showSettings(`Serveur injoignable (${desc || code}) : ${url}`)
  })

  openStation()

  if (SMOKE) {
    // Mode test : capture de la fenêtre une fois l'interface chargée (ou après 3 minutes), puis fermeture
    const started = Date.now()
    const check = setInterval(async () => {
      const url = win.webContents.getURL()
      if ((url.startsWith('http') && !win.webContents.isLoading()) || Date.now() - started > 180000) {
        clearInterval(check)
        await new Promise((r) => setTimeout(r, 5000))
        const img = await win.webContents.capturePage()
        const out = path.join(app.getPath('temp'), 'ltstation-desktop-smoke.png')
        fs.writeFileSync(out, img.toPNG())
        console.log('SMOKE OK', win.webContents.getURL(), out)
        app.quit()
      }
    }, 1000)
  }
}

ipcMain.handle('config:get', () => readConfig())
ipcMain.handle('config:save', async (_e, serverUrl) => {
  const url = String(serverUrl || '').trim()
  if (!/^https?:\/\/.+/i.test(url)) throw new Error('Adresse invalide (exemple : http://192.168.1.10:8080)')
  writeConfig({ ...readConfig(), mode: 'remote', serverUrl: url })
  await stopServer()
  openStation()
})
ipcMain.handle('setup:get', () => {
  const env = readEnv()
  return {
    DB_HOST: env.DB_HOST || 'localhost', DB_PORT: env.DB_PORT || '5432', DB_USER: env.DB_USER || 'postgres',
    DB_NAME: env.DB_NAME || 'center', STATION_DB_NAME: env.STATION_DB_NAME || 'station', hasPassword: !!env.DB_PASSWORD,
    port: readConfig().port, dataDir: dataDir(),
  }
})
ipcMain.handle('setup:save', async (_e, values) => {
  const port = Number(values.port)
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Port du serveur invalide')
  if (!values.DB_PASSWORD && !readEnv().DB_PASSWORD) throw new Error('Mot de passe PostgreSQL obligatoire')
  writeEnv(values)
  writeConfig({ ...readConfig(), mode: 'local', port })
  await stopServer()
  openStation()
})

function menu() {
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    {
      label: 'Station',
      submenu: [
        { label: 'Base de données (serveur intégré)…', click: async () => { await stopServer(); showSetup() } },
        { label: 'Adresse du serveur (serveur distant)…', click: () => showSettings() },
        { label: 'Journal du serveur', click: () => shell.openPath(logFile()) },
        { label: 'Dossier des données', click: () => shell.openPath(dataDir()) },
        { label: 'Recharger', accelerator: 'F5', click: () => openStation() },
        { type: 'separator' },
        { label: 'Plein écran', role: 'togglefullscreen' },
        { label: 'Quitter', role: 'quit' },
      ],
    },
  ]))
}

// Une seule instance : un second lancement remet la fenêtre existante au premier plan
if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (win) {
      if (win.isMinimized()) win.restore()
      win.focus()
    }
  })
  app.whenReady().then(() => {
    fs.mkdirSync(dataDir(), { recursive: true })
    menu()
    createWindow()
  })
}

let quitting = false
app.on('before-quit', (e) => {
  if (quitting || !server) return
  e.preventDefault()
  quitting = true
  stopServer().then(() => app.quit())
})

app.on('window-all-closed', () => app.quit())

process.on('uncaughtException', (err) => {
  dialog.showErrorBox('LTStation Station', String(err && err.stack ? err.stack : err))
})
