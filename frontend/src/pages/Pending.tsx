import { Result } from 'antd'
import { useLocation } from 'react-router-dom'
import { MENU } from '../layout/menu'

/** Écran affiché pour un module dont le portage n'est pas encore terminé. */
export default function Pending() {
  const { pathname } = useLocation()
  const label =
    MENU.flatMap((s) => s.groups.flatMap((g) => g.items)).find((l) => l.path === pathname)?.label ?? pathname
  return <Result status="info" title={label} subTitle="Ce module est en cours de portage depuis la plateforme d'origine." />
}
