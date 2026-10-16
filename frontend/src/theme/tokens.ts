import { theme, type ThemeConfig } from 'antd'
import type { GlobalToken } from 'antd'

export type ThemeMode = 'light' | 'dark'
export type ModePreference = ThemeMode | 'system'
export type Density = 'comfortable' | 'compact'

/** Couleurs de la marque (bandeau latéral, page de connexion). */
export const BRAND = {
  primary: '#1e88e5',
  accent: '#26c6da',
}

/** Jetons de départ communs : Ant Design en dérive toute la palette, en clair comme en sombre. */
const SEED: ThemeConfig['token'] = {
  colorPrimary: BRAND.primary,
  colorInfo: BRAND.primary,
  borderRadius: 8,
  wireframe: false,
}

/** Ajustements propres à chaque mode (le fond gris-bleu d'origine en clair). */
const BY_MODE: Record<ThemeMode, ThemeConfig['token']> = {
  light: { colorBgLayout: '#f3f3f9' },
  dark: {},
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
      Menu: { itemBorderRadius: 6 },
      Table: { headerBorderRadius: 8 },
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
