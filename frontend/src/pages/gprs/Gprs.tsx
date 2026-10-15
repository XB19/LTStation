import { useCallback, useEffect, useState } from 'react'
import {
  Button, Card, Col, DatePicker, Descriptions, Empty, Form, Input, InputNumber, Modal, Radio, Row, Select, Space, Switch, Table, Tabs,
  Tag, Upload, message,
} from 'antd'
import { ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import dayjs from 'dayjs'
import { del, get, http, post, put, type SelectItem } from '../../api/http'
import PageHeader from '../../components/PageHeader'
import CrudTable from '../../components/CrudTable'
import SearchTable, { serializeFilters, type SearchResult } from '../../components/SearchTable'
import { fmtDate, fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>
const SECTION = 'GPRS'
const DIGITS = [0, 1, 2, 3].map((value) => ({ value, label: String(value) }))

/** Liste déroulante GPRS (GPRS/GetSelect d'origine). */
function GprsSelect({ kind, stationId, ...rest }: { kind: 'Station' | 'OilType' | 'NozzleSelect'; stationId?: number } & Record<string, unknown>) {
  const [options, setOptions] = useState<SelectItem[]>([])
  useEffect(() => {
    if (kind === 'NozzleSelect' && !stationId) return setOptions([])
    get<SelectItem[]>(`/gprs/select/${kind}`, { stationId }).then(setOptions)
  }, [kind, stationId])
  return (
    <Select allowClear showSearch optionFilterProp="label" style={{ minWidth: 180 }}
      options={options.map((o) => ({ value: o.id, label: o.text }))} {...rest} />
  )
}

// ------------------------------------------------------------------ Tableau de bord GPRS

/** Accueil des clients GPRS : volumes et montants mensuels de l'année (GPRS/GetChartData d'origine). */
export function GprsDashboard() {
  const [data, setData] = useState<{ volume: number[]; amount: number[] } | null>(null)
  useEffect(() => {
    get<{ volume: number[]; amount: number[] }>('/gprs/chart').then(setData)
  }, [])
  const months = Array.from({ length: 12 }, (_, i) => `${i + 1}`.padStart(2, '0'))
  return (
    <>
      <PageHeader title="Accueil" section={SECTION} />
      <Card title={`Ventes ${dayjs().year()}`}>
        {data ? (
          <ReactECharts style={{ height: 380 }} option={{
            tooltip: { trigger: 'axis' },
            legend: { data: ['Volume (L)', 'Montant'] },
            xAxis: { type: 'category', data: months },
            yAxis: [{ type: 'value', name: 'L' }, { type: 'value', name: 'Montant' }],
            series: [
              { name: 'Volume (L)', type: 'bar', data: data.volume },
              { name: 'Montant', type: 'line', yAxisIndex: 1, data: data.amount },
            ],
          }} />
        ) : <Empty />}
      </Card>
    </>
  )
}

// ------------------------------------------------------------------ Clients

const ROLES: Record<number, string> = { 0: 'Administrateur', 1: 'Revendeur', 2: 'Client' }
const TYPES: Record<number, string> = { 0: 'Client GPRS', 1: 'Employé', 2: 'Compte groupe' }

/** Liste des comptes clients (GPRS/CustomerList d'origine). */
export function GprsCustomers() {
  const load = useCallback(() => get<Row[]>('/gprs/customers'), [])
  return (
    <>
      <PageHeader title="Liste des clients" section={SECTION} />
      <Card>
        <CrudTable<Row>
          rowKey="id" load={load} addLabel="Ajouter un client"
          modalTitle={(m) => (m === 'create' ? 'Nouveau client' : 'Modifier le client')}
          columns={[
            { title: 'N°', dataIndex: 'id' },
            { title: 'Compte', dataIndex: 'userName' },
            { title: 'Rôle', dataIndex: 'roleId', render: (v) => ROLES[Number(v)] ?? String(v ?? '') },
            { title: 'Type', dataIndex: 'typeId', render: (v) => <Tag>{TYPES[Number(v)] ?? String(v ?? '')}</Tag> },
            { title: 'Créé par', dataIndex: 'belongToId' },
            { title: 'Création', dataIndex: 'createTime', render: fmtDateTime },
          ]}
          renderForm={() => (
            <>
              <Form.Item name="userName" label="Nom du compte" rules={[{ required: true }]}><Input /></Form.Item>
              <Form.Item name="password" label="Mot de passe" rules={[{ required: true }]}><Input.Password /></Form.Item>
            </>
          )}
          editValues={(r) => ({ userName: r.userName, password: r.password })}
          onCreate={(v) => post('/gprs/customers', v)}
          onUpdate={(r, v) => put(`/gprs/customers/${r.id}`, v)}
          onDelete={(r) => del(`/gprs/customers/${r.id}`)}
        />
      </Card>
    </>
  )
}

/** Longueur des ICCID par zone (GPRS/ICCIDLenSet d'origine). */
export function GprsIccidLen() {
  const load = useCallback(() => get<Row[]>('/gprs/iccid'), [])
  return (
    <>
      <PageHeader title="Traitement des zones spéciales" section={SECTION} />
      <Card>
        <CrudTable<Row>
          rowKey="id" load={load} addLabel="Ajouter une zone"
          modalTitle={(m) => (m === 'create' ? 'Nouvelle zone' : 'Modifier la zone')}
          columns={[
            { title: 'Zone', dataIndex: 'name' },
            { title: 'Code ICCID', dataIndex: 'iccidType' },
            { title: 'Longueur ICCID', dataIndex: 'iccidLength' },
          ]}
          renderForm={() => (
            <>
              <Form.Item name="name" label="Zone" rules={[{ required: true }]}><Input /></Form.Item>
              <Form.Item name="iccidType" label="Code ICCID" rules={[{ required: true }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item name="iccidLength" label="Longueur ICCID" rules={[{ required: true }]}><InputNumber min={1} max={100} style={{ width: '100%' }} /></Form.Item>
            </>
          )}
          onCreate={(v) => post('/gprs/iccid', v)}
          onUpdate={(r, v) => put(`/gprs/iccid/${r.id}`, v)}
          onDelete={(r) => del(`/gprs/iccid/${r.id}`)}
        />
      </Card>
    </>
  )
}

// ------------------------------------------------------------------ Stations et énergies

/** Stations et énergies/prix (GPRS/StationSet d'origine). */
export function GprsStations() {
  const loadStations = useCallback(() => get<Row[]>('/gprs/stations'), [])
  const loadOils = useCallback(() => get<Row[]>('/gprs/oils'), [])
  return (
    <>
      <PageHeader title="Configuration des stations" section={SECTION} />
      <Row gutter={16}>
        <Col xs={24} xl={15}>
          <Card title="Stations" style={{ marginBottom: 16 }}>
            <CrudTable<Row>
              rowKey="stationId" load={loadStations} addLabel="Ajouter une station" modalWidth={640}
              modalTitle={(m) => (m === 'create' ? 'Nouvelle station' : 'Modifier la station')}
              columns={[
                { title: 'N°', dataIndex: 'stationId' },
                { title: 'Nom', dataIndex: 'stationName' },
                { title: 'Adresse', dataIndex: 'stationAddress' },
                { title: 'Longitude', dataIndex: 'longitude' },
                { title: 'Latitude', dataIndex: 'latitude' },
              ]}
              createValues={() => ({ amountDigits: 2, volumeDigits: 2, priceDigits: 2 })}
              renderForm={() => (
                <>
                  <Form.Item name="stationName" label="Nom" rules={[{ required: true }]}><Input maxLength={50} /></Form.Item>
                  <Form.Item name="stationAddress" label="Adresse"><Input /></Form.Item>
                  <Row gutter={8}>
                    <Col span={12}><Form.Item name="managerName" label="Responsable"><Input /></Form.Item></Col>
                    <Col span={12}><Form.Item name="telePhone" label="Téléphone"><Input maxLength={50} /></Form.Item></Col>
                    <Col span={12}><Form.Item name="longitude" label="Longitude"><InputNumber min={-180} max={180} step={0.000001} style={{ width: '100%' }} /></Form.Item></Col>
                    <Col span={12}><Form.Item name="latitude" label="Latitude"><InputNumber min={-90} max={90} step={0.000001} style={{ width: '100%' }} /></Form.Item></Col>
                    <Col span={8}><Form.Item name="amountDigits" label="Décimales montant"><Select options={DIGITS} /></Form.Item></Col>
                    <Col span={8}><Form.Item name="volumeDigits" label="Décimales volume"><Select options={DIGITS} /></Form.Item></Col>
                    <Col span={8}><Form.Item name="priceDigits" label="Décimales prix"><Select options={DIGITS} /></Form.Item></Col>
                  </Row>
                </>
              )}
              onCreate={(v) => post('/gprs/stations', v)}
              onUpdate={(r, v) => put(`/gprs/stations/${r.stationId}`, v)}
              onDelete={(r) => del(`/gprs/stations/${r.stationId}`)}
            />
          </Card>
        </Col>
        <Col xs={24} xl={9}>
          <Card title="Énergies et prix">
            <CrudTable<Row>
              rowKey="id" load={loadOils} addLabel="Ajouter une énergie"
              modalTitle={(m) => (m === 'create' ? 'Nouvelle énergie' : "Modifier l'énergie")}
              columns={[
                { title: 'Énergie', dataIndex: 'oilName' },
                { title: 'Prix', dataIndex: 'oilPriceCur', render: (v) => fmtNum(v, 3) },
              ]}
              editValues={(r) => ({ oilName: r.oilName, price: r.oilPriceCur })}
              renderForm={() => (
                <>
                  <Form.Item name="oilName" label="Énergie" rules={[{ required: true }]}><Input maxLength={50} /></Form.Item>
                  <Form.Item name="price" label="Prix" rules={[{ required: true }]} extra="Un changement de prix est envoyé aux pistolets.">
                    <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
                  </Form.Item>
                </>
              )}
              onCreate={(v) => post('/gprs/oils', v)}
              onUpdate={(r, v) => put(`/gprs/oils/${r.id}`, v)}
              onDelete={(r) => del(`/gprs/oils/${r.id}`)}
            />
          </Card>
        </Col>
      </Row>
    </>
  )
}

// ------------------------------------------------------------------ Pistolets

/** Pistolets (terminal ICCID + n° de pistolet) et commandes associées (GPRS/NozzleSet d'origine). */
export function GprsNozzles() {
  const [stationId, setStationId] = useState<number>()
  const load = useCallback(() => get<Row[]>('/gprs/nozzles', { stationId }), [stationId])
  return (
    <>
      <PageHeader title="Configuration des pistolets" section={SECTION} />
      <Card>
        <Space style={{ marginBottom: 16 }}>
          <span>Station :</span>
          <GprsSelect kind="Station" value={stationId} onChange={setStationId} placeholder="Toutes" />
        </Space>
        <CrudTable<Row>
          rowKey="id" load={load} addLabel="Ajouter un pistolet"
          modalTitle={(m) => (m === 'create' ? 'Nouveau pistolet' : 'Modifier le pistolet')}
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: 'ICCID', dataIndex: 'iccid' },
            { title: 'Pistolet', dataIndex: 'gunId' },
            { title: 'Énergie', dataIndex: 'oilName' },
            { title: 'Prix', dataIndex: 'price', render: (v) => fmtNum(v, 3) },
            { title: 'Totalisateur', dataIndex: 'totalliser', render: (v) => fmtNum(v) },
            { title: 'Verrouillé', dataIndex: 'isLock', render: (v) => (Number(v) === 1 ? <Tag color="red">Oui</Tag> : 'Non') },
          ]}
          createValues={() => ({ stationId })}
          editValues={(r) => ({ ...r, lock: Number(r.isLock ?? 0) })}
          renderForm={(mode) => (
            <>
              <Form.Item name="stationId" label="Station" rules={[{ required: true }]}><GprsSelect kind="Station" /></Form.Item>
              <Form.Item name="iccid" label="ICCID" rules={[{ required: true }]}><Input maxLength={100} /></Form.Item>
              <Form.Item name="gunId" label="N° de pistolet" rules={[{ required: true }]}><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
              <Form.Item name="oilId" label="Énergie" rules={[{ required: true }]}><GprsSelect kind="OilType" /></Form.Item>
              {mode === 'edit' && (
                <>
                  <Form.Item name="totalliser" label="Totalisateur"><InputNumber style={{ width: '100%' }} /></Form.Item>
                  <Form.Item name="calibration" label="Étalonnage (impulsions)" extra="Laisser à 0 pour ne pas envoyer d'étalonnage.">
                    <InputNumber style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="lock" label="Verrouillage">
                    <Radio.Group options={[{ value: 0, label: 'Déverrouillé' }, { value: 1, label: 'Verrouillé' }]} />
                  </Form.Item>
                </>
              )}
            </>
          )}
          onCreate={(v) => post('/gprs/nozzles', v)}
          onUpdate={(r, v) => put(`/gprs/nozzles/${r.id}`, v)}
          onDelete={(r) => del(`/gprs/nozzles/${r.id}`)}
        />
      </Card>
    </>
  )
}

// ------------------------------------------------------------------ Cuves

/** Cuves et barème de jaugeage importé en CSV (GPRS/TankSet d'origine). */
export function GprsTanks() {
  const [table, setTable] = useState<{ tank: Row; rows: Row[] } | null>(null)
  const load = useCallback(() => get<Row[]>('/gprs/tanks'), [])
  const send = (url: string, v: Record<string, unknown>) => {
    const fd = new FormData()
    fd.append('gradeId', String(v.gradeId))
    fd.append('tankHeight', String(v.tankHeight))
    fd.append('tankVolume', String(v.tankVolume))
    const files = v.volumeTable as { originFileObj?: File }[] | undefined
    if (files?.[0]?.originFileObj) fd.append('volumeTable', files[0].originFileObj)
    return http.post(url, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  }
  return (
    <>
      <PageHeader title="Configuration des cuves" section={SECTION} />
      <Card>
        <CrudTable<Row>
          rowKey="id" load={load} addLabel="Ajouter une cuve"
          modalTitle={(m) => (m === 'create' ? 'Nouvelle cuve' : 'Modifier la cuve')}
          columns={[
            { title: 'Énergie', dataIndex: 'gradeName' },
            { title: 'Hauteur (mm)', dataIndex: 'tankHeight', render: (v) => fmtNum(v) },
            { title: 'Volume (L)', dataIndex: 'tankVolume', render: (v) => fmtNum(v) },
            { title: 'Hauteur actuelle', dataIndex: 'tankRealHeight', render: (v) => fmtNum(v) },
            { title: 'Volume actuel', dataIndex: 'tankRealVolume', render: (v) => fmtNum(v) },
            {
              title: 'Barème', dataIndex: 'volumeRows',
              render: (v, r) => (
                <a onClick={async () => setTable({ tank: r, rows: await get<Row[]>(`/gprs/tanks/${r.id}/volume-table`) })}>{String(v ?? 0)} lignes</a>
              ),
            },
          ]}
          editValues={(r) => ({ gradeId: r.gradeId, tankHeight: r.tankHeight, tankVolume: r.tankVolume })}
          renderForm={(mode) => (
            <>
              <Form.Item name="gradeId" label="Énergie" rules={[{ required: true }]}><GprsSelect kind="OilType" /></Form.Item>
              <Form.Item name="tankHeight" label="Hauteur de la cuve (mm)" rules={[{ required: true }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item name="tankVolume" label="Volume de la cuve (L)" rules={[{ required: true }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
              <Form.Item name="volumeTable" label="Barème de jaugeage (CSV « hauteur,volume »)" valuePropName="fileList"
                getValueFromEvent={(e) => e?.fileList?.slice(-1)}
                rules={mode === 'create' ? [{ required: true, message: 'Importez le barème' }] : []}
                extra={mode === 'edit' ? 'Laisser vide pour conserver le barème actuel.' : undefined}>
                <Upload beforeUpload={() => false} maxCount={1} accept=".csv,.txt"><Button icon={<UploadOutlined />}>Choisir un fichier</Button></Upload>
              </Form.Item>
            </>
          )}
          onCreate={(v) => send('/gprs/tanks', v)}
          onUpdate={(r, v) => send(`/gprs/tanks/${r.id}`, v)}
          onDelete={(r) => del(`/gprs/tanks/${r.id}`)}
        />
      </Card>
      <Modal title={`Barème de jaugeage — ${String(table?.tank.gradeName ?? '')}`} open={!!table} footer={null} onCancel={() => setTable(null)}>
        <Table<Row> size="small" rowKey="height" dataSource={table?.rows} pagination={{ pageSize: 15 }} columns={[
          { title: 'Hauteur (mm)', dataIndex: 'height', render: (v) => fmtNum(v) },
          { title: 'Volume (L)', dataIndex: 'volume', render: (v) => fmtNum(v) },
        ]} />
      </Modal>
    </>
  )
}

// ------------------------------------------------------------------ État des pistolets

/** État et données courantes des pistolets, surveillance (GPRS/NozzleSearch d'origine). */
export function GprsNozzleSearch() {
  const [stationId, setStationId] = useState<number>()
  const [rows, setRows] = useState<Row[]>([])
  const [loading, setLoading] = useState(false)
  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await get<Row[]>('/gprs/nozzle-status', { stationId }))
    } finally {
      setLoading(false)
    }
  }, [stationId])
  useEffect(() => {
    load()
  }, [load])
  const monitored = rows.length > 0 && rows.every((r) => r.isMonitor)
  return (
    <>
      <PageHeader title="État des pistolets" section={SECTION} />
      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <span>Station :</span>
          <GprsSelect kind="Station" value={stationId} onChange={setStationId} placeholder="Toutes" />
          <span>Surveillance :</span>
          <Switch checked={monitored} disabled={rows.length === 0} onChange={async (monitor) => {
            await post('/gprs/monitor', { stationId: stationId ?? 0, monitor })
            message.success(monitor ? 'Surveillance activée' : 'Surveillance désactivée')
            load()
          }} />
          <Button icon={<ReloadOutlined />} onClick={load} />
        </Space>
        <Table<Row> rowKey="id" loading={loading} dataSource={rows} columns={[
          { title: 'Station', dataIndex: 'stationName' },
          { title: 'ICCID', dataIndex: 'iccid' },
          { title: 'Pistolet', dataIndex: 'gunId' },
          { title: 'Énergie', dataIndex: 'oilName' },
          { title: 'Prix', dataIndex: 'price', render: (v) => fmtNum(v, 3) },
          { title: 'Connexion', dataIndex: 'isConnect', render: (v) => (v ? <Tag color="green">Connecté</Tag> : <Tag>Déconnecté</Tag>) },
          { title: 'Surveillance', dataIndex: 'isMonitor', render: (v) => (v ? <Tag color="blue">Oui</Tag> : 'Non') },
          { title: 'Montant en cours', dataIndex: 'amount', render: (v) => fmtNum(v) },
          { title: 'Volume en cours', dataIndex: 'volume', render: (v) => fmtNum(v) },
          { title: 'Totalisateur', dataIndex: 'totalliser', render: (v) => fmtNum(v) },
        ]} />
      </Card>
    </>
  )
}

// ------------------------------------------------------------------ Transactions

type Period = [dayjs.Dayjs, dayjs.Dayjs]
const defaultPeriod = (): Period => [dayjs().subtract(1, 'month').startOf('day'), dayjs().add(1, 'day').startOf('day')]

/** Transactions des terminaux GPRS (GPRS/Transaction d'origine). */
export function GprsTransactions() {
  const [form] = Form.useForm()
  const stationId = Form.useWatch('stationId', form)
  return (
    <>
      <PageHeader title="Transactions" section={SECTION} />
      <Card>
        <SearchTable<Row>
          url="/gprs/transactions" rowKey="id" form={form}
          initialFilters={{ period: defaultPeriod() }}
          toParams={(v) => {
            const { period, ...rest } = v as { period?: Period }
            return serializeFilters({ ...rest, start: period?.[0], end: period?.[1] })
          }}
          filters={
            <>
              <Form.Item name="stationId" label="Station"><GprsSelect kind="Station" onChange={() => form.setFieldValue('gunId', undefined)} /></Form.Item>
              <Form.Item name="oilId" label="Énergie"><GprsSelect kind="OilType" /></Form.Item>
              <Form.Item name="gunId" label="Pistolet"><GprsSelect kind="NozzleSelect" stationId={stationId} style={{ width: 110 }} /></Form.Item>
              <Form.Item name="period" label="Période" rules={[{ required: true }]}><DatePicker.RangePicker showTime /></Form.Item>
            </>
          }
          summary={(r: SearchResult<Row>) => (
            <Descriptions size="small" column={3}>
              <Descriptions.Item label="Nombre de transactions">{String(r.sumCount ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="Volume total">{fmtNum(r.sumVolume)} L</Descriptions.Item>
              <Descriptions.Item label="Montant total">{fmtNum(r.sumAmount)}</Descriptions.Item>
            </Descriptions>
          )}
          columns={[
            { title: 'N°', dataIndex: 'id' },
            { title: 'Station', dataIndex: 'stationName' },
            { title: 'ICCID', dataIndex: 'iccid' },
            { title: 'Pistolet', dataIndex: 'gunId' },
            { title: 'Énergie', dataIndex: 'oilName' },
            { title: 'Prix', dataIndex: 'price', render: (v) => fmtNum(v, 3) },
            { title: 'Montant', dataIndex: 'amount', render: (v) => fmtNum(v) },
            { title: 'Volume', dataIndex: 'volume', render: (v) => fmtNum(v) },
            { title: 'Totalisateur', dataIndex: 'volumeTotal', render: (v) => fmtNum(v) },
            { title: 'Carte ID', dataIndex: 'idCard' },
            { title: 'Date', dataIndex: 'createTime', render: fmtDateTime },
          ]}
        />
      </Card>
    </>
  )
}

// ------------------------------------------------------------------ Rapports

function ShiftReport() {
  const [rows, setRows] = useState<Row[]>([])
  const [loading, setLoading] = useState(false)
  const run = async (v: { stationId?: number; period: Period }) => {
    setLoading(true)
    try {
      setRows(await get<Row[]>('/gprs/reports/shifts', {
        stationId: v.stationId, start: v.period[0].format('YYYY-MM-DD'), end: v.period[1].format('YYYY-MM-DD'),
      }))
    } finally {
      setLoading(false)
    }
  }
  return (
    <>
      <Form layout="inline" initialValues={{ period: defaultPeriod() }} onFinish={run} style={{ marginBottom: 16 }}>
        <Form.Item name="stationId" label="Station"><GprsSelect kind="Station" /></Form.Item>
        <Form.Item name="period" label="Période" rules={[{ required: true }]}><DatePicker.RangePicker /></Form.Item>
        <Button type="primary" htmlType="submit">Rechercher</Button>
      </Form>
      <Table<Row> rowKey={(r) => `${r.stationId}-${r.shiftNo}`} loading={loading} dataSource={rows}
        expandable={{
          expandedRowRender: (r) => (
            <Table<Row> size="small" rowKey={(d) => `${d.iccid}-${d.gunId}-${d.createTime}`} pagination={false} dataSource={r.details as Row[]} columns={[
              { title: 'ICCID', dataIndex: 'iccid' },
              { title: 'Pistolet', dataIndex: 'gunId' },
              { title: 'Montant', dataIndex: 'amount', render: (v) => fmtNum(v) },
              { title: 'Volume', dataIndex: 'volume', render: (v) => fmtNum(v) },
              { title: 'Date', dataIndex: 'createTime', render: fmtDateTime },
            ]} />
          ),
        }}
        columns={[
          { title: 'Station', dataIndex: 'stationName' },
          { title: 'Poste', dataIndex: 'shiftNo' },
          { title: 'Montant', dataIndex: 'amount', render: (v) => fmtNum(v) },
          { title: 'Volume', dataIndex: 'volume', render: (v) => fmtNum(v) },
        ]} />
    </>
  )
}

function DayReport() {
  const [rows, setRows] = useState<Row[]>([])
  const [loading, setLoading] = useState(false)
  const run = async (v: { stationId?: number; period: Period }) => {
    setLoading(true)
    try {
      setRows(await get<Row[]>('/gprs/reports/days', serializeFilters({ stationId: v.stationId, start: v.period[0], end: v.period[1] })))
    } finally {
      setLoading(false)
    }
  }
  const sum = (k: string) => rows.reduce((a, r) => a + Number(r[k] ?? 0), 0)
  return (
    <>
      <Form layout="inline" initialValues={{ period: defaultPeriod() }} onFinish={run} style={{ marginBottom: 16 }}>
        <Form.Item name="stationId" label="Station"><GprsSelect kind="Station" /></Form.Item>
        <Form.Item name="period" label="Période" rules={[{ required: true }]}><DatePicker.RangePicker showTime /></Form.Item>
        <Button type="primary" htmlType="submit">Rechercher</Button>
      </Form>
      <Table<Row> rowKey={(r) => `${r.stationId}-${r.dayTime}`} loading={loading} dataSource={rows}
        summary={() => rows.length ? (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0} colSpan={2}><b>Total</b></Table.Summary.Cell>
            <Table.Summary.Cell index={2}><b>{fmtNum(sum('amount'))}</b></Table.Summary.Cell>
            <Table.Summary.Cell index={3}><b>{fmtNum(sum('volume'))}</b></Table.Summary.Cell>
          </Table.Summary.Row>
        ) : null}
        columns={[
          { title: 'Station', dataIndex: 'stationName' },
          { title: 'Jour', dataIndex: 'dayTime', render: fmtDate },
          { title: 'Montant', dataIndex: 'amount', render: (v) => fmtNum(v) },
          { title: 'Volume', dataIndex: 'volume', render: (v) => fmtNum(v) },
        ]} />
    </>
  )
}

function IdCardReport() {
  const [rows, setRows] = useState<Row[]>([])
  const [loading, setLoading] = useState(false)
  const run = async (v: { idCard?: string; period: Period }) => {
    setLoading(true)
    try {
      setRows(await get<Row[]>('/gprs/reports/id-cards', serializeFilters({ idCard: v.idCard, start: v.period[0], end: v.period[1] })))
    } finally {
      setLoading(false)
    }
  }
  const sum = (k: string) => rows.reduce((a, r) => a + Number(r[k] ?? 0), 0)
  return (
    <>
      <Form layout="inline" initialValues={{ period: defaultPeriod() }} onFinish={run} style={{ marginBottom: 16 }}>
        <Form.Item name="idCard" label="Carte ID"><Input allowClear /></Form.Item>
        <Form.Item name="period" label="Période" rules={[{ required: true }]}><DatePicker.RangePicker showTime /></Form.Item>
        <Button type="primary" htmlType="submit">Rechercher</Button>
      </Form>
      <Table<Row> rowKey={(r) => String(r.idCard ?? '')} loading={loading} dataSource={rows}
        summary={() => rows.length ? (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}><b>Total</b></Table.Summary.Cell>
            <Table.Summary.Cell index={1}><b>{fmtNum(sum('amount'))}</b></Table.Summary.Cell>
            <Table.Summary.Cell index={2}><b>{fmtNum(sum('volume'))}</b></Table.Summary.Cell>
          </Table.Summary.Row>
        ) : null}
        columns={[
          { title: 'Carte ID', dataIndex: 'idCard', render: (v) => String(v ?? '') || '—' },
          { title: 'Montant', dataIndex: 'amount', render: (v) => fmtNum(v) },
          { title: 'Volume', dataIndex: 'volume', render: (v) => fmtNum(v) },
        ]} />
    </>
  )
}

/** Rapports par poste, par jour et par carte (GPRS/ReportSearch d'origine). */
export function GprsReports() {
  return (
    <>
      <PageHeader title="Rapports" section={SECTION} />
      <Card>
        <Tabs items={[
          { key: 'shift', label: 'Par poste', children: <ShiftReport /> },
          { key: 'day', label: 'Journalier', children: <DayReport /> },
          { key: 'id', label: 'Par carte ID', children: <IdCardReport /> },
        ]} />
      </Card>
    </>
  )
}
