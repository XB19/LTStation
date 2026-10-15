import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, Button, Card, Form, Input, Typography, message } from 'antd'
import dayjs from 'dayjs'
import { get, post } from '../api/http'
import './login.css'

/** Activation de la plateforme par clé (GPRS/WebActive d'origine). */
export default function Activation() {
  const [state, setState] = useState<{ active: boolean; expiry: string | null } | null>(null)
  const [loading, setLoading] = useState(false)

  const load = () => get<{ active: boolean; expiry: string | null }>('/auth/license').then(setState)
  useEffect(() => {
    load()
  }, [])

  const submit = async (v: { code: string }) => {
    setLoading(true)
    try {
      const r = await post<{ expiry: string }>('/auth/activate', v)
      message.success(`Plateforme activée jusqu'au ${dayjs(r.expiry).format('DD/MM/YYYY HH:mm')}`)
      load()
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="lt-login">
      <Card className="lt-login-card">
        <Typography.Title level={4}>Activation de la plateforme</Typography.Title>
        {state && (
          <Alert
            style={{ marginBottom: 16 }}
            type={state.active ? 'success' : 'warning'}
            message={
              state.expiry
                ? `${state.active ? 'Active' : 'Expirée'} — échéance : ${dayjs(state.expiry).format('DD/MM/YYYY HH:mm')}`
                : "La plateforme n'est pas encore activée"
            }
          />
        )}
        <Form onFinish={submit}>
          <Form.Item name="code" rules={[{ required: true, message: "Saisissez la clé d'activation" }]}>
            <Input.TextArea rows={4} placeholder="Clé d'activation" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            Activer
          </Button>
        </Form>
        <div style={{ marginTop: 12 }}>
          <Link to="/login">Retour</Link>
        </div>
      </Card>
    </div>
  )
}
