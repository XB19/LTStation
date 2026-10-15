import { useState } from 'react'
import { Button, Card, DatePicker, Form, Input, Select, Space, Table, Tabs, Typography } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { get } from '../../api/http'
import PageHeader from '../../components/PageHeader'
import PeriodChart from '../../components/PeriodChart'
import SearchTable, { serializeFilters } from '../../components/SearchTable'
import StationFilter, { type StationFilterValue } from '../../components/StationFilter'
import { fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>
const n3 = (v: unknown) => fmtNum(v, 3)

function Period({ value, onChange }: { value: [dayjs.Dayjs, dayjs.Dayjs]; onChange: (v: [dayjs.Dayjs, dayjs.Dayjs]) => void }) {
  return <DatePicker.RangePicker showTime value={value} onChange={(v) => v && onChange(v as [dayjs.Dayjs, dayjs.Dayjs])} />
}

const range = (p: [dayjs.Dayjs, dayjs.Dayjs]) => ({ start: p[0].format('YYYY-MM-DDTHH:mm:ss'), end: p[1].format('YYYY-MM-DDTHH:mm:ss') })

function Summary({ employees }: { employees?: boolean }) {
  const [period, setPeriod] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([dayjs().startOf('month'), dayjs()])
  const [filter, setFilter] = useState<StationFilterValue>({})
  const [rows, setRows] = useState<Row[]>([])
  const [loading, setLoading] = useState(false)
  const search = async () => {
    setLoading(true)
    try {
      setRows(await get<Row[]>(employees ? '/card-summary/employees' : '/card-summary/users', { ...range(period), ...filter }))
    } finally {
      setLoading(false)
    }
  }
  const data = rows.filter((r) => r.asn !== '__total__')
  const total = rows.find((r) => r.asn === '__total__')
  const cols = [
    ...(employees ? [{ title: 'Station', dataIndex: 'stationName' }] : []),
    { title: 'Nom', dataIndex: 'name' },
    { title: employees ? "N° d'employé" : 'Compte utilisateur', dataIndex: 'accountNo' },
    { title: 'N° de carte', dataIndex: 'asn' },
    { title: 'Solde initial', dataIndex: 'iniBalance', render: n3 },
    { title: 'Rechargé', dataIndex: 'saveAmount', render: n3 },
    { title: 'Offert', dataIndex: 'freeAmount', render: n3 },
    { title: 'Annulé', dataIndex: 'chargeAmount', render: n3 },
    { title: 'Consommé', dataIndex: 'amount', render: n3 },
    { title: 'Volume', dataIndex: 'volume', render: n3 },
    { title: 'Solde final', dataIndex: 'finalBalance', render: n3 },
    { title: 'Écart', dataIndex: 'error', render: n3 },
  ]
  const offset = employees ? 4 : 3
  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Period value={period} onChange={setPeriod} />
        {employees && <StationFilter value={filter} onChange={setFilter} />}
        <Button type="primary" icon={<SearchOutlined />} onClick={search}>Rechercher</Button>
      </Space>
      <Table size="middle" rowKey="asn" loading={loading} dataSource={data} columns={cols} scroll={{ x: 'max-content' }}
        pagination={{ pageSize: 15 }}
        summary={() => total && (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0} colSpan={offset}><b>Total</b></Table.Summary.Cell>
            {['iniBalance', 'saveAmount', 'freeAmount', 'chargeAmount', 'amount', 'volume', 'finalBalance', 'error'].map((k, i) => (
              <Table.Summary.Cell key={k} index={offset + i}><b>{n3(total[k])}</b></Table.Summary.Cell>
            ))}
          </Table.Summary.Row>
        )}
      />
    </>
  )
}

const BILL_COLUMNS = [
  { title: 'Nom', dataIndex: 'name' },
  { title: 'Compte', dataIndex: 'account' },
  { title: 'N° de carte', dataIndex: 'asn' },
  { title: 'Opération', dataIndex: 'operateType' },
  { title: 'Montant', dataIndex: 'amount', render: n3 },
  { title: 'Offert', dataIndex: 'discount', render: n3 },
  { title: 'Volume', dataIndex: 'volume', render: n3 },
  { title: 'Station', dataIndex: 'stationName' },
  { title: 'Solde', dataIndex: 'balance', render: n3 },
  { title: 'Date', dataIndex: 'createTime', render: fmtDateTime },
  { title: 'Écart', dataIndex: 'error', render: n3 },
]

