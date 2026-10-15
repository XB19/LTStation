import { useCallback, useState } from 'react'
import {
  Button, Card, Col, DatePicker, Form, Input, InputNumber, Modal, Row, Segmented, Select, Space, Table, Tabs, Tag,
  Tooltip, Upload, message,
} from 'antd'
import { KeyOutlined, LineChartOutlined, PrinterOutlined, TableOutlined, UploadOutlined } from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import dayjs from 'dayjs'
import { del, get, http, post, put } from '../../api/http'
import AirDeviceFormFields, { deviceTypeFromDigits } from '../../components/AirDeviceFormFields'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'
import RemoteSelect, { useSelectOptions } from '../../components/RemoteSelect'
import StationFilter, { type StationFilterValue } from '../../components/StationFilter'
import { fmtDateTime } from '../../utils/format'

type Row = Record<string, unknown>

const warn = (v: unknown, labels: [string, string]) =>
  Number(v) === 1 ? <Tag color="red">{labels[0]}</Tag> : Number(v) === 2 ? <Tag color="orange">{labels[1]}</Tag> : <Tag>Normal</Tag>

function TankFormFields() {
  const form = Form.useFormInstance()
  const stationId = Form.useWatch('stationId', form)
  const gradeId = Form.useWatch('gradeId', form)
  const loadPrice = async (s?: number, g?: number) => {
    if (s == null || g == null) return
    try {
      const r = await get<{ price: number }>('/air-setting/price', { stationId: s, gradeId: g })
      form.setFieldValue('price', r.price)
    } catch {
      form.setFieldValue('price', undefined)
    }
  }
  const num = (name: string, label: string, required = false) => (
    <Col span={12}>
      <Form.Item name={name} label={label} rules={required ? [{ required: true }] : []}>
        <InputNumber style={{ width: '100%' }} step={0.001} />
      </Form.Item>
    </Col>
  )
  return (
    <Row gutter={16}>
      <Col span={12}>
        <Form.Item name="stationId" label="Station" rules={[{ required: true }]}>
          <RemoteSelect type="Station" style={{ width: '100%' }} onChange={(v) => loadPrice(v, gradeId)} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="gradeId" label="Énergie" rules={[{ required: true }]}>
          <RemoteSelect type="TCPGrade" style={{ width: '100%' }} onChange={(v) => loadPrice(stationId, v)} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="price" label="Prix configuré">
          <InputNumber readOnly style={{ width: '100%' }} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="tankName" label="Nom de la cuve" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
      </Col>
      {num('totalHeight', 'Hauteur de la cuve', true)}
      {num('totalVolume', 'Capacité de la cuve', true)}
      {num('overHeight', 'Alerte niveau haut')}
      {num('lowerHeight', 'Alerte niveau bas')}
      {num('overTemp', 'Alerte température haute')}
      {num('lowerTemp', 'Alerte température basse')}
      {num('overWater', "Alerte niveau d'eau haut")}
      {num('lowerWater', "Alerte niveau d'eau bas")}
    </Row>
  )
}

function TankChart({ tank, onClose }: { tank: Row | null; onClose: () => void }) {
  const [type, setType] = useState(0)
  const [date, setDate] = useState(dayjs())
  const [data, setData] = useState<{ labels: string[]; values: (number | null)[] } | null>(null)
  const load = useCallback(async () => {
    if (!tank) return
    setData(await get(`/air-setting/tanks/${tank.id}/chart`, { type, date: date.format('YYYY-MM-DD') }))
  }, [tank, type, date])
  return (
    <Modal title={`Niveau de la cuve ${tank?.tankName ?? ''}`} open={!!tank} onCancel={onClose} footer={null} width={900}
      afterOpenChange={(o) => o && load()}>
      <Space style={{ marginBottom: 12 }}>
        <Segmented value={type} onChange={(v) => setType(Number(v))}
          options={[{ value: 0, label: 'Jour' }, { value: 1, label: 'Mois' }, { value: 2, label: 'Année' }]} />
        <DatePicker value={date} onChange={(d) => d && setDate(d)} picker={type === 0 ? 'date' : type === 1 ? 'month' : 'year'} />
        <Button onClick={load}>Afficher</Button>
      </Space>
      {data && (
        <ReactECharts style={{ height: 360 }} option={{
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: data.labels },
          yAxis: { type: 'value', name: 'Hauteur' },
          series: [{ name: 'Hauteur', type: 'line', smooth: true, connectNulls: true, data: data.values }],
        }} />
      )}
    </Modal>
  )
}

