import { useEffect, useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Layout, Menu, Dropdown, Modal, Form, Input, Typography, notification, Space, message, Button } from 'antd'
import type { MenuProps } from 'antd'
import {
  AppstoreOutlined, AuditOutlined, BarChartOutlined, CarOutlined, CreditCardOutlined, FileSearchOutlined,
  FileTextOutlined, HomeOutlined, LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined, PieChartOutlined,
  QrcodeOutlined, SafetyOutlined, SearchOutlined, SettingOutlined, ShoppingOutlined, TeamOutlined, UserOutlined,
  VideoCameraOutlined, WifiOutlined, DashboardOutlined,
} from '@ant-design/icons'
import { useAuth } from '../auth/AuthContext'
import { get, put } from '../api/http'
import { MENU, linkVisible } from './menu'
import AppearanceButton from '../theme/AppearanceButton'
import './layout.css'

const ICONS: Record<string, React.ReactNode> = {
  home: <HomeOutlined />, setting: <SettingOutlined />, 'file-search': <FileSearchOutlined />,
  'bar-chart': <BarChartOutlined />, 'pie-chart': <PieChartOutlined />, 'credit-card': <CreditCardOutlined />,
  search: <SearchOutlined />, safety: <SafetyOutlined />, audit: <AuditOutlined />, 'file-text': <FileTextOutlined />,
  'video-camera': <VideoCameraOutlined />, appstore: <AppstoreOutlined />, car: <CarOutlined />,
  shopping: <ShoppingOutlined />, qrcode: <QrcodeOutlined />, wifi: <WifiOutlined />, team: <TeamOutlined />,
}

interface TankAlarm {
  id: number
  tankName: string
  errorMessage: string
}

export default function MainLayout() {
  const { me, logout, can } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)
  const [expiry, setExpiry] = useState('')
  const [accountOpen, setAccountOpen] = useState(false)
  const [form] = Form.useForm()
  const [api, contextHolder] = notification.useNotification()

  // Alarmes des cuves toutes les 10 s (Home/GetError d'origine)
  useEffect(() => {
    const poll = () =>
      get<TankAlarm[]>('/layout/alarms')
        .then((list) => {
          if (list.length > 0) {
            api.error({
              key: 'tank-alarm',
              message: 'Alarme',
              description: list.map((a) => `${a.tankName} : ${a.errorMessage}`).join('\n'),
              placement: 'bottomRight',
              duration: 1.8,
            })
          }
        })
        .catch(() => undefined)
    const t = window.setInterval(poll, 10000)
    return () => window.clearInterval(t)
  }, [api])

  // Bandeau défilant des appareils expirés (JoyFillRenew/GetExpiredNumber)
  useEffect(() => {
    if (!can('34')) return
    get<{ expiringSoon: number; expired: number }>('/layout/expired-devices')
      .then((r) => {
        let text = ''
        if (r.expiringSoon > 0) text += `${r.expiringSoon} appareil(s) expirent dans moins d'un mois, `
        if (r.expired > 0) text += `${r.expired} appareil(s) ont dépassé leur date d'expiration, `
        if (text) setExpiry(text + 'veuillez renouveler leur activation au plus vite.')
      })
      .catch(() => undefined)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me])

  const items: MenuProps['items'] = useMemo(() => {
    if (!me) return []
    const has = (id: string) => me.auth.includes(id)
    const out: NonNullable<MenuProps['items']> = [
      { key: '/', icon: <DashboardOutlined />, label: 'Accueil' },
    ]
    MENU.filter((s) => s.visible(me, has)).forEach((section, si) => {
      const groups = section.groups
        .filter((g) => !g.anyOf || g.anyOf.some(has))
        .map((g, gi) => ({
          key: `g-${si}-${gi}`,
          icon: ICONS[g.icon],
          label: g.label,
          children: g.items.filter((l) => linkVisible(me, l, has)).map((l) => ({ key: l.path, label: l.label })),
        }))
        .filter((g) => g.children.length > 0)
      if (groups.length > 0) {
        out.push({ type: 'group', key: `s-${si}`, label: collapsed ? '' : section.label, children: groups })
      }
    })
    return out
  }, [me, collapsed])

  const openAccount = async () => {
    try {
      const a = await get<{ userName: string }>('/layout/account')
      form.setFieldsValue({ userName: a.userName, password: '' })
      setAccountOpen(true)
    } catch {
      /* message déjà affiché */
    }
  }

  const saveAccount = async () => {
    const v = await form.validateFields()
    await put('/layout/account', v)
    message.success('Opération réussie')
    setAccountOpen(false)
  }

  const userMenu: MenuProps['items'] = [
    { key: 'account', icon: <SettingOutlined />, label: 'Paramètres du compte', onClick: openAccount },
    {
      key: 'logout', icon: <LogoutOutlined />, label: 'Déconnexion',
      onClick: async () => {
        await logout()
        navigate('/login')
      },
    },
  ]

  return (
    <Layout className="lt-layout">
      {contextHolder}
      <Layout.Sider width={250} collapsible collapsed={collapsed} trigger={null} className="lt-sider" theme="light">
        <div className="lt-brand" onClick={() => navigate('/')}>
          {collapsed ? 'LT' : me?.title ?? 'LTStation'}
        </div>
        {!collapsed && <div className="lt-welcome">Bienvenue, {me?.loginName}</div>}
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={items}
          onClick={(e) => navigate(e.key)}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header className="lt-header">
          <Button
            type="text"
            className="lt-toggle"
            aria-label={collapsed ? 'Déplier le menu' : 'Replier le menu'}
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <div className="lt-ticker" title={expiry} onClick={() => expiry && navigate('/main/active-device')}>
            {expiry && <span className="lt-ticker-text">{expiry}</span>}
          </div>
          <Space size="large">
            <Typography.Text strong>Plateforme de données</Typography.Text>
            <AppearanceButton />
            <Dropdown menu={{ items: userMenu }} placement="bottomRight">
              <Space style={{ cursor: 'pointer' }}>
                <UserOutlined />
                {me?.loginName}
              </Space>
            </Dropdown>
          </Space>
        </Layout.Header>
        <Layout.Content className="lt-content">
          <Outlet />
        </Layout.Content>
      </Layout>

      <Modal
        title="Informations du compte"
        open={accountOpen}
        onOk={saveAccount}
        onCancel={() => setAccountOpen(false)}
        okText="Enregistrer"
        cancelText="Annuler"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="userName" label="Nom d'utilisateur" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="Nouveau mot de passe" extra="Laisser vide pour ne pas le changer">
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  )
}
