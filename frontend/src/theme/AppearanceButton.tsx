import { Button, Dropdown, Tooltip } from 'antd'
import type { MenuProps } from 'antd'
import { ColumnHeightOutlined, CompressOutlined, DesktopOutlined, MoonOutlined, SunOutlined } from '@ant-design/icons'
import { useAppTheme } from './ThemeProvider'
import type { Density, ModePreference } from './tokens'

/** Bouton « Apparence » : thème (clair / sombre / selon le poste) et densité. */
export default function AppearanceButton({ size = 'middle' }: { size?: 'small' | 'middle' }) {
  const { mode, modePref, density, setModePref, setDensity } = useAppTheme()
  const menu: MenuProps = {
    selectable: true,
    multiple: true,
    selectedKeys: [`mode:${modePref}`, `density:${density}`],
    items: [
      {
        type: 'group', label: 'Thème', children: [
          { key: 'mode:light', icon: <SunOutlined />, label: 'Clair' },
          { key: 'mode:dark', icon: <MoonOutlined />, label: 'Sombre' },
          { key: 'mode:system', icon: <DesktopOutlined />, label: 'Selon le poste' },
        ],
      },
      { type: 'divider' },
      {
        type: 'group', label: 'Densité', children: [
          { key: 'density:comfortable', icon: <ColumnHeightOutlined />, label: 'Confortable' },
          { key: 'density:compact', icon: <CompressOutlined />, label: 'Compacte' },
        ],
      },
    ],
    onClick: ({ key }) => {
      const [kind, value] = key.split(':')
      if (kind === 'mode') setModePref(value as ModePreference)
      else setDensity(value as Density)
    },
  }
  return (
    <Dropdown menu={menu} trigger={['click']} placement="bottomRight">
      <Tooltip title="Apparence">
        <Button type="text" shape="circle" size={size} aria-label="Apparence" icon={mode === 'dark' ? <MoonOutlined /> : <SunOutlined />} />
      </Tooltip>
    </Dropdown>
  )
}
