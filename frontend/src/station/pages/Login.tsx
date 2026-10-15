import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Form, Input } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { useStation } from '../auth'

/** Connexion de l'employé (identifiant et mot de passe de sa fiche employé). */
export default function Login() {
  const { login } = useStation()
  const nav = useNavigate()
  const [loading, setLoading] = useState(false)
  return (
    <div className="st-login">
      <div className="st-login-hero">
        <div className="st-logo">LT</div>
        <div>
          <h1>LTStation<br />Station</h1>
          <p style={{ fontSize: 17, opacity: 0.85, maxWidth: 440, marginTop: 16 }}>
            Ventes de carburant, cartes clients, postes et rapports de votre station, au même endroit.
          </p>
        </div>
        <div style={{ opacity: 0.6 }}>Données synchronisées en temps réel avec le Center</div>
      </div>
      <div className="st-login-form">
        <Form layout="vertical" size="large" style={{ width: 360 }} onFinish={async (v) => {
          setLoading(true)
          try {
            await login(v.login, v.password)
            nav('/')
          } finally {
            setLoading(false)
          }
        }}>
          <h2 style={{ fontSize: 28, marginBottom: 4 }}>Connexion</h2>
          <p style={{ color: '#64748b', marginBottom: 28 }}>Identifiez-vous avec votre compte employé.</p>
          <Form.Item name="login" label="Identifiant" rules={[{ required: true, message: 'Saisissez votre identifiant' }]}>
            <Input prefix={<UserOutlined />} autoFocus autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="Mot de passe" rules={[{ required: true, message: 'Saisissez votre mot de passe' }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading} style={{ height: 50, marginTop: 8 }}>Se connecter</Button>
        </Form>
      </div>
    </div>
  )
}
