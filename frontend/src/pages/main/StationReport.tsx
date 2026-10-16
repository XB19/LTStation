import { useEffect, useState } from 'react'
import { Button, Card, DatePicker, Descriptions, Segmented, Space, Table, Tabs } from 'antd'
import { DownloadOutlined, SearchOutlined } from '@ant-design/icons'
import ReactECharts from '../../components/ThemedChart'
import dayjs from 'dayjs'
import { get } from '../../api/http'
import PageHeader from '../../components/PageHeader'
import StationFilter, { type StationFilterValue } from '../../components/StationFilter'
import { fmtNum } from '../../utils/format'

type Row = Record<string, unknown>

const qs = (o: Record<string, unknown>) =>
  new URLSearchParams(Object.entries(o).filter(([, v]) => v != null && v !== '').map(([k, v]) => [k, String(v)])).toString()

function ShiftReport() {
  const [filter, setFilter] = useState<StationFilterValue>({})
  const [period, setPeriod] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([dayjs().startOf('day'), dayjs()])
  const [rows, setRows] = useState<Row[]>([])
  const params = () => ({ ...filter, start: period[0].format('YYYY-MM-DDTHH:mm:ss'), end: period[1].format('YYYY-MM-DDTHH:mm:ss') })
  const search = async () => setRows(await get<Row[]>('/station-report/shift', params()))
  useEffect(() => {
    search()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  const sum = (k: string) => rows.reduce((a, r) => a + Number(r[k] ?? 0), 0)
  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <DatePicker.RangePicker showTime value={period} onChange={(v) => v && setPeriod(v as [dayjs.Dayjs, dayjs.Dayjs])} />
        <StationFilter value={filter} onChange={setFilter} />
        <Button type="primary" icon={<SearchOutlined />} onClick={search}>Rechercher</Button>
        <Button icon={<DownloadOutlined />} onClick={() => window.open(`/api/station-report/export?${qs({ report: 'shift', ...params() })}`)}>
          Exporter
        </Button>
      </Space>
      <Table size="middle" rowKey="id" dataSource={rows} pagination={{ pageSize: 15 }} scroll={{ x: 'max-content' }}
        columns={[
          { title: 'Station', dataIndex: 'stationName' },
          { title: "N° d'appareil", dataIndex: 'deviceNo' },
          { title: 'Pistolet', dataIndex: 'nozId' },
          { title: 'Montant total', dataIndex: 'truelyPay', render: (v) => fmtNum(v) },
          { title: 'Volume total', dataIndex: 'volume', render: (v) => fmtNum(v) },
          { title: 'Période', dataIndex: 'checkTime' },
        ]}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0} colSpan={3}><b>Total</b></Table.Summary.Cell>
            <Table.Summary.Cell index={3}><b>{fmtNum(sum('truelyPay'))}</b></Table.Summary.Cell>
            <Table.Summary.Cell index={4}><b>{fmtNum(sum('volume'))}</b></Table.Summary.Cell>
            <Table.Summary.Cell index={5} />
          </Table.Summary.Row>
        )}
      />
    </>
  )
}

const PICKER: Record<string, 'date' | 'month' | 'quarter' | 'year'> = { day: 'date', month: 'month', season: 'quarter', year: 'year' }

