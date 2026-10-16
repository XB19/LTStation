import { useEffect, useState } from 'react'
import { Button, Card, Col, Empty, Modal, Pagination, Progress, Row, Segmented, Statistic, Table } from 'antd'
import ReactECharts from '../components/ThemedChart'
import dayjs from 'dayjs'
import { get, type PageResult } from '../api/http'
import PageHeader from '../components/PageHeader'
import StationFilter, { type StationFilterValue } from '../components/StationFilter'
import { useAuth } from '../auth/AuthContext'
import { GprsDashboard } from './gprs/Gprs'

interface Stats {
  online: number
  offline: number
  todayVolume: number
  todayAmount: number
}

interface Gauge {
  id: number
  tankName: string
  percent: number
}

interface Chart {
  labels: string[]
  amount: number[]
  volume: number[]
  deposit: number[]
  reverse: number[]
}

const fmtTime = (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '')

/** Accueil : tableau de bord GPRS pour les clients GPRS, sinon celui de Main/Index. */
export default function Dashboard() {
  const { me } = useAuth()
  return me?.loginType === '99' ? <GprsDashboard /> : <MainDashboard />
}

/** Tableau de bord (Main/Index d'origine). */
function MainDashboard() {
  const [stats, setStats] = useState<Stats>({ online: 0, offline: 0, todayVolume: 0, todayAmount: 0 })
  const [filter, setFilter] = useState<StationFilterValue>({})
  const [gauges, setGauges] = useState<PageResult<Gauge>>({ rows: [], total: 0 })
  const [gaugePage, setGaugePage] = useState(1)
  const [chartType, setChartType] = useState(0)
  const [chart, setChart] = useState<Chart | null>(null)
  const [latest, setLatest] = useState<Record<string, unknown>[]>([])
  const [offlineOpen, setOfflineOpen] = useState(false)
  const [offline, setOffline] = useState<Record<string, unknown>[]>([])

  useEffect(() => {
    get<Stats>('/dashboard/stats').then(setStats)
    get<Record<string, unknown>[]>('/dashboard/latest-transactions').then(setLatest)
  }, [])

  useEffect(() => {
    get<PageResult<Gauge>>('/dashboard/tank-gauges', { ...filter, page: gaugePage }).then(setGauges)
  }, [filter, gaugePage])

  useEffect(() => {
    get<Chart>('/dashboard/report-chart', { chartType, date: dayjs().format('YYYY-MM-DD') }).then(setChart)
  }, [chartType])

  const showOffline = async () => {
    setOffline(await get('/dashboard/offline-devices'))
    setOfflineOpen(true)
  }

  const total = stats.online + stats.offline
  const pct = (n: number) => (total ? Math.round((n / total) * 100) : 0)

  return (
    <>
      <PageHeader title="Accueil" section="Statistiques" />
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} xl={6}>
          <Card className="lt-stat">
            <Statistic title="En ligne" value={stats.online} />
            <Progress percent={pct(stats.online)} showInfo={false} status="success" />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card className="lt-stat" hoverable onClick={showOffline}>
            <Statistic title="Hors ligne" value={stats.offline} />
            <Progress percent={pct(stats.offline)} showInfo={false} />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card className="lt-stat">
            <Statistic title="Volume du jour" value={stats.todayVolume} precision={2} />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card className="lt-stat">
            <Statistic title="Montant du jour" value={stats.todayAmount} precision={2} />
          </Card>
        </Col>

        <Col span={24}>
          <Card
            title="Volume des cuves"
            extra={
              <StationFilter
                value={filter}
                onChange={(v) => {
                  setGaugePage(1)
                  setFilter(v)
                }}
              />
            }
          >
            {gauges.rows.length === 0 ? (
              <Empty description="Aucune cuve" />
            ) : (
              <Row gutter={16} justify="center">
                {gauges.rows.map((g) => (
                  <Col key={g.id} xs={12} md={8} xl={4} style={{ textAlign: 'center', marginBottom: 16 }}>
                    <Progress type="dashboard" percent={Number(g.percent)} />
                    <div>{g.tankName}</div>
                  </Col>
                ))}
              </Row>
            )}
            <div style={{ textAlign: 'center' }}>
              <Pagination size="small" current={gaugePage} pageSize={6} total={gauges.total} onChange={setGaugePage} />
            </div>
          </Card>
        </Col>

        <Col xs={24} xl={16}>
          <Card
            title="Graphique des ventes"
            extra={
              <Segmented
                value={chartType}
                onChange={(v) => setChartType(Number(v))}
                options={[
                  { value: 0, label: 'Par heure' },
                  { value: 1, label: 'Par jour' },
                  { value: 2, label: 'Par mois' },
                  { value: 3, label: 'Par trimestre' },
                ]}
              />
            }
          >
            {chart && (
              <ReactECharts
                style={{ height: 360 }}
                option={{
                  tooltip: { trigger: 'axis' },
                  legend: { data: ['Montant', 'Volume', 'Recharge', 'Annulation'] },
                  grid: { left: 50, right: 20, bottom: 30 },
                  xAxis: { type: 'category', data: chart.labels },
                  yAxis: { type: 'value' },
                  series: [
                    { name: 'Montant', type: 'line', smooth: true, areaStyle: { opacity: 0.1 }, data: chart.amount },
                    { name: 'Volume', type: 'line', smooth: true, areaStyle: { opacity: 0.1 }, data: chart.volume },
                    { name: 'Recharge', type: 'line', smooth: true, areaStyle: { opacity: 0.1 }, data: chart.deposit },
                    { name: 'Annulation', type: 'line', smooth: true, areaStyle: { opacity: 0.1 }, data: chart.reverse },
                  ],
                }}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title="Transactions du jour">
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={latest}
              columns={[
                { title: 'Station', dataIndex: 'stationName' },
                { title: 'Montant', dataIndex: 'amount' },
                { title: 'Volume', dataIndex: 'volume' },
                { title: 'Heure', dataIndex: 'createTime', render: fmtTime },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Modal
        title="Appareils hors ligne"
        open={offlineOpen}
        width={900}
        onCancel={() => setOfflineOpen(false)}
        footer={<Button onClick={() => setOfflineOpen(false)}>Fermer</Button>}
      >
        <Table
          size="small"
          rowKey="id"
          dataSource={offline}
          pagination={{ pageSize: 15 }}
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: "N° d'appareil", dataIndex: 'deviceNo' },
            { title: 'Pistolet', dataIndex: 'nozId' },
            { title: 'Heure de déconnexion', dataIndex: 'createTime', render: fmtTime },
          ]}
        />
      </Modal>
    </>
  )
}
