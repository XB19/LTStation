import { theme, type ThemeConfig } from 'antd'
import type { GlobalToken } from 'antd'

export type ThemeMode = 'light' | 'dark'
export type ModePreference = ThemeMode | 'system'
export type Density = 'comfortable' | 'compact'

/** Couleurs de la marque (bandeau latéral, page de connexion). */
export const BRAND = {
  primary: '#2563eb',
  accent: '#0891b2',
}

/** Jetons de départ communs : Ant Design en dérive toute la palette, en clair comme en sombre. */
const SEED: ThemeConfig['token'] = {
  colorPrimary: BRAND.primary,
  colorInfo: BRAND.primary,
  colorSuccess: '#16a34a',
  colorWarning: '#d97706',
  colorError: '#dc2626',
  borderRadius: 8,
  fontFamily: "'Segoe UI Variable Text', 'Segoe UI', system-ui, -apple-system, 'Helvetica Neue', Arial, sans-serif",
  wireframe: false,
}

/** Neutres ardoise propres à chaque mode. */
const BY_MODE: Record<ThemeMode, ThemeConfig['token']> = {
  light: {
    colorBgLayout: '#f4f6fb',
    colorText: '#0f172a',
    colorTextSecondary: '#475569',
    colorTextTertiary: '#64748b',
    colorBorder: '#d5dce6',
    colorBorderSecondary: '#e7ebf2',
  },
  dark: {
    colorBgLayout: '#0b1120',
    colorBgContainer: '#131c2e',
    colorBgElevated: '#1a2438',
    colorBorder: '#334155',
    colorBorderSecondary: '#243047',
  },
}

/** Hauteur de l'en-tête et du bandeau de marque, selon la densité. */
export const HEADER_HEIGHT: Record<Density, number> = { comfortable: 64, compact: 52 }

export function buildTheme(mode: ThemeMode, density: Density, motion: boolean): ThemeConfig {
  const algorithm = [mode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm]
  if (density === 'compact') algorithm.push(theme.compactAlgorithm)
  const headerHeight = HEADER_HEIGHT[density]
  return {
    algorithm,
    token: { ...SEED, ...BY_MODE[mode], motion },
    components: {
      Layout: { headerHeight },
      Menu: { itemBorderRadius: 8, subMenuItemBg: 'transparent', itemSelectedColor: BRAND.primary },
      Table: {
        headerBorderRadius: 10,
        headerBg: mode === 'dark' ? '#182235' : '#f8fafc',
        headerColor: mode === 'dark' ? '#cbd5e1' : '#475569',
        headerSplitColor: 'transparent',
        rowHoverBg: mode === 'dark' ? '#1a2438' : '#f1f5fd',
      },
      Card: { borderRadiusLG: 12 },
      Button: { fontWeight: 500, primaryShadow: 'none', defaultShadow: 'none', dangerShadow: 'none' },
      Modal: { borderRadiusLG: 14 },
    },
  }
}

/**
 * Jetons Ant Design recopiés en variables CSS sur <html>, pour que les feuilles
 * de style maison suivent le thème et la densité. Les nombres sont en px.
 */
export const CSS_VARS: Record<string, keyof GlobalToken> = {
  '--lt-color-primary': 'colorPrimary',
  '--lt-color-primary-bg': 'colorPrimaryBg',
  '--lt-color-success': 'colorSuccess',
  '--lt-color-warning': 'colorWarning',
  '--lt-color-error': 'colorError',
  '--lt-color-text': 'colorText',
  '--lt-color-text-secondary': 'colorTextSecondary',
  '--lt-color-text-tertiary': 'colorTextTertiary',
  '--lt-color-bg-layout': 'colorBgLayout',
  '--lt-color-bg-container': 'colorBgContainer',
  '--lt-color-bg-elevated': 'colorBgElevated',
  '--lt-color-border': 'colorBorder',
  '--lt-color-border-secondary': 'colorBorderSecondary',
  '--lt-color-fill': 'colorFillTertiary',
  '--lt-radius': 'borderRadius',
  '--lt-radius-lg': 'borderRadiusLG',
  '--lt-space-xs': 'paddingXS',
  '--lt-space-sm': 'paddingSM',
  '--lt-space-md': 'padding',
  '--lt-space-lg': 'paddingLG',
  '--lt-space-xl': 'paddingXL',
  '--lt-font-size': 'fontSize',
  '--lt-font-size-sm': 'fontSizeSM',
  '--lt-font-size-lg': 'fontSizeLG',
}