const billParams = (v: Record<string, unknown>) => {
  const { period, ...rest } = v as { period?: [dayjs.Dayjs, dayjs.Dayjs] }
  return serializeFilters({ ...rest, start: period?.[0], end: period?.[1] })
}

function SingleBill() {
  return (
    <SearchTable<Row>
      url="/card-summary/single-bill" rowKey="id" pageSize={15} autoLoad={false}
      initialFilters={{ period: [dayjs().startOf('month'), dayjs()], holderClass: 1, name: '' , cardNo: '', phone: '', carNo: '' }}
      toParams={billParams}
      filters={
        <>
          <Form.Item name="period" label="Période"><DatePicker.RangePicker showTime /></Form.Item>
          <Form.Item name="holderClass" label="Recherche">
            <Select style={{ width: 140 }} options={[
              { value: 1, label: 'Utilisateur' }, { value: 2, label: 'Employé' }, { value: 3, label: 'Groupe' },
            ]} />
          </Form.Item>
          <Form.Item name="name" label="Nom"><Input allowClear /></Form.Item>
          <Form.Item name="cardNo" label="N° de carte"><Input allowClear /></Form.Item>
          <Form.Item name="phone" label="Téléphone"><Input allowClear /></Form.Item>
          <Form.Item name="carNo" label="Immatriculation"><Input allowClear /></Form.Item>
        </>
      }
      columns={BILL_COLUMNS}
    />
  )
}

function GroupBill() {
  return (
    <SearchTable<Row>
      url="/card-summary/group-bill" rowKey="id" pageSize={15} autoLoad={false}
      initialFilters={{ period: [dayjs().startOf('month'), dayjs()] }}
      toParams={billParams}
      filters={
        <>
          <Form.Item name="period" label="Période"><DatePicker.RangePicker showTime /></Form.Item>
          <Form.Item name="name" label="Nom du groupe"><Input allowClear /></Form.Item>
          <Form.Item name="cardNo" label="N° de carte"><Input allowClear /></Form.Item>
          <Form.Item name="phone" label="Téléphone"><Input allowClear /></Form.Item>
          <Form.Item name="certificateNo" label="N° d'entreprise"><Input allowClear /></Form.Item>
          <Form.Item name="contact" label="Responsable"><Input allowClear /></Form.Item>
        </>
      }
      columns={BILL_COLUMNS}
    />
  )
}

function Sedimentary() {
  const [end, setEnd] = useState(dayjs())
  const [data, setData] = useState<{ rows: Row[]; sumAmount: number } | null>(null)
  const search = async () => setData(await get('/card-summary/sedimentary', { end: end.format('YYYY-MM-DDTHH:mm:ss') }))
  return (
    <>
      <PeriodChart title="Évolution du solde dormant" url="/card-summary/sedimentary-chart" types={[0, 1, 2]}
        series={[{ key: 'values', name: 'Solde dormant' }]} />
      <Space wrap style={{ marginBottom: 12 }}>
        <DatePicker showTime value={end} onChange={(d) => d && setEnd(d)} />
        <Button type="primary" icon={<SearchOutlined />} onClick={search}>Rechercher</Button>
        {data && <Typography.Text strong>Solde dormant total : {fmtNum(data.sumAmount, 0)}</Typography.Text>}
      </Space>
      <Table size="middle" rowKey="asn" dataSource={data?.rows ?? []} pagination={{ pageSize: 15 }} columns={[
        { title: 'Nom', dataIndex: 'name' },
        { title: 'Compte', dataIndex: 'accountNo' },
        { title: 'N° de carte', dataIndex: 'asn' },
        { title: 'Solde', dataIndex: 'finalBalance', render: n3 },
        { title: 'Dernière opération', dataIndex: 'createTime', render: fmtDateTime },
      ]} />
    </>
  )
}

/** Statistiques des cartes (Main/CardSummary d'origine). */
export default function CardSummary() {
  return (
    <>
      <PageHeader title="Statistiques des cartes" section="Statistiques" />
      <Card>
        <Tabs destroyInactiveTabPane items={[
          { key: 'users', label: 'Bilan des cartes utilisateur', children: <Summary /> },
          { key: 'emp', label: 'Bilan des cartes employé', children: <Summary employees /> },
          { key: 'single', label: 'Relevé individuel', children: <SingleBill /> },
          { key: 'group', label: 'Relevé de groupe', children: <GroupBill /> },
          { key: 'sed', label: 'Soldes dormants', children: <Sedimentary /> },
        ]} />
      </Card>
    </>
  )
}
