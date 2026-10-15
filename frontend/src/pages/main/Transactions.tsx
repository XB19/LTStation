import { useEffect, useState } from 'react'
import { Button, Card, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Typography, message } from 'antd'
import ReactECharts from 'echarts-for-react'
import dayjs from 'dayjs'
import { get, post } from '../../api/http'
import PageHeader from '../../components/PageHeader'
import RemoteSelect, { useSelectOptions } from '../../components/RemoteSelect'
import SearchTable, { serializeFilters } from '../../components/SearchTable'
import StationFilter from '../../components/StationFilter'
import { fmtNum } from '../../utils/format'

type Row = Record<string, unknown>

export const MONTHS = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre',
  'Novembre', 'Décembre'].map((label, i) => ({ value: i + 1, label }))

const CARD_TYPES = [
  { value: 0, label: 'Carte employé' },
  { value: 1, label: 'Carte utilisateur' },
  { value: 2, label: 'Carte de crédit' },
  { value: 3, label: 'Utilisateur application' },
  { value: 5, label: 'Sans carte' },
  { value: 4, label: 'Paiement par QR code' },
]

/** Convertit les filtres du formulaire en paramètres d'API (période ou mois, station, etc.). */
export function transactionParams(v: Record<string, unknown>) {
  const { period, station, ...rest } = v as { period?: [dayjs.Dayjs, dayjs.Dayjs]; station?: Record<string, unknown> }
  return serializeFilters({
    ...rest,
    ...(station ?? {}),
    start: period?.[0],
    end: period?.[1],
  })
}

function SalesChart({ station }: { station?: Record<string, unknown> }) {
  const [type, setType] = useState(0)
  const [date, setDate] = useState(dayjs())
  const [data, setData] = useState<{ labels: string[]; amount: number[]; volume: number[] } | null>(null)
  useEffect(() => {
    get<{ labels: string[]; amount: number[]; volume: number[] }>('/transactions/chart', {
      type, date: date.format('YYYY-MM-DD'), ...(station ?? {}),
    }).then(setData)
  }, [type, date, station])
  return (
    <Card size="small" style={{ marginBottom: 16 }} title="Graphique"
      extra={
        <Space>
          <Select value={type} onChange={setType} style={{ width: 170 }} options={[
            { value: 0, label: 'Rapport horaire' }, { value: 1, label: 'Rapport journalier' },
            { value: 2, label: 'Rapport mensuel' }, { value: 3, label: 'Rapport annuel' },
          ]} />
          <DatePicker value={date} onChange={(d) => d && setDate(d)} picker={type === 0 ? 'date' : type === 1 ? 'month' : 'year'} />
        </Space>
      }>
      {data && (
        <ReactECharts style={{ height: 260 }} option={{
          tooltip: { trigger: 'axis' },
          legend: { data: ['Montant', 'Volume'] },
          grid: { left: 50, right: 20, bottom: 30, top: 40 },
          xAxis: { type: 'category', data: data.labels },
          yAxis: { type: 'value' },
          series: [
            { name: 'Montant', type: 'line', smooth: true, areaStyle: { opacity: 0.1 }, data: data.amount },
            { name: 'Volume', type: 'line', smooth: true, areaStyle: { opacity: 0.1 }, data: data.volume },
          ],
        }} />
      )}
    </Card>
  )
}

/** Transactions des stations (Main/Transaction d'origine). */
export default function Transactions() {
  const [form] = Form.useForm()
  const [retrieve, setRetrieve] = useState(false)
  const [rForm] = Form.useForm()
  const station = Form.useWatch('station', form)
  const cars = useSelectOptions('CardNumSearch')

  const sendRetrieve = async () => {
    await post('/transactions/retrieve', await rForm.validateFields())
    message.success('Demande envoyée')
    setRetrieve(false)
  }

  return (
    <>
      <PageHeader title="Transactions des stations" section="Consultation des enregistrements" />
      <SalesChart station={station} />
      <Card>
        <SearchTable<Row>
          url="/transactions"
          exportUrl="/transactions/export"
          rowKey="id"
          pageSize={15}
          form={form}
          initialFilters={{ month: dayjs().month() + 1, station: {} }}
          toParams={transactionParams}
          extraButtons={<Button onClick={() => setRetrieve(true)}>Récupérer des transactions</Button>}
          filters={
            <>
              <Form.Item name="period" label="Période"><DatePicker.RangePicker showTime /></Form.Item>
              <Form.Item name="month" label="Mois"><Select allowClear style={{ width: 130 }} options={MONTHS} /></Form.Item>
              <Form.Item name="station"><StationFilter /></Form.Item>
              <Form.Item name="oilCode" label="Énergie"><RemoteSelect type="OilType" /></Form.Item>
              <Form.Item name="person" label="Porteur">
                <RemoteSelect type="CustomerName" textAsValue />
              </Form.Item>
              <Form.Item name="cardType" label="Type de carte"><Select allowClear style={{ width: 190 }} options={CARD_TYPES} /></Form.Item>
              <Form.Item name="carNumber" label="Immatriculation">
                <Select allowClear showSearch style={{ width: 160 }} options={cars.map((c) => ({ value: c.text, label: c.text }))} />
              </Form.Item>
            </>
          }
          summary={(r) => (
            <Typography.Text strong>
              Montant total : {fmtNum(r.totalAmount)} — Volume total : {fmtNum(r.totalVolume)}
            </Typography.Text>
          )}
          columns={[
            { title: 'N° de carte', dataIndex: 'asn' },
            { title: 'Station', dataIndex: 'stationName' },
            { title: 'Pistolet', dataIndex: 'gunId' },
            { title: 'Type de carte', dataIndex: 'cardType' },
            { title: 'Immatriculation', dataIndex: 'carNumber' },
            { title: 'Type de transaction', dataIndex: 'tradeType' },
            { title: 'Énergie', dataIndex: 'oilName' },
            { title: 'POSTTC', dataIndex: 'posttc' },
            { title: 'Volume', dataIndex: 'volume', render: (v) => fmtNum(v) },
            { title: 'Montant', dataIndex: 'amount', render: (v) => fmtNum(v) },
            { title: 'Solde', dataIndex: 'balance', render: (v) => fmtNum(v) },
            { title: 'Volume cumulé', dataIndex: 'volumeTotal', render: (v) => fmtNum(v) },
            { title: 'Date', dataIndex: 'createTime', render: (v) => (v ? dayjs(v as string).format('YYYY-MM-DD HH:mm:ss') : '') },
            { title: 'Erreur', dataIndex: 'error' },
          ]}
        />
      </Card>
      <Modal title="Récupérer des transactions" open={retrieve} onOk={sendRetrieve} onCancel={() => setRetrieve(false)}
        okText="Envoyer" cancelText="Annuler">
        <Form form={rForm} layout="vertical">
          <Form.Item name="deviceNo" label="N° d'appareil" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="nozId" label="N° de pistolet" rules={[{ required: true }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="posStart" label="POSTTC de début (hexadécimal)" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="posEnd" label="POSTTC de fin (hexadécimal)" rules={[{ required: true }]}><Input /></Form.Item>
        </Form>
      </Modal>
    </>
  )
}
