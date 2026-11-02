import { theme, type ThemeConfig } from 'antd'
import type { GlobalToken } from 'antd'

export type ThemeMode = 'light' | 'dark'
export type ModePreference = ThemeMode | 'system'
export type Density = 'comfortable' | 'compact'

/** Couleurs de la marque ZENER. Le vert est un accent : trop clair pour du texte sur fond clair. */
export const BRAND = {
  primary: '#1a70b5',
  navy: '#16305f',
  accent: '#9bc133',
  success: '#347434',
}

/** Jetons de départ communs : Ant Design en dérive toute la palette, en clair comme en sombre. */
const SEED: ThemeConfig['token'] = {
  colorPrimary: BRAND.primary,
  colorInfo: BRAND.primary,
  colorSuccess: BRAND.success,
  colorWarning: '#d97706',
  colorError: '#dc2626',
  borderRadius: 8,
  fontFamily: "'Segoe UI Variable Text', 'Segoe UI', system-ui, -apple-system, 'Helvetica Neue', Arial, sans-serif",
  wireframe: false,
}

/** Neutres ardoise propres à chaque mode. */
const BY_MODE: Record<ThemeMode, ThemeConfig['token']> = {
  light: {
    colorBgLayout: '#dfe8f2',
    colorBgContainer: '#f6f9fc',
    colorBgElevated: '#f6f9fc',
    colorText: '#12294f',
    colorTextSecondary: '#3c5070',
    colorTextTertiary: '#55688a',
    colorBorder: '#b9c9dc',
    colorBorderSecondary: '#d3dfeb',
  },
  dark: {
    colorBgLayout: '#0c1a33',
    colorBgContainer: '#13264a',
    colorBgElevated: '#1a3058',
    colorBorder: '#37507c',
    colorBorderSecondary: '#27406b',
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
      Layout: { headerHeight, siderBg: mode === 'dark' ? '#0a1630' : BRAND.navy },
      Menu: {
        itemBorderRadius: 8,
        darkItemBg: 'transparent',
        darkSubMenuItemBg: 'rgba(0, 0, 0, 0.16)',
        darkItemSelectedBg: BRAND.primary,
        darkItemColor: 'rgba(255, 255, 255, 0.82)',
        darkGroupTitleColor: 'rgba(255, 255, 255, 0.6)',
      },
      Table: {
        headerBorderRadius: 10,
        headerBg: mode === 'dark' ? '#1a3058' : '#d3e1ef',
        headerColor: mode === 'dark' ? '#d5e2f2' : BRAND.navy,
        headerSplitColor: 'transparent',
        rowHoverBg: mode === 'dark' ? '#1a3058' : '#e6eff8',
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
