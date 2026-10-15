import { useCallback } from 'react'
import { Card, Col, Form, Input, Row, Select, Tabs } from 'antd'
import { del, get, post, put } from '../../api/http'
import CrudTable from '../../components/CrudTable'
import DmsInput from '../../components/DmsInput'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'

const STATION_TYPES = [
  { value: 1, label: 'Station' },
  { value: 2, label: 'Point d’émission de cartes' },
  { value: 3, label: 'Centre' },
]

type Row = Record<string, unknown>
const base = '/station-setting'

function Regions() {
  const load = useCallback(() => get<Row[]>(`${base}/regions`), [])
  return (
    <CrudTable<Row>
      rowKey="regionId"
      load={load}
      addLabel="Ajouter une région"
      modalTitle={(m) => (m === 'create' ? 'Ajouter une région' : 'Informations de la région')}
      columns={[
        { title: 'ID', dataIndex: 'regionId' },
        { title: 'Code de la région', dataIndex: 'codeInner' },
        { title: 'Nom de la région', dataIndex: 'regionDesc' },
      ]}
      createValues={() => get(`${base}/regions/next-code`)}
      renderForm={() => (
        <>
          <Form.Item name="codeInner" label="Code de la région">
            <Input readOnly />
          </Form.Item>
          <Form.Item name="code" label="Codification de la région" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="regionDesc" label="Nom de la région" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="companyId" label="Compte de rattachement">
            <RemoteSelect type="Company" />
          </Form.Item>
        </>
      )}
      onCreate={(v) => post(`${base}/regions`, v)}
      onUpdate={(r, v) => put(`${base}/regions/${r.regionId}`, v)}
      onDelete={(r) => del(`${base}/regions/${r.regionId}`)}
    />
  )
}

function Cities() {
  const load = useCallback(() => get<Row[]>(`${base}/cities`), [])
  return (
    <CrudTable<Row>
      rowKey="cityId"
      load={load}
      addLabel="Ajouter une ville"
      modalTitle={(m) => (m === 'create' ? 'Ajouter une ville' : 'Modifier la ville')}
      columns={[
        { title: 'ID', dataIndex: 'cityId' },
        { title: 'Code de la ville', dataIndex: 'codeInner' },
        { title: 'Nom de la ville', dataIndex: 'cityDesc' },
      ]}
      renderForm={(mode, form) => (
        <>
          <Form.Item name="regionId" label="Région" rules={[{ required: true }]}>
            <RemoteSelect
              type="RegionAuth"
              onChange={async (regionId) => {
                if (regionId == null) return
                const cityId = mode === 'edit' ? form.getFieldValue('cityId') : undefined
                form.setFieldsValue(await get(`${base}/cities/next-code`, { regionId, cityId }))
              }}
            />
          </Form.Item>
          <Form.Item name="cityId" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="codeInner" label="Code de la ville">
            <Input readOnly />
          </Form.Item>
          <Form.Item name="code" label="Codification de la ville">
            <Input readOnly />
          </Form.Item>
          <Form.Item name="cityDesc" label="Nom de la ville" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </>
      )}
      onCreate={(v) => post(`${base}/cities`, v)}
      onUpdate={(r, v) => put(`${base}/cities/${r.cityId}`, v)}
      onDelete={(r) => del(`${base}/cities/${r.cityId}`)}
    />
  )
}

