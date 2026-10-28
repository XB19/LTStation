import { useCallback, useEffect, useState } from 'react'
import {
  Button, Card, Col, DatePicker, Empty, Form, Input, InputNumber, Modal, Progress, Radio, Row, Select, Space, Table, Tabs, Tag,
  Tree, message,
} from 'antd'
import { DeleteOutlined, EditOutlined, MinusCircleOutlined, PlusOutlined, ReloadOutlined, UserAddOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { del, get, post, put, type PageResult } from '../../api/http'
import PageHeader from '../../components/PageHeader'
import CrudTable from '../../components/CrudTable'
import RemoteSelect from '../../components/RemoteSelect'
import SearchTable, { serializeFilters } from '../../components/SearchTable'
import { confirmDelete } from '../../components/confirmDelete'
import { fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>
const SECTION = 'Communication sans fil'

// ------------------------------------------------------------------ Paramètres de la plateforme

interface TreeNode {
  id: number
  name: string
  children: TreeNode[]
}
interface AntTreeNode {
  key: number
  title: string
  children: AntTreeNode[]
}
const toTreeData = (nodes: TreeNode[]): AntTreeNode[] => nodes.map((n) => ({ key: n.id, title: n.name, children: toTreeData(n.children) }))

/** Comptes et gestionnaires de la plateforme (Main/TCPPlatform d'origine). */
export function TcpPlatform() {
  const [tree, setTree] = useState<TreeNode[]>([])
  const [selected, setSelected] = useState<{ id: number; name: string } | null>(null)
  const [modal, setModal] = useState<'child' | 'manager' | null>(null)
  const [form] = Form.useForm()
  const loadTree = useCallback(() => get<TreeNode[]>('/company-groups/tree').then(setTree), [])
  useEffect(() => {
    loadTree()
  }, [loadTree])
  const open = (m: 'child' | 'manager') => {
    form.resetFields()
    setModal(m)
  }
  return (
    <>
      <PageHeader title="Paramètres de la plateforme" section={SECTION} />
      <Row gutter={16}>
        <Col xs={24} md={10}>
          <Card title="Comptes">
            {tree.length ? (
              <Tree defaultExpandAll treeData={toTreeData(tree)}
                onSelect={(_, e) => setSelected(e.selected ? { id: Number(e.node.key), name: String(e.node.title) } : null)} />
            ) : <Empty />}
          </Card>
        </Col>
        <Col xs={24} md={14}>
          <Card title={selected ? `Compte : ${selected.name}` : 'Choisissez un compte'}>
            <Space>
              <Button type="primary" icon={<PlusOutlined />} disabled={!selected} onClick={() => open('child')}>Ajouter un sous-compte</Button>
              <Button icon={<UserAddOutlined />} disabled={!selected} onClick={() => open('manager')}>Ajouter un gestionnaire</Button>
            </Space>
          </Card>
        </Col>
      </Row>
      <Modal title={modal === 'child' ? 'Nouveau sous-compte' : 'Nouveau gestionnaire'} open={!!modal} onCancel={() => setModal(null)}
        okText="Enregistrer" cancelText="Annuler"
        onOk={async () => {
          const v = await form.validateFields()
          if (modal === 'child') await post(`/wireless/platforms/${selected!.id}/children`, v)
          else await post(`/wireless/platforms/${selected!.id}/managers`, v)
          message.success('Opération réussie')
          setModal(null)
          loadTree()
        }}>
        <Form form={form} layout="vertical">
          {modal === 'child' ? (
            <>
              <Form.Item name="name" label="Nom du compte" rules={[{ required: true }]}><Input /></Form.Item>
              <Form.Item name="limitStation" label="Nombre maximal de stations (0 = illimité)" initialValue={0}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </>
          ) : (
            <>
              <Form.Item name="userName" label="Nom d'utilisateur" rules={[{ required: true }]}><Input /></Form.Item>
              <Form.Item name="password" label="Mot de passe" rules={[{ required: true }]}><Input.Password /></Form.Item>
            </>
          )}
        </Form>
      </Modal>
    </>
  )
}

// ------------------------------------------------------------------ Paramètres de base

function StationsTab() {
  const load = useCallback(() => get<PageResult<Row>>('/wireless/stations', { page: 1, size: 1000 }).then((r) => r.rows), [])
  return (
    <CrudTable<Row>
      rowKey="stationId" load={load} addLabel="Ajouter une station"
      modalTitle={(m) => (m === 'create' ? 'Nouvelle station' : 'Modifier la station')}
      columns={[
        { title: 'Code', dataIndex: 'code' },
        { title: 'Nom', dataIndex: 'name' },
        { title: 'N° de boîtier', dataIndex: 'boxCode' },
        { title: 'Adresse', dataIndex: 'address' },
        { title: 'Téléphone', dataIndex: 'phone' },
        { title: 'Responsable', dataIndex: 'manager' },
      ]}
      renderForm={() => (
        <>
          <Form.Item name="code" label="Code (vide = automatique)"><Input maxLength={10} /></Form.Item>
          <Form.Item name="name" label="Nom" rules={[{ required: true }]}><Input maxLength={20} /></Form.Item>
          <Form.Item name="boxCode" label="N° de boîtier" rules={[{ required: true }]}><Input maxLength={50} /></Form.Item>
          <Form.Item name="address" label="Adresse"><Input maxLength={50} /></Form.Item>
          <Form.Item name="phone" label="Téléphone"><Input maxLength={15} /></Form.Item>
          <Form.Item name="manager" label="Responsable"><Input maxLength={20} /></Form.Item>
        </>
      )}
      onCreate={(v) => post('/wireless/stations', v)}
      onUpdate={(r, v) => put(`/wireless/stations/${r.stationId}`, v)}
      onDelete={(r) => del(`/wireless/stations/${r.stationId}`)}
    />
  )
}

function EnergiesTab() {
  const load = useCallback(() => get<Row[]>('/wireless/energies'), [])
  return (
    <CrudTable<Row>
      rowKey="gradeSpecificId" load={load} addLabel="Ajouter une énergie"
      modalTitle={(m) => (m === 'create' ? 'Nouvelle énergie' : "Modifier l'énergie")}
      columns={[{ title: 'Code', dataIndex: 'code' }, { title: 'Nom', dataIndex: 'name' }]}
      renderForm={() => (
        <>
          <Form.Item name="code" label="Code" rules={[{ required: true }]}><Input maxLength={4} /></Form.Item>
          <Form.Item name="name" label="Nom" rules={[{ required: true }]}><Input maxLength={30} /></Form.Item>
        </>
      )}
      onCreate={(v) => post('/wireless/energies', v)}
      onUpdate={(r, v) => put(`/wireless/energies/${r.gradeSpecificId}`, v)}
      onDelete={(r) => del(`/wireless/energies/${r.gradeSpecificId}`)}
    />
  )
}

function PricesTab() {
  const load = useCallback(() => get<Row[]>('/wireless/prices'), [])
  return (
    <CrudTable<Row>
      rowKey="id" load={load} addLabel="Ajouter un prix"
      modalTitle={(m) => (m === 'create' ? 'Nouveau prix' : 'Modifier le prix')}
      columns={[
        { title: 'Station', dataIndex: 'stationName' },
        { title: 'Énergie', dataIndex: 'gradeName' },
        { title: 'Prix', dataIndex: 'price', render: (v) => fmtNum(v, 3) },
      ]}
      editValues={(r) => ({ stationId: r.stationId, gradeId: r.gradeSpecificId, price: r.price })}
      renderForm={() => (
        <>
          <Form.Item name="stationId" label="Station" rules={[{ required: true }]}><RemoteSelect type="TCPStation" /></Form.Item>
          <Form.Item name="gradeId" label="Énergie" rules={[{ required: true }]}><RemoteSelect type="TCPGrade" /></Form.Item>
          <Form.Item name="price" label="Prix" rules={[{ required: true }]} extra="Le prix est envoyé aux pistolets concernés.">
            <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
          </Form.Item>
        </>
      )}
      onCreate={(v) => post('/wireless/prices', v)}
      onUpdate={(r, v) => put(`/wireless/prices/${r.id}`, v)}
      onDelete={(r) => del(`/wireless/prices/${r.id}`)}
    />
  )
}

function TanksTab() {
  const load = useCallback(() => get<Row[]>('/wireless/tanks'), [])
  return (
    <CrudTable<Row>
      rowKey="id" load={load} addLabel="Ajouter une cuve"
      modalTitle={(m) => (m === 'create' ? 'Nouvelle cuve' : 'Modifier la cuve')}
      columns={[
        { title: 'Station', dataIndex: 'stationName' },
        { title: 'Cuve', dataIndex: 'tankName' },
        { title: 'Énergie', dataIndex: 'gradeName' },
        { title: 'Jaugeur', dataIndex: 'deviceNo' },
        { title: 'Hauteur (mm)', dataIndex: 'totalHeight', render: (v) => fmtNum(v) },
        { title: 'Volume (L)', dataIndex: 'totalVolume', render: (v) => fmtNum(v) },
        { title: 'Volume actuel', dataIndex: 'currentVol', render: (v) => fmtNum(v) },
      ]}
      editValues={(r) => ({ ...r, tankHeight: r.totalHeight, tankVolume: r.totalVolume })}
      renderForm={() => (
        <>
          <Form.Item name="stationId" label="Station" rules={[{ required: true }]}><RemoteSelect type="TCPStation" /></Form.Item>
          <Form.Item name="tankName" label="Nom de la cuve" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="gradeId" label="Énergie" rules={[{ required: true }]}><RemoteSelect type="TCPGrade" /></Form.Item>
          <Form.Item name="deviceNo" label="N° du jaugeur"><Input /></Form.Item>
          <Form.Item name="tankHeight" label="Hauteur totale (mm)"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="tankVolume" label="Volume total (L)"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
        </>
      )}
      onCreate={(v) => post('/wireless/tanks', v)}
      onUpdate={(r, v) => put(`/wireless/tanks/${r.id}`, v)}
      onDelete={(r) => del(`/wireless/tanks/${r.id}`)}
    />
  )
}

/** Stations, énergies, prix et cuves des comptes sans fil (Main/BoxConfigure d'origine). */
export function BoxConfigure() {
  return (
    <>
      <PageHeader title="Paramètres de base" section={SECTION} />
      <Card>
        <Tabs destroyInactiveTabPane items={[
          { key: 's', label: 'Stations', children: <StationsTab /> },
          { key: 'e', label: 'Énergies', children: <EnergiesTab /> },
          { key: 'p', label: 'Prix', children: <PricesTab /> },
          { key: 't', label: 'Cuves', children: <TanksTab /> },
        ]} />
      </Card>
    </>
  )
}

// ------------------------------------------------------------------ Liaison des appareils

const BAUDS = [4800, 9600, 14400, 19200, 38400, 56000, 115200].map((b, i) => ({ value: i + 1, label: String(b) }))
const INTERVALS = Array.from({ length: 26 }, (_, i) => ({ value: i, label: `${80 + i * 10} ms` }))
const PROTOCOLS = ['Aucun', 'Bluesky', 'Tatsuno', 'Japon', 'Dart', 'Jaugeur'].map((label, value) => ({ value, label }))
const PARITIES = ['Aucune', 'Paire', 'Impaire'].map((label, value) => ({ value, label }))
const DIGITS = [0, 1, 2, 3].map((value) => ({ value, label: String(value) }))

/** Boîtiers, ports série et pistolets associés (Main/TCPDeviceBind d'origine). */
export function TcpDeviceBind() {
  const [boxes, setBoxes] = useState<Row[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState<string | null>(null)
  const [form] = Form.useForm()
  const stationId = Form.useWatch('stationId', form)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setBoxes(await get<Row[]>('/wireless/boxes'))
    } finally {
      setLoading(false)
    }
  }, [])
  useEffect(() => {
    reload()
  }, [reload])

  const create = () => {
    form.resetFields()
    form.setFieldsValue({ port: 1, baud: 2, interval: 2, deviceType: 0, stop: 0, parity: 0, protocol: 1, nozzles: [] })
    setOpen('')
  }
  const edit = async (box: Row, port: number) => {
    const r = await get<Row>(`/wireless/boxes/${box.deviceNo}/ports/${port}`)
    form.resetFields()
    form.setFieldsValue({
      stationId: r.stationId, deviceNo: r.deviceNo, port, baud: r.buad, interval: r.interval, deviceType: r.deviceType,
      stop: r.stop, parity: r.parity, protocol: r.agreeMentType,
      nozzles: (r.nozzles as Row[]).map((n) => ({ ...n, tankId: n.tankNo != null ? Number(n.tankNo) : undefined })),
    })
    setOpen(String(box.deviceNo))
  }
  const loadPort = async (port: number) => {
    const deviceNo = form.getFieldValue('deviceNo')
    if (!open || !deviceNo) return
    try {
      await edit({ deviceNo }, port)
    } catch {
      form.setFieldsValue({ nozzles: [] })
    }
  }
  const save = async () => {
    const v = await form.validateFields()
    await post('/wireless/boxes', { ...v, oldDeviceNo: open || undefined })
    message.success('Configuration envoyée au boîtier')
    setOpen(null)
    reload()
  }

  return (
    <>
      <PageHeader title="Liaison des appareils" section={SECTION} />
      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={create}>Ajouter un boîtier</Button>
          <Button icon={<ReloadOutlined />} onClick={reload} />
        </Space>
        <Table<Row> rowKey="id" loading={loading} dataSource={boxes} columns={[
          { title: 'N° de boîtier', dataIndex: 'deviceNo' },
          { title: 'Station', dataIndex: 'stationName' },
          {
            title: 'Ports configurés', dataIndex: 'ports',
            render: (v, r) => String(v ?? '').split(',').filter(Boolean).map((p) => (
              <Tag key={p} color="blue" style={{ cursor: 'pointer' }} onClick={() => edit(r, Number(p))}>Port {p} <EditOutlined /></Tag>
            )),
          },
          {
            title: 'Opération', key: 'op',
            render: (_, r) => (
              <Button size="small" type="text" danger icon={<DeleteOutlined />}
                onClick={() => confirmDelete(async () => {
                  await del(`/wireless/boxes/${r.deviceNo}`)
                  reload()
                })} />
            ),
          },
        ]} />
      </Card>
      <Modal title={open ? `Boîtier ${open}` : 'Nouveau boîtier'} open={open !== null} width={980} onCancel={() => setOpen(null)}
        onOk={save} okText="Enregistrer et envoyer" cancelText="Annuler" destroyOnClose>
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={8}><Form.Item name="stationId" label="Station" rules={[{ required: true }]}><RemoteSelect type="TCPStation" /></Form.Item></Col>
            <Col span={8}><Form.Item name="deviceNo" label="N° de boîtier" rules={[{ required: true }]}><Input /></Form.Item></Col>
            <Col span={8}>
              <Form.Item name="port" label="Port" rules={[{ required: true }]}>
                <Radio.Group optionType="button" options={[1, 2, 3, 4].map((p) => ({ value: p, label: String(p) }))}
                  onChange={(e) => loadPort(e.target.value)} />
              </Form.Item>
            </Col>
            <Col span={6}><Form.Item name="baud" label="Débit (bauds)"><Select options={BAUDS} /></Form.Item></Col>
            <Col span={6}><Form.Item name="interval" label="Intervalle"><Select options={INTERVALS} /></Form.Item></Col>
            <Col span={4}><Form.Item name="stop" label="Bits d'arrêt"><Select options={[0, 1, 2].map((value) => ({ value, label: String(value) }))} /></Form.Item></Col>
            <Col span={4}><Form.Item name="parity" label="Parité"><Select options={PARITIES} /></Form.Item></Col>
            <Col span={4}><Form.Item name="deviceType" label="Type"><Select options={[{ value: 0, label: '664' }, { value: 1, label: '886' }]} /></Form.Item></Col>
            <Col span={8}><Form.Item name="protocol" label="Protocole"><Select options={PROTOCOLS} /></Form.Item></Col>
          </Row>
          <Form.List name="nozzles">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name }) => (
                  <Space key={key} align="baseline" wrap>
                    <Form.Item name={[name, 'nozId']} label="Pistolet" rules={[{ required: true }]}><InputNumber min={1} style={{ width: 80 }} /></Form.Item>
                    <Form.Item name={[name, 'address']} label="Adresse" rules={[{ required: true }]}><InputNumber min={0} style={{ width: 80 }} /></Form.Item>
                    <Form.Item name={[name, 'tankId']} label="Cuve" rules={[{ required: true }]}>
                      <RemoteSelect type="GetTCPTank" params={{ stationId }} enabled={!!stationId} style={{ width: 150 }} />
                    </Form.Item>
                    <Form.Item name={[name, 'amountDigits']} label="Déc. montant" initialValue={2}><Select options={DIGITS} style={{ width: 70 }} /></Form.Item>
                    <Form.Item name={[name, 'volumeDigits']} label="Déc. volume" initialValue={2}><Select options={DIGITS} style={{ width: 70 }} /></Form.Item>
                    <Form.Item name={[name, 'priceDigits']} label="Déc. prix" initialValue={2}><Select options={DIGITS} style={{ width: 70 }} /></Form.Item>
                    <Form.Item name={[name, 'authTypeId']} label="Autorisation" initialValue={0}>
                      <Select style={{ width: 130 }} options={[{ value: 0, label: 'Aucune' }, { value: 1, label: 'Par carte' }, { value: 2, label: 'À distance' }]} />
                    </Form.Item>
                    <MinusCircleOutlined onClick={() => remove(name)} />
                  </Space>
                ))}
                <Button type="dashed" block icon={<PlusOutlined />} disabled={!stationId} onClick={() => add({ nozId: fields.length + 1, address: fields.length + 1 })}>
                  Ajouter un pistolet
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
    </>
  )
}

