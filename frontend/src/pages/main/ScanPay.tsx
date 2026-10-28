import { useEffect, useState } from 'react'
import { Button, Card, Col, DatePicker, Empty, Form, Image, Input, InputNumber, Modal, Row, Select, Spin, Tag, Tooltip, message } from 'antd'
import { DownloadOutlined, RollbackOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { get, post } from '../../api/http'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import SearchTable, { serializeFilters } from '../../components/SearchTable'
import { fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>

const REFUND = [
  { value: 0, label: 'Aucun', color: 'default' },
  { value: 1, label: 'Demande acceptée', color: 'blue' },
  { value: 2, label: 'Demande refusée', color: 'red' },
  { value: 3, label: 'Remboursé', color: 'green' },
  { value: 4, label: 'Notification en échec', color: 'orange' },
]

/** Historique des paiements par QR code (Main/JoyFillScanPayTradeRecord d'origine). */
export function ScanPayRecords() {
  const [refund, setRefund] = useState<Row | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [form] = Form.useForm()
  return (
    <>
      <PageHeader title="Transactions par QR code" section="Paiement par QR code" />
      <Card>
        <SearchTable<Row>
          url="/scan-pay/orders" exportUrl="/scan-pay/orders/export" rowKey="id" reloadKey={reloadKey}
          toParams={(v) => {
            const { period, ...rest } = v as { period?: [dayjs.Dayjs, dayjs.Dayjs] }
            return serializeFilters({ ...rest, start: period?.[0], end: period?.[1] })
          }}
          filters={
            <>
              <Form.Item name="stationId" label="Station"><RemoteSelect type="Station" /></Form.Item>
              <Form.Item name="gunNumber" label="Pistolet"><InputNumber min={0} /></Form.Item>
              <Form.Item name="phoneNumber" label="Téléphone"><Input allowClear /></Form.Item>
              <Form.Item name="refundStatus" label="Remboursement"><Select allowClear style={{ width: 180 }} options={REFUND} /></Form.Item>
              <Form.Item name="period" label="Période"><DatePicker.RangePicker showTime /></Form.Item>
            </>
          }
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: 'Pistolet', dataIndex: 'gunNumber' },
            { title: 'Téléphone', dataIndex: 'phoneNumber' },
            { title: 'Payé', dataIndex: 'amount', render: (v) => fmtNum(v) },
            { title: 'Servi (montant)', dataIndex: 'realRefuelAmount', render: (v) => fmtNum(v) },
            { title: 'Servi (litres)', dataIndex: 'realRefuelLiters', render: (v) => fmtNum(v) },
            { title: 'Remboursement nécessaire', dataIndex: 'needRefund', render: (v) => (Number(v) === 1 ? 'Oui' : 'Non') },
            { title: 'Reste à rembourser', dataIndex: 'refundBalance', render: (v) => fmtNum(v) },
            {
              title: 'Remboursement', dataIndex: 'refundStatus',
              render: (v) => {
                const s = REFUND.find((x) => x.value === Number(v ?? 0))
                return <Tag color={s?.color}>{s?.label}</Tag>
              },
            },
            { title: 'Paiement', dataIndex: 'payTime', render: fmtDateTime },
            { title: 'N° marchand', dataIndex: 'outTradeNo' },
            { title: 'N° de remboursement', dataIndex: 'outRefundNo' },
            {
              title: 'Opération', key: 'op',
              render: (_, r) =>
                Number(r.refundBalance ?? 0) > 0 && ![1, 3].includes(Number(r.refundStatus ?? 0)) ? (
                  <Tooltip title="Rembourser">
                    <Button size="small" type="text" icon={<RollbackOutlined />} onClick={() => {
                      form.setFieldsValue({ amount: r.refundBalance })
                      setRefund(r)
                    }} />
                  </Tooltip>
                ) : null,
            },
          ]}
        />
      </Card>
      <Modal title="Remboursement manuel" open={!!refund} onCancel={() => setRefund(null)} okText="Rembourser" cancelText="Annuler"
        onOk={async () => {
          const v = await form.validateFields()
          await post(`/scan-pay/orders/${refund!.id}/refund`, v)
          message.success('Remboursement demandé')
          setRefund(null)
          setReloadKey((k) => k + 1)
        }}>
        <Form form={form} layout="vertical">
          <Form.Item name="amount" label="Montant à rembourser" rules={[{ required: true }]}>
            <InputNumber min={0.01} max={Number(refund?.amount ?? 0)} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

/** Génération des QR codes de paiement par pistolet (Main/JoyFillScanPayBarcodeCreate d'origine). */
export function ScanPayBarcode() {
  const [stationId, setStationId] = useState<number>()
  const [guns, setGuns] = useState<Row[]>([])
  const [codes, setCodes] = useState<Record<number, string>>({})
  const [loading, setLoading] = useState<number | null>(null)
  useEffect(() => {
    setCodes({})
    if (stationId == null) setGuns([])
    else get<Row[]>('/scan-pay/guns', { stationId }).then(setGuns)
  }, [stationId])
  const generate = async (gunId: number) => {
    setLoading(gunId)
    try {
      const r = await post<{ url: string }>('/scan-pay/barcode', { stationId, gunId })
      setCodes((c) => ({ ...c, [gunId]: `${r.url}?t=${Date.now()}` }))
    } finally {
      setLoading(null)
    }
  }
  return (
    <>
      <PageHeader title="Génération de QR code de paiement" section="Paiement par QR code" />
      <Card style={{ marginBottom: 16 }}>
        <RemoteSelect type="Station" placeholder="Choisir une station" value={stationId} onChange={setStationId} style={{ width: 300 }} />
      </Card>
      {guns.length === 0 ? (
        <Card><Empty description={stationId ? 'Aucun pistolet actif' : 'Choisissez une station'} /></Card>
      ) : (
        <Row gutter={[16, 16]}>
          {guns.map((g) => {
            const id = g.id as number
            return (
              <Col key={id} xs={24} sm={12} lg={8} xl={6}>
                <Card title={`Pistolet ${g.nozId} — ${g.oilName ?? ''}`} actions={[
                  <Button key="g" type="link" loading={loading === id} onClick={() => generate(id)}>Générer</Button>,
                  codes[id] && <a key="d" href={codes[id]} download={`qr_${stationId}_${id}.png`}><DownloadOutlined /> Télécharger</a>,
                ].filter(Boolean)}>
                  <div style={{ height: 220, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    {loading === id ? <Spin /> : codes[id] ? <Image src={codes[id]} height={200} /> : <span style={{ color: 'var(--lt-color-text-tertiary)' }}>Appareil {String(g.deviceNo)}</span>}
                  </div>
                </Card>
              </Col>
            )
          })}
        </Row>
      )}
    </>
  )
}
