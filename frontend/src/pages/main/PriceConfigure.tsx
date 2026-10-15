import { useCallback, useState } from 'react'
import { Card, DatePicker, Form, InputNumber } from 'antd'
import dayjs from 'dayjs'
import { del, get, post, put } from '../../api/http'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import StationFilter, { type StationFilterValue } from '../../components/StationFilter'

type Row = Record<string, unknown>

const fmtDate = (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD') : '')

/** Configuration des prix (Main/PriceConfigure d'origine). */
export default function PriceConfigure() {
  const [filter, setFilter] = useState<StationFilterValue>({})
  const load = useCallback(() => get<Row[]>('/prices', filter), [filter])
  const toBody = (v: Record<string, unknown>) => ({
    ...v,
    validTime: dayjs(v.validTime as dayjs.Dayjs).format('YYYY-MM-DD'),
  })

  return (
    <>
      <PageHeader title="Configuration des prix" section="Paramètres de base" />
      <Card>
        <CrudTable<Row>
          rowKey="id"
          load={load}
          addLabel="Ajouter un prix"
          toolbar={<StationFilter value={filter} onChange={setFilter} />}
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: "Type d'énergie", dataIndex: 'gradeName' },
            { title: 'Prix actuel', dataIndex: 'currentPpu' },
            { title: 'Nouveau prix', dataIndex: 'newPpu' },
            { title: "Date d'effet", dataIndex: 'validateTime', render: fmtDate },
          ]}
          modalTitle={(m) => (m === 'create' ? 'Ajouter un prix' : 'Modifier le prix')}
          createValues={() => ({ validTime: dayjs() })}
          editValues={(r) => ({
            stationId: r.stationId,
            gradeId: r.gradeSpecificId,
            currentPpu: r.currentPpu,
            newPrice: r.newPpu,
            validTime: dayjs(r.validateTime as string),
          })}
          renderForm={(mode) => (
            <>
              <Form.Item name="stationId" label="Station" rules={[{ required: true }]}>
                <RemoteSelect type="ICStation" style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="gradeId" label="Énergie" rules={[{ required: true }]}>
                <RemoteSelect type="TCPGrade" style={{ width: '100%' }} />
              </Form.Item>
              {mode === 'edit' && (
                <Form.Item name="currentPpu" label="Prix actuel">
                  <InputNumber readOnly style={{ width: '100%' }} />
                </Form.Item>
              )}
              <Form.Item name="newPrice" label="Nouveau prix" rules={[{ required: true }]}>
                <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="validTime" label="Date d'effet" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </>
          )}
          onCreate={(v) => post('/prices', toBody(v))}
          onUpdate={(r, v) => put(`/prices/${r.id}`, toBody(v))}
          onDelete={(r) => del(`/prices/${r.id}`)}
        />
      </Card>
    </>
  )
}
