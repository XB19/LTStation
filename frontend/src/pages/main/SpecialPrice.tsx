import { useCallback } from 'react'
import { Card, DatePicker, Form, Input, InputNumber } from 'antd'
import dayjs from 'dayjs'
import { del, get, post, put } from '../../api/http'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import { fmtDateTime } from '../../utils/format'

type Row = Record<string, unknown>

const toBody = (v: Record<string, unknown>) => {
  const [s, e] = v.period as [dayjs.Dayjs, dayjs.Dayjs]
  return { name: v.name, gradeId: v.gradeId, price: v.price, start: s.format('YYYY-MM-DDTHH:mm:ss'), end: e.format('YYYY-MM-DDTHH:mm:ss') }
}

/** Prix préférentiels (Main/SpecialPrice d'origine). */
export default function SpecialPrice() {
  const load = useCallback(() => get<Row[]>('/special-prices'), [])
  return (
    <>
      <PageHeader title="Prix préférentiels" section="Configuration système" />
      <Card>
        <CrudTable<Row>
          rowKey="id"
          load={load}
          addLabel="Ajouter un prix préférentiel"
          columns={[
            { title: 'Nom', dataIndex: 'name' },
            { title: 'Énergie', dataIndex: 'gradeName' },
            { title: 'Prix préférentiel', dataIndex: 'spricePrice' },
            { title: 'Période de validité', render: (_, r) => `${fmtDateTime(r.startTime)} → ${fmtDateTime(r.endTime)}` },
          ]}
          editValues={(r) => ({
            name: r.name, gradeId: r.gradeId, price: r.spricePrice,
            period: [dayjs(r.startTime as string), dayjs(r.endTime as string)],
          })}
          renderForm={() => (
            <>
              <Form.Item name="name" label="Nom" rules={[{ required: true }]}><Input /></Form.Item>
              <Form.Item name="gradeId" label="Énergie" rules={[{ required: true }]}>
                <RemoteSelect type="GradeID" style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="price" label="Prix préférentiel" rules={[{ required: true }]}>
                <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="period" label="Période" rules={[{ required: true }]}>
                <DatePicker.RangePicker showTime style={{ width: '100%' }} />
              </Form.Item>
            </>
          )}
          onCreate={(v) => post('/special-prices', toBody(v))}
          onUpdate={(r, v) => put(`/special-prices/${r.id}`, toBody(v))}
          onDelete={(r) => del(`/special-prices/${r.id}`)}
        />
      </Card>
    </>
  )
}
