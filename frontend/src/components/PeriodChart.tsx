import { useEffect, useState } from 'react'
import { Card, DatePicker, Select, Space } from 'antd'
import ReactECharts from 'echarts-for-react'
import dayjs from 'dayjs'
import { get } from '../api/http'

export interface Series {
  key: string
  name: string
}

interface Props {
  title: string
  url: string
  series: Series[]
  params?: Record<string, unknown>
  /** Types proposés (0 heure, 1 jour, 2 mois, 3 trimestre) */
  types?: number[]
  chartType?: 'line' | 'bar'
}

const TYPE_LABELS: Record<number, string> = {
  0: 'Rapport horaire',
  1: 'Rapport journalier',
  2: 'Rapport mensuel',
  3: 'Rapport trimestriel',
}

/** Graphique par période (heure / jour / mois / trimestre) commun à plusieurs écrans. */
export default function PeriodChart({ title, url, series, params, types = [0, 1, 2, 3], chartType = 'line' }: Props) {
  const [type, setType] = useState(types[0])
  const [date, setDate] = useState(dayjs())
  const [data, setData] = useState<Record<string, unknown> | null>(null)
  const key = JSON.stringify(params ?? {})
  useEffect(() => {
    get<Record<string, unknown>>(url, { type, date: date.format('YYYY-MM-DD'), ...(params ?? {}) }).then(setData)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, type, date, key])
  return (
    <Card size="small" title={title} style={{ marginBottom: 16 }}
      extra={
        <Space>
          <Select value={type} onChange={setType} style={{ width: 180 }}
            options={types.map((t) => ({ value: t, label: TYPE_LABELS[t] }))} />
          <DatePicker value={date} onChange={(d) => d && setDate(d)} picker={type === 0 ? 'date' : type === 1 ? 'month' : 'year'} />
        </Space>
      }>
      {data && (
        <ReactECharts style={{ height: 260 }} option={{
          tooltip: { trigger: 'axis' },
          legend: { data: series.map((s) => s.name) },
          grid: { left: 60, right: 20, bottom: 30, top: 40 },
          xAxis: { type: 'category', data: data.labels },
          yAxis: { type: 'value' },
          series: series.map((s) => ({
            name: s.name, type: chartType, smooth: true, areaStyle: chartType === 'line' ? { opacity: 0.1 } : undefined,
            data: data[s.key],
          })),
        }} />
      )}
    </Card>
  )
}