// ------------------------------------------------------------------ Données en temps réel

const NOZ_STATUS: Record<number, { label: string; color: string }> = {
  [-1]: { label: 'Hors ligne', color: 'default' },
  0: { label: 'En ligne', color: 'green' },
  1: { label: 'Pistolet levé', color: 'gold' },
  2: { label: 'Autorisé', color: 'blue' },
  3: { label: 'En distribution', color: 'processing' },
}

/** Pistolets et cuves en temps réel, commandes à distance (Main/TCPDeviceShow d'origine). */
export function TcpDeviceShow() {
  const [stationId, setStationId] = useState<number>()
  const [data, setData] = useState<{ nozzles: Row[]; tanks: Row[] } | null>(null)
  const [preset, setPreset] = useState<Row | null>(null)
  const [form] = Form.useForm()

  const load = useCallback(async () => {
    if (stationId == null) return setData(null)
    setData(await get(`/wireless/live/${stationId}`))
  }, [stationId])
  useEffect(() => {
    load()
    if (stationId == null) return
    const t = setInterval(load, 5000)
    return () => clearInterval(t)
  }, [load, stationId])

  const startStop = async (n: Row, stop: boolean) => {
    await post('/wireless/commands', { type: 'StartOrStop', deviceNo: n.deviceNo, nozId: n.nozId, startStop: stop ? 1 : 0 })
    message.success(stop ? "Commande d'arrêt envoyée" : 'Commande de démarrage envoyée')
  }

  return (
    <>
      <PageHeader title="Données en temps réel" section={SECTION} />
      <Card style={{ marginBottom: 16 }}>
        <Space>
          <RemoteSelect type="TCPStation" placeholder="Choisir une station" value={stationId} onChange={setStationId} style={{ width: 300 }} />
          <Button icon={<ReloadOutlined />} onClick={load} disabled={stationId == null} />
        </Space>
      </Card>
      {!data ? <Card><Empty description="Choisissez une station" /></Card> : (
        <>
          <Card title="Pistolets" style={{ marginBottom: 16 }}>
            {data.nozzles.length === 0 ? <Empty /> : (
              <Row gutter={[16, 16]}>
                {data.nozzles.map((n) => {
                  const s = NOZ_STATUS[Number(n.statue ?? -1)] ?? NOZ_STATUS[-1]
                  const online = Number(n.statue ?? -1) >= 0
                  return (
                    <Col key={`${n.deviceNo}-${n.nozId}`} xs={24} sm={12} lg={8} xl={6}>
                      <Card size="small" title={`Pistolet ${n.nozId} — ${n.gradeName ?? ''}`} extra={<Tag color={s.color}>{s.label}</Tag>}
                        actions={online && n.deviceNo ? [
                          <a key="s" onClick={() => startStop(n, false)}>Démarrer</a>,
                          <a key="t" onClick={() => startStop(n, true)}>Arrêter</a>,
                          <a key="p" onClick={() => {
                            form.setFieldsValue({ preSetType: 0, preSetValue: undefined, price: n.newPpu })
                            setPreset(n)
                          }}>Prédéterminer</a>,
                        ] : undefined}>
                        <div>Montant : <b>{fmtNum(n.amount)}</b></div>
                        <div>Volume : <b>{fmtNum(n.volume)}</b> L</div>
                        <div>Prix : {fmtNum(n.newPpu ?? n.price, 3)}</div>
                        <div>Totalisateur : {fmtNum(n.volumeTotal)} L / {fmtNum(n.amountTotal)}</div>
                        <div style={{ color: 'var(--lt-color-text-tertiary)', fontSize: 12 }}>{n.deviceNo ? `Boîtier ${n.deviceNo} · ` : ''}{fmtDateTime(n.updateTime)}</div>
                      </Card>
                    </Col>
                  )
                })}
              </Row>
            )}
          </Card>
          <Card title="Cuves">
            {data.tanks.length === 0 ? <Empty /> : (
              <Row gutter={[16, 16]}>
                {data.tanks.map((t) => (
                  <Col key={String(t.id)} xs={24} sm={12} lg={8}>
                    <Card size="small" title={`${t.tankName ?? ''} (${String(t.gradeId ?? '')})`}>
                      <Space align="center" size="large">
                        <Progress type="circle" size={90} percent={Number(t.oilVolumeRatio ?? 0)} />
                        <div>
                          <div>Volume : {fmtNum(t.currentVolume)} / {fmtNum(t.totalVolume)} L</div>
                          <div>Hauteur : {fmtNum(t.currentHeight)} / {fmtNum(t.totalHeight)} mm</div>
                          <div>Eau : {fmtNum(t.currentWaterHeight)} mm ({fmtNum(t.waterHeightRatio)} %)</div>
                          <div>Température : {fmtNum(t.currentTemp)} °C</div>
                          <div style={{ color: 'var(--lt-color-text-tertiary)', fontSize: 12 }}>{fmtDateTime(t.updateTime)}</div>
                        </div>
                      </Space>
                    </Card>
                  </Col>
                ))}
              </Row>
            )}
          </Card>
        </>
      )}
      <Modal title={`Prédétermination — pistolet ${preset?.nozId ?? ''}`} open={!!preset} onCancel={() => setPreset(null)}
        okText="Envoyer" cancelText="Annuler"
        onOk={async () => {
          const v = await form.validateFields()
          await post('/wireless/commands', { ...v, type: 'PreSet', deviceNo: preset!.deviceNo, nozId: preset!.nozId })
          message.success('Prédétermination envoyée')
          setPreset(null)
        }}>
        <Form form={form} layout="vertical">
          <Form.Item name="preSetType" label="Type">
            <Radio.Group options={[{ value: 0, label: 'Montant' }, { value: 1, label: 'Volume' }]} />
          </Form.Item>
          <Form.Item name="preSetValue" label="Valeur" rules={[{ required: true }]}><InputNumber min={0.01} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="price" label="Prix" rules={[{ required: true }]}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
        </Form>
      </Modal>
    </>
  )
}

