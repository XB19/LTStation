import { useCallback, useEffect, useState } from 'react'
import { Alert, Card, Col, Empty, Form, Input, Row } from 'antd'
import { del, get, post, put } from '../../api/http'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'

type Row = Record<string, unknown>

/** Liste des caméras de surveillance (Main/MonitorDeviceList d'origine). */
export function MonitorDeviceList() {
  const load = useCallback(() => get<Row[]>('/monitor-devices'), [])
  return (
    <>
      <PageHeader title="Liste des appareils surveillés" section="Surveillance des appareils" />
      <Card>
        <CrudTable<Row>
          rowKey="id"
          load={load}
          addLabel="Ajouter une caméra"
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: "Nom de l'appareil", dataIndex: 'deviceName' },
            { title: 'Adresse du flux (ezopen://…)', dataIndex: 'deviceUrl' },
          ]}
          renderForm={() => (
            <>
              <Form.Item name="stationId" label="Station" rules={[{ required: true }]}>
                <RemoteSelect type="Station" style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="deviceName" label="Nom de l'appareil" rules={[{ required: true }]}><Input /></Form.Item>
              <Form.Item name="deviceUrl" label="Adresse du flux" rules={[{ required: true }]}><Input /></Form.Item>
            </>
          )}
          onCreate={(v) => post('/monitor-devices', v)}
          onUpdate={(r, v) => put(`/monitor-devices/${r.id}`, v)}
          onDelete={(r) => del(`/monitor-devices/${r.id}`)}
        />
      </Card>
    </>
  )
}

/** Mur d'écrans de vidéosurveillance EZVIZ (Main/MonitorDevice d'origine). */
export function MonitorDeviceWall() {
  const [devices, setDevices] = useState<Row[]>([])
  const [token, setToken] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    get<Row[]>('/monitor-devices').then(setDevices)
    get<{ accessToken: string }>('/monitor-devices/token')
      .then((r) => setToken(r.accessToken))
      .catch((e) => setError(e?.response?.data?.message ?? 'Jeton indisponible'))
  }, [])
  return (
    <>
      <PageHeader title="Surveillance des appareils" section="Surveillance des appareils" />
      {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 16 }} />}
      {devices.length === 0 ? (
        <Card><Empty description="Aucune caméra configurée" /></Card>
      ) : (
        <Row gutter={[16, 16]}>
          {devices.map((d) => (
            <Col key={String(d.id)} xs={24} lg={12} xxl={8}>
              <Card title={`${d.stationName ?? ''} — ${d.deviceName ?? ''}`} bodyStyle={{ padding: 0 }}>
                {token ? (
                  <iframe
                    title={String(d.deviceName)}
                    src={`https://open.ys7.com/ezopen/h5/iframe?url=${encodeURIComponent(String(d.deviceUrl))}&autoplay=1&accessToken=${token}`}
                    style={{ width: '100%', height: 300, border: 0 }}
                    allowFullScreen
                  />
                ) : (
                  <div style={{ height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>
                    Flux indisponible
                  </div>
                )}
              </Card>
            </Col>
          ))}
        </Row>
      )}
    </>
  )
}
