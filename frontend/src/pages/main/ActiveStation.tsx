import { useCallback, useState } from 'react'
import { Card, Form, Input, InputNumber, Space, Tag } from 'antd'
import { get, put } from '../../api/http'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'

type Row = Record<string, unknown>

const STATE: Record<number, { label: string; color: string }> = {
  0: { label: 'Activée', color: 'green' },
  1: { label: 'Supprimée', color: 'red' },
}

/** Activation des stations (Main/ActiveStation d'origine). */
export default function ActiveStation() {
  const [regionId, setRegionId] = useState<number>()
  const [cityId, setCityId] = useState<number>()
  const load = useCallback(() => get<Row[]>('/station-activation', { regionId, cityId }), [regionId, cityId])

  return (
    <>
      <PageHeader title="Activation de stations" section="Paramètres de base" />
      <Card>
        <CrudTable<Row>
          rowKey="stationId"
          load={load}
          toolbar={
            <Space>
              <RemoteSelect
                type="RegionAuth"
                placeholder="Région"
                value={regionId}
                onChange={(v) => {
                  setRegionId(v)
                  setCityId(undefined)
                }}
              />
              <RemoteSelect
                type="CityAuth"
                placeholder="Ville"
                params={{ regionId }}
                enabled={regionId != null}
                value={cityId}
                onChange={setCityId}
              />
            </Space>
          }
          columns={[
            { title: 'Région', dataIndex: 'regionName' },
            { title: 'Ville', dataIndex: 'cityName' },
            { title: 'Nom de la station', dataIndex: 'name' },
            {
              title: 'État de la station',
              dataIndex: 'isActive',
              render: (v: number) => {
                const s = STATE[v] ?? { label: 'Non activée', color: 'red' }
                return <Tag color={s.color}>{s.label}</Tag>
              },
            },
            { title: 'Tarif mensuel', dataIndex: 'stationPrice' },
            { title: 'Remise 2 ans', dataIndex: 'twoYear' },
            { title: 'Remise 3 ans', dataIndex: 'threeYear' },
          ]}
          modalTitle={() => 'Activation de la station'}
          editValues={(r) => ({ name: r.name, price: r.stationPrice ?? 0, twoYear: r.twoYear ?? 0, threeYear: r.threeYear ?? 0 })}
          renderForm={() => (
            <>
              <Form.Item name="name" label="Nom de la station">
                <Input readOnly />
              </Form.Item>
              <Form.Item name="price" label="Tarif mensuel" rules={[{ required: true }]}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="twoYear" label="Valeur de remise sur 2 ans" rules={[{ required: true }]}>
                <InputNumber min={0} precision={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="threeYear" label="Valeur de remise sur 3 ans" rules={[{ required: true }]}>
                <InputNumber min={0} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </>
          )}
          onUpdate={(r, v) => put(`/station-activation/${r.stationId}`, v)}
        />
      </Card>
    </>
  )
}