function SummaryReport({ period }: { period: 'day' | 'month' | 'season' | 'year' }) {
  const [filter, setFilter] = useState<StationFilterValue>({})
  const [date, setDate] = useState(dayjs())
  const [rows, setRows] = useState<Row[]>([])
  const params = () => ({ ...filter, period, date: date.format('YYYY-MM-DD') })
  const search = async () => setRows(await get<Row[]>('/station-report/summary', params()))
  useEffect(() => {
    search()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  const sum = (k: string) => rows.reduce((a, r) => a + Number(r[k] ?? 0), 0)
  const label = { day: 'du jour', month: 'du mois', season: 'du trimestre', year: "de l'année" }[period]
  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <DatePicker value={date} onChange={(d) => d && setDate(d)} picker={PICKER[period]} />
        <StationFilter value={filter} onChange={setFilter} />
        <Button type="primary" icon={<SearchOutlined />} onClick={search}>Rechercher</Button>
        <Button icon={<DownloadOutlined />} onClick={() => window.open(`/api/station-report/export?${qs({ report: period, ...params() })}`)}>
          Exporter
        </Button>
      </Space>
      <Descriptions size="small" bordered column={4} style={{ marginBottom: 12 }} items={[
        { key: 'a', label: `Montant consommé ${label}`, children: fmtNum(sum('sumSalerAmount')) },
        { key: 'v', label: `Volume consommé ${label}`, children: fmtNum(sum('sumSalerVolume')) },
        { key: 'd', label: `Montant rechargé ${label}`, children: fmtNum(sum('sumChargeAmount')) },
        { key: 'c', label: `Montant annulé ${label}`, children: fmtNum(sum('sumRepayAmount')) },
      ]} />
      <Table size="middle" rowKey="stationId" dataSource={rows} pagination={{ pageSize: 15 }} scroll={{ x: 'max-content' }}
        columns={[
          ...(period === 'season' ? [{ title: 'Trimestre', dataIndex: 'season' }] : []),
          { title: 'Station', dataIndex: 'stationName' },
          { title: 'Montant consommé', dataIndex: 'sumSalerAmount', render: (v: unknown) => fmtNum(v) },
          { title: 'Volume consommé', dataIndex: 'sumSalerVolume', render: (v: unknown) => fmtNum(v) },
          { title: 'Montant rechargé', dataIndex: 'sumChargeAmount', render: (v: unknown) => fmtNum(v) },
          { title: 'Montant annulé', dataIndex: 'sumRepayAmount', render: (v: unknown) => fmtNum(v) },
        ]}
      />
    </>
  )
}

function StationCompareChart() {
  const [type, setType] = useState(0)
  const [date, setDate] = useState(dayjs())
  const [data, setData] = useState<{ labels: string[]; series: { name: string; data: number[] }[] } | null>(null)
  useEffect(() => {
    get<{ labels: string[]; series: { name: string; data: number[] }[] }>('/station-report/station-chart', {
      type, date: date.format('YYYY-MM-DD'),
    }).then(setData).catch(() => setData(null))
  }, [type, date])
  return (
    <Card size="small" title="Ventes comparées par station" style={{ marginBottom: 16 }}
      extra={
        <Space>
          <Segmented value={type} onChange={(v) => setType(Number(v))}
            options={[{ value: 0, label: 'Mois' }, { value: 1, label: 'Année' }, { value: 2, label: 'Plusieurs années' }]} />
          <DatePicker value={date} onChange={(d) => d && setDate(d)} picker={type === 0 ? 'month' : 'year'} />
        </Space>
      }>
      {data && (
        <ReactECharts style={{ height: 300 }} notMerge option={{
          tooltip: { trigger: 'axis' },
          legend: { type: 'scroll' },
          grid: { left: 60, right: 80, bottom: 30, top: 40 },
          xAxis: { type: 'category', data: data.labels },
          yAxis: { type: 'value' },
          series: data.series.map((s) => ({ name: s.name, type: 'line', data: s.data, endLabel: { show: true, formatter: s.name } })),
        }} />
      )}
    </Card>
  )
}

/** Rapports des stations (Main/StationReport d'origine). */
export default function StationReport() {
  return (
    <>
      <PageHeader title="Rapports des stations" section="Recherche de rapports" />
      <StationCompareChart />
      <Card>
        <Tabs destroyInactiveTabPane items={[
          { key: 'shift', label: 'Rapport de poste', children: <ShiftReport /> },
          { key: 'day', label: 'Rapport journalier', children: <SummaryReport period="day" /> },
          { key: 'month', label: 'Rapport mensuel', children: <SummaryReport period="month" /> },
          { key: 'season', label: 'Rapport trimestriel', children: <SummaryReport period="season" /> },
          { key: 'year', label: 'Rapport annuel', children: <SummaryReport period="year" /> },
        ]} />
      </Card>
    </>
  )
}
