import { useCallback, useState } from 'react'
import { Button, Card, DatePicker, Form, Modal, QRCode, Tooltip, Typography } from 'antd'
import { EditOutlined, QrcodeOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { get, put } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'
import StationFilter, { type StationFilterValue } from '../../components/StationFilter'

type Row = Record<string, unknown>

/** Activation d'appareils par paiement WeChat (Main/ActiveDevice d'origine). */
export default function ActiveDevice() {
  const { me } = useAuth()
  const [filter, setFilter] = useState<StationFilterValue>({})
  const [qr, setQr] = useState<string | null>(null)
  const [edit, setEdit] = useState<Row | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [form] = Form.useForm()
  const load = useCallback(() => get<Row[]>('/air-devices/activation', filter), [filter])
  const isSystem = me?.userId === -1

  const showQr = async (r: Row) => {
    const { link } = await get<{ link: string }>(`/air-devices/${r.id}/activation-link`)
    setQr(link)
  }

  const saveTime = async () => {
    const v = await form.validateFields()
    await put(`/air-devices/${edit!.id}/valid-time`, { validTime: dayjs(v.validTime).format('YYYY-MM-DD HH:mm:ss') })
    setEdit(null)
    setReloadKey((k) => k + 1)
  }

  return (
    <>
      <PageHeader title="Activation d'appareils" section="Paramètres de base" />
      <Card>
        <CrudTable<Row>
          rowKey="id"
          load={load}
          reloadKey={reloadKey}
          toolbar={<StationFilter value={filter} onChange={setFilter} />}
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: 'N° de pistolet', dataIndex: 'nozId' },
            { title: "N° d'appareil", dataIndex: 'deviceNo' },
            { title: 'Validité', dataIndex: 'validTime' },
          ]}
          extraActions={(r) => (
            <>
              <Tooltip title="Activer par QR code">
                <Button size="small" type="text" icon={<QrcodeOutlined />} onClick={() => showQr(r)} />
              </Tooltip>
              {isSystem && (
                <Tooltip title="Modifier la date">
                  <Button
                    size="small"
                    type="text"
                    icon={<EditOutlined />}
                    onClick={() => {
                      form.setFieldsValue({ validTime: r.validTime ? dayjs(r.validTime as string) : dayjs() })
                      setEdit(r)
                    }}
                  />
                </Tooltip>
              )}
            </>
          )}
        />
      </Card>
      <Modal title="Activation par QR code" open={qr !== null} onCancel={() => setQr(null)} footer={null}>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
          {qr && <QRCode value={qr} size={320} color="#94771d" />}
          <Typography.Text type="secondary">Scannez avec WeChat pour payer le renouvellement de l'appareil.</Typography.Text>
        </div>
      </Modal>
      <Modal
        title="Date de validité"
        open={edit !== null}
        onOk={saveTime}
        onCancel={() => setEdit(null)}
        okText="Enregistrer"
        cancelText="Annuler"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="validTime" label="Validité" rules={[{ required: true }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
