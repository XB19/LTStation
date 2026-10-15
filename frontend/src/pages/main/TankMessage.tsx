import { useCallback, useEffect, useState } from 'react'
import { Card, DatePicker, Form, Segmented, Space, Table, Tabs } from 'antd'
import ReactECharts from 'echarts-for-react'
import dayjs from 'dayjs'
import { get } from '../../api/http'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import SearchTable, { serializeFilters } from '../../components/SearchTable'
import StationFilter, { type StationFilterValue } from '../../components/StationFilter'
import { fmtDateTime } from '../../utils/format'

type Row = Record<string, unknown>

function LevelChart() {
  const [station, setStation] = useState<StationFilterValue>({})
  const [tankId, setTankId] = useState<number>()
  const [type, setType] = useState(0)
  const [date, setDate] = useState(dayjs())
  const [data, setData] = useState<{ labels: string[]; values: number[] } | null>(null)
  useEffect(() => {
    if (tankId == null) {
      setData(null)
      return
    }
    get<{ labels: string[]; values: number[] }>(`/air-setting/tanks/${tankId}/chart`, { type, date: date.format('YYYY-MM-DD') }).then(setData)
  }, [tankId, type, date])
  return (
    <Card size="small" title="Courbe de niveau" style={{ marginBottom: 16 }}
      extra={
        <Space wrap>
          <StationFilter value={station} onChange={(v) => {
            setStation(v)
            setTankId(undefined)
          }} />
          <RemoteSelect type="TankSelect" placeholder="Cuve" params={{ stationId: station.stationId }}
            enabled={station.stationId != null} value={tankId} onChange={setTankId} />
          <Segmented value={type} onChange={(v) => setType(Number(v))}
            options={[{ value: 0, label: 'Jour' }, { value: 1, label: 'Mois' }, { value: 2, label: 'Année' }]} />
          <DatePicker value={date} onChange={(d) => d && setDate(d)} picker={type === 0 ? 'date' : type === 1 ? 'month' : 'year'} />
        </Space>
      }>
      {data ? (
        <ReactECharts style={{ height: 260 }} option={{
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: data.labels },
          yAxis: { type: 'value', name: 'Hauteur' },
          series: [{ name: 'Hauteur', type: 'line', smooth: true, connectNulls: true, data: data.values }],
        }} />
      ) : (
        <div style={{ color: '#999', padding: 24, textAlign: 'center' }}>Choisissez une station puis une cuve</div>
      )}
    </Card>
  )
}

function RealTable({ url, columns }: { url: string; columns: { title: string; dataIndex: string; render?: (v: unknown) => string }[] }) {
  const [filter, setFilter] = useState<StationFilterValue>({})
  const [rows, setRows] = useState<Row[]>([])
  const load = useCallback(() => get<Row[]>(url, filter).then(setRows), [url, filter])
  useEffect(() => {
    load()
  }, [load])
  return (
    <>
      <div style={{ marginBottom: 12 }}><StationFilter value={filter} onChange={setFilter} /></div>
      <Table size="middle" rowKey={(r) => String(r.rowKey ?? r.id)} dataSource={rows} columns={columns} scroll={{ x: 'max-content' }} />
    </>
  )
}

function HistoryTable({ url, cloud }: { url: string; cloud: boolean }) {
  const [form] = Form.useForm()
  const station = Form.useWatch('station', form)
  return (
    <SearchTable<Row>
      url={url}
      rowKey="id"
      pageSize={15}
      form={form}
      initialFilters={{ period: [dayjs().startOf('day'), dayjs()], station: {} }}
      toParams={(v) => {
        const { period, station: st, ...rest } = v as { period?: [dayjs.Dayjs, dayjs.Dayjs]; station?: Record<string, unknown> }
        return serializeFilters({ ...rest, ...(st ?? {}), start: period?.[0], end: period?.[1] })
      }}
      filters={
        <>
          <Form.Item name="period" label="Période"><DatePicker.RangePicker showTime /></Form.Item>
          <Form.Item name="station"><StationFilter /></Form.Item>
          {cloud && (
            <Form.Item name="tankId" label="Cuve">
              <RemoteSelect type="TankSelect" params={{ stationId: station?.stationId }} enabled={station?.stationId != null} />
            </Form.Item>
          )}
        </>
      }
      columns={[
        { title: 'Station', dataIndex: 'stationName' },
        { title: 'Cuve', dataIndex: 'tankName' },
        { title: 'Hauteur', dataIndex: 'tankHeight' },
        { title: 'Volume', dataIndex: 'tankVolume' },
        { title: 'Date', dataIndex: 'createTime', render: fmtDateTime },
      ]}
    />
  )
}

const REAL_COLUMNS = [
  { title: 'Station', dataIndex: 'stationName' },
  { title: 'Cuve', dataIndex: 'tankName' },
  { title: "Type d'énergie", dataIndex: 'gradeType' },
  { title: 'Hauteur actuelle', dataIndex: 'tankHeight' },
  { title: 'Volume actuel', dataIndex: 'tankVolume' },
  { title: 'Date', dataIndex: 'createTime', render: fmtDateTime },
]

/** Informations des cuves (Main/TankMessage) ; variante `station` = cuves jaugées des stations (Main/StationTankMessage). */
export default function TankMessage({ station = false }: { station?: boolean }) {
  return (
    <>
      <PageHeader title={station ? 'Cuves des stations' : 'Informations des cuves'} section="Consultation des enregistrements" />
      {!station && <LevelChart />}
      <Card>
        <Tabs destroyInactiveTabPane items={[
          { key: 'real', label: 'Données en temps réel',
            children: <RealTable url={station ? '/tanks/station-real' : '/tanks/real'} columns={REAL_COLUMNS} /> },
          { key: 'history', label: 'Historique',
            children: <HistoryTable url={station ? '/tanks/station-history' : '/tanks/history'} cloud={!station} /> },
        ]} />
      </Card>
    </>
  )
}

export function StationTankMessage() {
  return <TankMessage station />
}
