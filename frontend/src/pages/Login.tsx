import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Button, Card, Form, Input, Typography } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { useAuth } from '../auth/AuthContext'
import './login.css'

/** Page de connexion (GPRS/Login d'origine). */
export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)

  const submit = async (v: { user: string; password: string }) => {
    setLoading(true)
    try {
      await login(v.user, v.password)
      navigate('/')
    } catch {
      /* message affiché par l'intercepteur */
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="lt-login">
      <Card className="lt-login-card">
        <Typography.Title level={3} style={{ textAlign: 'center' }}>
          LTStation Center
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ textAlign: 'center' }}>
          Plateforme de gestion des stations
        </Typography.Paragraph>
        <Form onFinish={submit} size="large">
          <Form.Item name="user" rules={[{ required: true, message: "Saisissez le nom d'utilisateur" }]}>
            <Input prefix={<UserOutlined />} placeholder="Nom d'utilisateur" autoFocus />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: 'Saisissez le mot de passe' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="Mot de passe" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            Connexion
          </Button>
        </Form>
        <div style={{ textAlign: 'right', marginTop: 12 }}>
          <Link to="/activation">Activer la plateforme</Link>
        </div>
      </Card>
    </div>
  )
}
