import { useEffect, useMemo, useState } from 'react'
import { Card, Col, DatePicker, Descriptions, Empty, Form, Row, Segmented, Select, Space, Tabs } from 'antd'
import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
import ReactECharts from '../../components/ThemedChart'
import dayjs from 'dayjs'
import { get } from '../../api/http'
import PageHeader from '../../components/PageHeader'
import SearchTable, { serializeFilters } from '../../components/SearchTable'
import { fmtDateTime, fmtNum } from '../../utils/format'

// Icônes Leaflet servies par Vite
L.Marker.prototype.options.icon = L.icon({
  iconUrl: markerIcon, iconRetinaUrl: markerIcon2x, shadowUrl: markerShadow, iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34],
})

interface Device {
  id: number
  deviceNo: string
  nozId: number
  stationName: string
  oilName: string
  isOnline: boolean
  lng: number | null
  lat: number | null
  price: number | null
}

type Row = Record<string, unknown>

function DeviceStats({ id }: { id: number }) {
  const [totals, setTotals] = useState<Record<string, number> | null>(null)
  const [type, setType] = useState(1)
  const [date, setDate] = useState(dayjs())
  const [chart, setChart] = useState<{ labels: string[]; amount: number[]; volume: number[]; count: number[] } | null>(null)
  useEffect(() => {
    get<Record<string, number>>(`/device-search/${id}/totals`).then(setTotals)
  }, [id])
  useEffect(() => {
    get<{ labels: string[]; amount: number[]; volume: number[]; count: number[] }>(`/device-search/${id}/chart`, {
      type, date: date.format('YYYY-MM-DD'),
    }).then(setChart)
  }, [id, type, date])
  const line = (p: string, label: string) =>
    totals && { key: p, label, children: `${totals[`${p}Count`]} transaction(s) — ${fmtNum(totals[`${p}Amount`])} — ${fmtNum(totals[`${p}Volume`])} L` }
  return (
    <>
      {totals && (
        <Descriptions bordered size="small" column={2} style={{ marginBottom: 16 }}
          items={[line('day', "Aujourd'hui"), line('month', 'Ce mois'), line('year', 'Cette année'), line('total', 'Depuis l’origine')].filter(Boolean) as never} />
      )}
      <Space style={{ marginBottom: 8 }}>
        <Segmented value={type} onChange={(v) => setType(Number(v))}
          options={[{ value: 1, label: 'Par jour' }, { value: 2, label: 'Par mois' }, { value: 3, label: 'Par année' }]} />
        <DatePicker value={date} onChange={(d) => d && setDate(d)} picker={type === 1 ? 'month' : 'year'} />
      </Space>
      {chart && (
        <ReactECharts style={{ height: 280 }} option={{
          tooltip: { trigger: 'axis' },
          legend: { data: ['Montant', 'Volume', 'Nombre'] },
          xAxis: { type: 'category', data: chart.labels },
          yAxis: [{ type: 'value' }, { type: 'value', name: 'Nombre' }],
          series: [
            { name: 'Montant', type: 'bar', data: chart.amount },
            { name: 'Volume', type: 'bar', data: chart.volume },
            { name: 'Nombre', type: 'line', yAxisIndex: 1, data: chart.count },
          ],
        }} />
      )}
    </>
  )
}

/** Recherche d'appareils (Main/DeviceSearch d'origine) — carte OpenStreetMap au lieu de Baidu. */
export default function DeviceSearch() {
  const [devices, setDevices] = useState<Device[]>([])
  const [selected, setSelected] = useState<number>()
  useEffect(() => {
    get<Device[]>('/device-search/locations').then((d) => {
      setDevices(d)
      if (d.length) setSelected(d[0].id)
    })
  }, [])
  const located = useMemo(() => devices.filter((d) => d.lat != null && d.lng != null && (d.lat !== 0 || d.lng !== 0)), [devices])
  const center: [number, number] = located.length ? [located[0].lat!, located[0].lng!] : [6.1319, 1.2228]

  return (
    <>
      <PageHeader title="Recherche d'appareils" section="Recherche d'appareils" />
      <Row gutter={16}>
        <Col xs={24} xl={12}>
          <Card title="Carte des appareils" bodyStyle={{ padding: 0 }}>
            <MapContainer center={center} zoom={located.length ? 14 : 6} style={{ height: 460 }} key={center.join(',')}>
              <TileLayer attribution='&copy; OpenStreetMap' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
              {located.map((d) => (
                <Marker key={d.id} position={[d.lat!, d.lng!]} eventHandlers={{ click: () => setSelected(d.id) }}>
                  <Popup>
                    <b>{d.stationName}</b><br />
                    Pistolet {d.nozId} — {d.oilName}<br />
                    Appareil {d.deviceNo}<br />
                    Prix : {d.price ?? '—'}<br />
                    {d.isOnline ? 'En ligne' : 'Hors ligne'}
                  </Popup>
                </Marker>
              ))}
            </MapContainer>
            {devices.length > 0 && located.length === 0 && (
              <div style={{ padding: 12, color: 'var(--lt-color-text-tertiary)' }}>Aucun appareil ne transmet encore sa position GPS.</div>
            )}
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card title="Appareil" extra={
            <Select style={{ width: 300 }} value={selected} onChange={setSelected} showSearch optionFilterProp="label"
              options={devices.map((d) => ({ value: d.id, label: `${d.stationName} — pistolet ${d.nozId} (${d.deviceNo})` }))} />
          }>
            {selected ? <DeviceStats id={selected} /> : <Empty description="Aucun appareil" />}
          </Card>
        </Col>
      </Row>
      {selected && (
        <Card style={{ marginTop: 16 }}>
          <Tabs items={[{
            key: 'tx', label: "Transactions de l'appareil",
            children: (
              <SearchTable<Row>
                key={selected}
                url={`/device-search/${selected}/transactions`}
                rowKey="id"
                toParams={(v) => {
                  const p = v.period as [dayjs.Dayjs, dayjs.Dayjs] | undefined
                  return serializeFilters({ start: p?.[0], end: p?.[1] })
                }}
                filters={<Form.Item name="period" label="Période"><DatePicker.RangePicker showTime /></Form.Item>}
                columns={[
                  { title: 'Station', dataIndex: 'stationName' },
                  { title: "N° d'appareil", dataIndex: 'deviceNo' },
                  { title: 'Pistolet', dataIndex: 'gunId' },
                  { title: 'Énergie', dataIndex: 'oilName' },
                  { title: 'Montant', dataIndex: 'amount', render: (v) => fmtNum(v) },
                  { title: 'Volume', dataIndex: 'volume', render: (v) => fmtNum(v) },
                  { title: 'Volume cumulé', dataIndex: 'volumeTotal', render: (v) => fmtNum(v) },
                  { title: 'N° de carte', dataIndex: 'asn' },
                  { title: 'Solde', dataIndex: 'balance', render: (v) => fmtNum(v) },
                  { title: 'Date', dataIndex: 'createTime', render: fmtDateTime },
                ]}
              />
            ),
          }]} />
        </Card>
      )}
    </>
  )
}