function VolumeTable({ tank, onClose }: { tank: Row | null; onClose: () => void }) {
  const [volumeId, setVolumeId] = useState<number>()
  const [rows, setRows] = useState<Row[]>([])
  const [reload, setReload] = useState(0)
  const options = useSelectOptions('TankVolume', { reload }, !!tank)
  const show = async (id?: number) => {
    setVolumeId(id)
    setRows(id == null ? [] : await get<Row[]>(`/air-setting/tank-volumes/${id}`))
  }
  const bind = async () => {
    if (!tank || volumeId == null) return
    await put(`/air-setting/tanks/${tank.id}/volume-table`, { tankVolumeId: volumeId })
    message.success('Opération réussie')
    onClose()
  }
  return (
    <Modal title="Barème de jaugeage" open={!!tank} onCancel={onClose} onOk={bind} okText="Associer à la cuve"
      cancelText="Fermer" width={700} afterOpenChange={(o) => o && show((tank?.tankVolumeId as number) || undefined)}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Select style={{ width: 240 }} placeholder="Choisir un barème" value={volumeId} onChange={show}
          options={options.map((o) => ({ value: o.id, label: o.text }))} />
        <Upload accept=".csv" showUploadList={false} customRequest={async ({ file, onSuccess }) => {
          const fd = new FormData()
          fd.append('file', file as Blob)
          const r = await http.post<{ tankVolumeId: number; rows: number }>('/air-setting/tank-volumes/import', fd, {
            headers: { 'Content-Type': 'multipart/form-data' },
          })
          message.success(`${r.data.rows} ligne(s) importée(s)`)
          setReload((k) => k + 1)
          show(r.data.tankVolumeId)
          onSuccess?.(r.data)
        }}>
          <Button icon={<UploadOutlined />}>Importer un barème (CSV hauteur,volume)</Button>
        </Upload>
      </Space>
      <Table size="small" rowKey="id" dataSource={rows} pagination={{ pageSize: 10 }} columns={[
        { title: 'Nom du barème', dataIndex: 'tankVolumeName' },
        { title: 'Hauteur', dataIndex: 'height' },
        { title: 'Volume', dataIndex: 'volume' },
      ]} />
    </Modal>
  )
}

function Tanks() {
  const [filter, setFilter] = useState<StationFilterValue>({})
  const [chart, setChart] = useState<Row | null>(null)
  const [volume, setVolume] = useState<Row | null>(null)
  const load = useCallback(() => get<Row[]>('/air-setting/tanks', filter), [filter])
  return (
    <>
      <CrudTable<Row>
        rowKey="id"
        load={load}
        addLabel="Ajouter une cuve"
        modalWidth={760}
        toolbar={<StationFilter value={filter} onChange={setFilter} />}
        columns={[
          { title: 'Station', dataIndex: 'stationName' },
          { title: "Type d'énergie", dataIndex: 'oilName' },
          { title: 'Nom de la cuve', dataIndex: 'tankName' },
          { title: 'Niveau actuel', dataIndex: 'currentHeight' },
          { title: 'Volume actuel', dataIndex: 'currentOil' },
          { title: 'Alerte niveau', dataIndex: 'levelWarning', render: (v) => warn(v, ['Haut', 'Bas']) },
          { title: 'Alerte température', dataIndex: 'tempWarning', render: (v) => warn(v, ['Haute', 'Basse']) },
          { title: 'Alerte eau', dataIndex: 'waterWarning', render: (v) => warn(v, ['Haute', 'Basse']) },
        ]}
        modalTitle={(m) => (m === 'create' ? 'Ajouter une cuve' : 'Modifier la cuve')}
        renderForm={() => <TankFormFields />}
        onCreate={(v) => post('/air-setting/tanks', v)}
        onUpdate={(r, v) => put(`/air-setting/tanks/${r.id}`, v)}
        onDelete={(r) => del(`/air-setting/tanks/${r.id}`)}
        extraActions={(r) => (
          <>
            <Tooltip title="Courbe de niveau">
              <Button size="small" type="text" icon={<LineChartOutlined />} onClick={() => setChart(r)} />
            </Tooltip>
            <Tooltip title="Barème de jaugeage">
              <Button size="small" type="text" icon={<TableOutlined />} onClick={() => setVolume(r)} />
            </Tooltip>
          </>
        )}
      />
      <TankChart tank={chart} onClose={() => setChart(null)} />
      <VolumeTable tank={volume} onClose={() => setVolume(null)} />
    </>
  )
}

