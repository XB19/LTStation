import { useCallback } from 'react'
import { Card, Form, Image, InputNumber, Space } from 'antd'
import { del, get, post, put, type PageResult } from '../../api/http'
import CrudTable from '../../components/CrudTable'
import ImageListUpload from '../../components/ImageListUpload'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'

type Row = Record<string, unknown>

/** Publicités affichées sur les écrans des stations (Main/Advert d'origine). */
export default function Advert() {
  const load = useCallback(async () => (await get<PageResult<Row>>('/adverts', { page: 1, size: 1000 })).rows, [])
  return (
    <>
      <PageHeader title="Publicités" section="Autres" />
      <Card>
        <CrudTable<Row>
          rowKey="id"
          load={load}
          addLabel="Ajouter une publicité"
          modalWidth={680}
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: "N° d'application (code station)", dataIndex: 'stationCode' },
            { title: 'Heure de début', dataIndex: 'startTime', render: (v) => `${v} h` },
            { title: 'Heure de fin', dataIndex: 'endTime', render: (v) => `${v} h` },
            {
              title: 'Images', dataIndex: 'images',
              render: (v: string[]) => (
                <Image.PreviewGroup>
                  <Space>{(v ?? []).map((u) => <Image key={u} src={u} width={48} height={48} style={{ objectFit: 'cover' }} />)}</Space>
                </Image.PreviewGroup>
              ),
            },
          ]}
          editValues={(r) => ({ stationId: r.stationId, startTime: r.startTime, endTime: r.endTime, images: r.images })}
          createValues={() => ({ startTime: 0, endTime: 24, images: [] })}
          renderForm={() => (
            <>
              <Form.Item name="stationId" label="Station" rules={[{ required: true }]}>
                <RemoteSelect type="TCPAllStation" style={{ width: '100%' }} />
              </Form.Item>
              <Space size="large">
                <Form.Item name="startTime" label="Heure de début" rules={[{ required: true }]}>
                  <InputNumber min={0} max={24} />
                </Form.Item>
                <Form.Item name="endTime" label="Heure de fin" rules={[{ required: true }]}>
                  <InputNumber min={0} max={24} />
                </Form.Item>
              </Space>
              <Form.Item name="images" label="Images">
                <ImageListUpload />
              </Form.Item>
            </>
          )}
          onCreate={(v) => post('/adverts', v)}
          onUpdate={(r, v) => put(`/adverts/${r.id}`, v)}
          onDelete={(r) => del(`/adverts/${r.id}`)}
        />
      </Card>
    </>
  )
}
