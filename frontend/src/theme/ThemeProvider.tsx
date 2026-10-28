import { createContext, useContext, useEffect, useLayoutEffect, useMemo, useState, type ReactNode } from 'react'
import { App as AntApp, ConfigProvider, theme } from 'antd'
import frFR from 'antd/locale/fr_FR'
import { buildTheme, CSS_VARS, HEADER_HEIGHT, type Density, type ModePreference, type ThemeMode } from './tokens'
import './theme.css'

interface ThemeState {
  /** Mode effectivement affiché */
  mode: ThemeMode
  /** Choix de l'utilisateur (« system » suit le réglage du poste) */
  modePref: ModePreference
  density: Density
  setModePref: (m: ModePreference) => void
  setDensity: (d: Density) => void
}

/** Même clé que le script d'index.html qui applique le thème avant le premier affichage. */
const STORAGE_KEY = 'lt-appearance'
const DEFAULTS = { modePref: 'system' as ModePreference, density: 'comfortable' as Density }

function readPrefs(): typeof DEFAULTS {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}')
    return {
      modePref: ['light', 'dark', 'system'].includes(saved.modePref) ? saved.modePref : DEFAULTS.modePref,
      density: ['comfortable', 'compact'].includes(saved.density) ? saved.density : DEFAULTS.density,
    }
  } catch {
    return DEFAULTS
  }
}

function writePrefs(p: typeof DEFAULTS) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(p))
  } catch {
    /* stockage indisponible : le choix vaut pour la session */
  }
}

/** Suit une media query (thème sombre du poste, animations réduites). */
function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches)
  useEffect(() => {
    const mq = window.matchMedia(query)
    const on = () => setMatches(mq.matches)
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [query])
  return matches
}

const Ctx = createContext<ThemeState | null>(null)

/** Recopie les jetons calculés par Ant Design en variables CSS (voir CSS_VARS). */
function TokenBridge({ density }: { density: Density }) {
  const { token } = theme.useToken()
  useLayoutEffect(() => {
    const style = document.documentElement.style
    Object.entries(CSS_VARS).forEach(([name, key]) => {
      const v = token[key]
      style.setProperty(name, typeof v === 'number' ? `${v}px` : String(v))
    })
    style.setProperty('--lt-header-height', `${HEADER_HEIGHT[density]}px`)
  }, [token, density])
  return null
}

/** Thème de l'application : clair / sombre / système, densité confortable / compacte. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [prefs, setPrefs] = useState(readPrefs)
  const systemDark = useMediaQuery('(prefers-color-scheme: dark)')
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
  const mode: ThemeMode = prefs.modePref === 'system' ? (systemDark ? 'dark' : 'light') : prefs.modePref

  useLayoutEffect(() => {
    const root = document.documentElement
    root.dataset.theme = mode
    root.dataset.density = prefs.density
  }, [mode, prefs.density])

  const config = useMemo(() => buildTheme(mode, prefs.density, !reducedMotion), [mode, prefs.density, reducedMotion])

  // Modal.confirm, message… appelés hors composant : même thème que l'application
  useEffect(() => {
    ConfigProvider.config({
      holderRender: (children) => <ConfigProvider locale={frFR} theme={config}>{children}</ConfigProvider>,
    })
  }, [config])

  const value = useMemo<ThemeState>(() => {
    const update = (patch: Partial<typeof DEFAULTS>) =>
      setPrefs((p) => {
        const next = { ...p, ...patch }
        writePrefs(next)
        return next
      })
    return {
      mode,
      modePref: prefs.modePref,
      density: prefs.density,
      setModePref: (modePref) => update({ modePref }),
      setDensity: (density) => update({ density }),
    }
  }, [mode, prefs])

  return (
    <Ctx.Provider value={value}>
      <ConfigProvider locale={frFR} theme={config}>
        <TokenBridge density={prefs.density} />
        <AntApp>{children}</AntApp>
      </ConfigProvider>
    </Ctx.Provider>
  )
}

const FALLBACK: ThemeState = {
  mode: 'light',
  modePref: 'light',
  density: 'comfortable',
  setModePref: () => undefined,
  setDensity: () => undefined,
}

/** Thème courant ; hors ThemeProvider (logiciel Station), valeurs claires par défaut. */
export function useAppTheme(): ThemeState {
  return useContext(Ctx) ?? FALLBACK
}