function Nozzles() {
  const [filter, setFilter] = useState<StationFilterValue>({})
  const [activate, setActivate] = useState<Row | null>(null)
  const [print, setPrint] = useState<Row | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [codeForm] = Form.useForm()
  const [printForm] = Form.useForm()
  const load = useCallback(() => get<Row[]>('/air-setting/nozzles', filter), [filter])

  const saveCode = async () => {
    const v = await codeForm.validateFields()
    const r = await put<{ validTime: string }>(`/air-setting/nozzles/${activate!.id}/active-code`, v)
    message.success(`Appareil activé jusqu'au ${r.validTime}`)
    setActivate(null)
    setReloadKey((k) => k + 1)
  }
  const savePrint = async () => {
    const v = await printForm.validateFields()
    const r = await put<{ message: string }>(`/air-setting/nozzles/${print!.id}/print`, v)
    message.success(r.message)
    setPrint(null)
  }

  return (
    <>
      <CrudTable<Row>
        rowKey="id"
        load={load}
        reloadKey={reloadKey}
        addLabel="Ajouter un pistolet"
        modalWidth={700}
        toolbar={<StationFilter value={filter} onChange={setFilter} />}
        columns={[
          { title: 'Station', dataIndex: 'stationName' },
          { title: "Type d'énergie", dataIndex: 'oilName' },
          { title: 'N° de pistolet', dataIndex: 'nozId' },
          { title: "N° d'appareil", dataIndex: 'deviceNo' },
          {
            title: 'Décimales (montant, volume, prix)',
            render: (_, r) => `${r.amountDigits},${r.volumeDigits},${r.priceDigits}`,
          },
          { title: 'Création', dataIndex: 'createTime', render: fmtDateTime },
          {
            title: 'État',
            dataIndex: 'isOnline',
            render: (v) => (v ? <Tag color="green">En ligne</Tag> : <Tag color="red">Hors ligne</Tag>),
          },
        ]}
        modalTitle={(m) => (m === 'create' ? 'Ajouter un pistolet' : 'Modifier le pistolet')}
        editValues={(r) => ({
          stationId: r.stationId,
          tankId: r.tankNo ? Number(r.tankNo) : undefined,
          nozId: r.nozId,
          amountDigits: r.amountDigits,
          volumeDigits: r.volumeDigits,
          priceDigits: r.priceDigits,
          deviceType: deviceTypeFromDigits(r.digitsType as string),
          deviceNo: r.deviceNo,
        })}
        renderForm={() => <AirDeviceFormFields />}
        onCreate={(v) => post('/air-devices', v)}
        onUpdate={(r, v) => put(`/air-devices/${r.id}`, v)}
        onDelete={(r) => del(`/air-devices/${r.id}`)}
        extraActions={(r) => (
          <>
            <Tooltip title="Activer par code">
              <Button size="small" type="text" icon={<KeyOutlined />} onClick={() => {
                codeForm.resetFields()
                setActivate(r)
              }} />
            </Tooltip>
            <Tooltip title="Textes d'impression">
              <Button size="small" type="text" icon={<PrinterOutlined />} onClick={() => {
                printForm.setFieldsValue({ deviceNo: r.deviceNo, topText: '', bottomText: '' })
                setPrint(r)
              }} />
            </Tooltip>
          </>
        )}
      />
      <Modal title="Activation par code" open={!!activate} onOk={saveCode} onCancel={() => setActivate(null)}
        okText="Activer" cancelText="Annuler">
        <Form form={codeForm} layout="vertical">
          <Form.Item name="activeCode" label="Code d'activation" rules={[{ required: true }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal title="Textes d'impression du ticket" open={!!print} onOk={savePrint} onCancel={() => setPrint(null)}
        okText="Envoyer" cancelText="Annuler">
        <Form form={printForm} layout="vertical">
          <Form.Item name="deviceNo" label="N° d'appareil">
            <Input disabled />
          </Form.Item>
          <Form.Item name="topText" label="Texte d'en-tête">
            <Input maxLength={15} />
          </Form.Item>
          <Form.Item name="bottomText" label="Texte de pied">
            <Input maxLength={15} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

/** Appareils cloud (Main/AirSeting d'origine). */
export default function AirSetting() {
  return (
    <>
      <PageHeader title="Appareils cloud" section="Paramètres de base" />
      <Card>
        <Tabs destroyInactiveTabPane items={[
          { key: 'tank', label: 'Cuves', children: <Tanks /> },
          { key: 'noz', label: 'Pistolets', children: <Nozzles /> },
        ]} />
      </Card>
    </>
  )
}