function StationForm() {
  const regionId = Form.useWatch('regionId', Form.useFormInstance())
  return (
    <Row gutter={16}>
      <Col span={12}>
        <Form.Item name="regionId" label="Région" rules={[{ required: true }]}>
          <RemoteSelect type="RegionAuth" style={{ width: '100%' }} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="cityId" label="Ville" rules={[{ required: true }]}>
          <RemoteSelect type="CityAuth" params={{ regionId }} enabled={regionId != null} style={{ width: '100%' }} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="stationTypeId" label="Type de station" initialValue={1}>
          <Select options={STATION_TYPES} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="code" label="Code de la station">
          <Input readOnly />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="name" label="Nom de la station" rules={[{ required: true, message: 'Saisissez le nom de la station' }]}>
          <Input maxLength={20} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="address" label="Adresse">
          <Input maxLength={50} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="phone" label="Téléphone" rules={[{ pattern: /^[0-9+ ]*$/, message: 'Saisissez un nombre' }]}>
          <Input maxLength={15} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="fax" label="Fax">
          <Input maxLength={15} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="manager" label="Responsable">
          <Input maxLength={20} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="email" label="E-mail">
          <Input maxLength={50} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="postal" label="Code postal">
          <Input maxLength={10} />
        </Form.Item>
      </Col>
      <Col span={12} />
      <Col span={12}>
        <Form.Item name="longitude" label="Longitude">
          <DmsInput />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="latitude" label="Latitude">
          <DmsInput />
        </Form.Item>
      </Col>
    </Row>
  )
}

function Stations() {
  const load = useCallback(() => get<Row[]>(`${base}/stations`), [])
  return (
    <CrudTable<Row>
      rowKey="stationId"
      load={load}
      addLabel="Ajouter une station"
      modalWidth={760}
      modalTitle={(m) => (m === 'create' ? 'Ajouter une station' : 'Modifier la station')}
      columns={[
        { title: 'ID', dataIndex: 'stationId' },
        { title: 'Code de la station', dataIndex: 'code' },
        { title: 'Nom de la station', dataIndex: 'name' },
        { title: 'Longitude', dataIndex: 'longitude' },
        { title: 'Latitude', dataIndex: 'latitude' },
      ]}
      createValues={async () => ({ ...(await get<Row>(`${base}/stations/next-code`)), stationTypeId: 1 })}
      editValues={(r) => ({ ...r, email: r.email1 })}
      renderForm={() => <StationForm />}
      onCreate={(v) => post(`${base}/stations`, v)}
      onUpdate={(r, v) => put(`${base}/stations/${r.stationId}`, v)}
      onDelete={(r) => del(`${base}/stations/${r.stationId}`)}
    />
  )
}

function OilTypes() {
  const load = useCallback(() => get<Row[]>(`${base}/oil-types`), [])
  return (
    <CrudTable<Row>
      rowKey="gradeSpecificId"
      load={load}
      addLabel="Ajouter une énergie"
      modalTitle={(m) => (m === 'create' ? 'Ajouter une énergie' : 'Modifier l’énergie')}
      columns={[
        { title: 'ID', dataIndex: 'gradeSpecificId' },
        { title: 'Code de l’énergie', dataIndex: 'code' },
        { title: 'Nom de l’énergie', dataIndex: 'name' },
      ]}
      createValues={() => get(`${base}/oil-types/next-code`)}
      renderForm={() => (
        <>
          <Form.Item name="code" label="Code de l’énergie">
            <Input readOnly />
          </Form.Item>
          <Form.Item name="name" label="Nom de l’énergie" rules={[{ required: true }]}>
            <Input maxLength={30} />
          </Form.Item>
        </>
      )}
      onCreate={(v) => post(`${base}/oil-types`, v)}
      onUpdate={(r, v) => put(`${base}/oil-types/${r.gradeSpecificId}`, v)}
      onDelete={(r) => del(`${base}/oil-types/${r.gradeSpecificId}`)}
    />
  )
}

/** Configuration des stations (Main/StationSeting d'origine). */
export default function StationSetting() {
  return (
    <>
      <PageHeader title="Configuration des stations" section="Paramètres de base" />
      <Card>
        <Tabs
          destroyInactiveTabPane
          items={[
            { key: 'region', label: 'Régions', children: <Regions /> },
            { key: 'city', label: 'Villes', children: <Cities /> },
            { key: 'station', label: 'Stations', children: <Stations /> },
            { key: 'oil', label: 'Énergies', children: <OilTypes /> },
          ]}
        />
      </Card>
    </>
  )
}