// ------------------------------------------------------------------ Transactions

/** Transactions des stations sans fil (Main/NormalTransaction d'origine). */
export function NormalTransaction() {
  return (
    <>
      <PageHeader title="Transactions" section={SECTION} />
      <Card>
        <SearchTable<Row>
          url="/wireless/transactions" rowKey="id"
          initialFilters={{ period: [dayjs().startOf('day'), dayjs().endOf('day')] }}
          toParams={(v) => {
            const { period, ...rest } = v as { period?: [dayjs.Dayjs, dayjs.Dayjs] }
            return serializeFilters({ ...rest, start: period?.[0], end: period?.[1] })
          }}
          filters={
            <>
              <Form.Item name="gradeId" label="Énergie"><RemoteSelect type="TCPGrade" style={{ width: 160 }} /></Form.Item>
              <Form.Item name="period" label="Période" rules={[{ required: true }]}><DatePicker.RangePicker showTime /></Form.Item>
            </>
          }
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: 'Pistolet', dataIndex: 'gunId' },
            { title: 'Énergie', dataIndex: 'oilName' },
            { title: 'Montant', dataIndex: 'amount', render: (v) => fmtNum(v) },
            { title: 'Volume (L)', dataIndex: 'volume', render: (v) => fmtNum(v) },
            { title: 'Totalisateur', dataIndex: 'volumeTotal', render: (v) => fmtNum(v) },
            { title: 'Date', dataIndex: 'createTime', render: fmtDateTime },
          ]}
        />
      </Card>
    </>
  )
}
