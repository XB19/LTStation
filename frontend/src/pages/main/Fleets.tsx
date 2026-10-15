import { useEffect, useState } from 'react'
import {
  Alert, Button, Card, Col, DatePicker, Descriptions, Form, Input, InputNumber, Modal, Radio, Row, Select, Space, Table, Tag,
  Tooltip, message,
} from 'antd'
import { DeleteOutlined, DollarOutlined, EditOutlined, PlusOutlined, SwapOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { del, get, post, put } from '../../api/http'
import { confirmDelete } from '../../components/confirmDelete'
import PageHeader from '../../components/PageHeader'
import SearchTable, { serializeFilters } from '../../components/SearchTable'
import { fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>

export const ACCOUNT_TYPES = [{ value: 1, label: 'Montant' }, { value: 2, label: 'Litres' }]
const STATES = [{ value: 0, label: 'Suspendu' }, { value: 1, label: 'Normal' }]
const OPS = [{ value: 1, label: 'Recharge' }, { value: 2, label: 'Remboursement' }]
const label = (opts: { value: number; label: string }[], v: unknown) => opts.find((o) => o.value === Number(v))?.label ?? ''

const periodParams = (v: Record<string, unknown>) => {
  const { period, ...rest } = v as { period?: [dayjs.Dayjs, dayjs.Dayjs] }
  return serializeFilters({ ...rest, start: period?.[0], end: period?.[1] })
}

interface FleetInfo {
  teamId?: number
  teamName?: string
  masterBalance?: number
  accountType?: number
}

function useFleetInfo(): [FleetInfo | null, () => void] {
  const [info, setInfo] = useState<FleetInfo | null>(null)
  const refresh = () => {
    get<FleetInfo>('/fleets/me').then(setInfo)
  }
  useEffect(refresh, [])
  return [info, refresh]
}

// ------------------------------------------------------------------ Liste des flottes

function FleetForm({ teamId }: { teamId?: number }) {
  const [cards, setCards] = useState<Row[]>([])
  const [employees, setEmployees] = useState<Row[]>([])
  const form = Form.useFormInstance()
  useEffect(() => {
    get<Row[]>('/fleets/cards', { teamId }).then(setCards)
    get<Row[]>('/employees').then(setEmployees)
  }, [teamId])
  return (
    <Row gutter={16}>
      <Col span={12}><Form.Item name="teamName" label="Nom de la flotte" rules={[{ required: true }]}><Input /></Form.Item></Col>
      <Col span={12}>
        <Form.Item name="contactPersonId" label="Contact de la flotte (employé)" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label"
            options={employees.map((e) => ({ value: e.employeeId as number, label: String(e.name) }))}
            onChange={(v) => {
              const e = employees.find((x) => x.employeeId === v)
              form.setFieldsValue({ contactPerson: e?.name, phoneNumber: e?.telephone })
            }} />
        </Form.Item>
      </Col>
      <Form.Item name="contactPerson" hidden><Input /></Form.Item>
      <Col span={12}><Form.Item name="phoneNumber" label="Téléphone"><Input /></Form.Item></Col>
      <Col span={12}>
        <Form.Item name="accountType" label="Type de compte" rules={[{ required: true }]}>
          <Select options={ACCOUNT_TYPES} disabled={teamId != null} />
        </Form.Item>
      </Col>
      <Col span={12}><Form.Item name="accountState" label="État du compte" initialValue={1}><Select options={STATES} /></Form.Item></Col>
      <Col span={12}><Form.Item name="address" label="Adresse"><Input /></Form.Item></Col>
      <Col span={24}>
        <Form.Item name="mCardIds" label="Cartes de la flotte">
          <Select mode="multiple" options={cards.map((c) => ({ value: String(c.cardNo), label: String(c.cardNo) }))} />
        </Form.Item>
      </Col>
      <Col span={24}><Form.Item name="remark" label="Remarque"><Input.TextArea rows={2} /></Form.Item></Col>
    </Row>
  )
}

export function FleetList() {
  const [reloadKey, setReloadKey] = useState(0)
  const [edit, setEdit] = useState<Row | 'new' | null>(null)
  const [recharge, setRecharge] = useState<Row | null>(null)
  const [form] = Form.useForm()
  const [rForm] = Form.useForm()
  const reload = () => setReloadKey((k) => k + 1)

  const openEdit = (r: Row | 'new') => {
    form.resetFields()
    if (r !== 'new') {
      form.setFieldsValue({ ...r, contactPersonId: r.contactPersonId, mCardIds: String(r.mCardIDs ?? '').split(',').filter(Boolean) })
    }
    setEdit(r)
  }
  const save = async () => {
    const v = await form.validateFields()
    if (edit === 'new') await post('/fleets', v)
    else await put(`/fleets/${(edit as Row).id}`, v)
    message.success('Opération réussie')
    setEdit(null)
    reload()
  }
  const doRecharge = async () => {
    const v = await rForm.validateFields()
    await post(`/fleets/${recharge!.id}/recharge`, { ...v, expectedBalance: recharge!.masterBalance })
    message.success('Opération réussie')
    setRecharge(null)
    reload()
  }

  return (
    <>
      <PageHeader title="Liste des flottes" section="Flottes" />
      <Card>
        <SearchTable<Row>
          url="/fleets" rowKey="id" reloadKey={reloadKey} toParams={periodParams}
          extraButtons={<Button icon={<PlusOutlined />} onClick={() => openEdit('new')}>Ajouter une flotte</Button>}
          filters={
            <>
              <Form.Item name="teamName" label="Flotte"><Input allowClear /></Form.Item>
              <Form.Item name="contactPerson" label="Contact"><Input allowClear /></Form.Item>
              <Form.Item name="accountType" label="Type de compte"><Select allowClear style={{ width: 120 }} options={ACCOUNT_TYPES} /></Form.Item>
              <Form.Item name="period" label="Ouverture"><DatePicker.RangePicker showTime /></Form.Item>
            </>
          }
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: 'Flotte', dataIndex: 'teamName' },
            { title: 'Type de compte', dataIndex: 'accountType', render: (v) => label(ACCOUNT_TYPES, v) },
            { title: 'Solde principal', dataIndex: 'masterBalance', render: (v) => fmtNum(v) },
            { title: 'État', dataIndex: 'accountState', render: (v) => <Tag color={Number(v) === 1 ? 'green' : 'red'}>{label(STATES, v)}</Tag> },
            { title: 'Contact', dataIndex: 'contactPerson' },
            { title: 'Téléphone', dataIndex: 'phoneNumber' },
            { title: 'Adresse', dataIndex: 'address' },
            { title: 'Ouverture', dataIndex: 'createTime', render: fmtDateTime },
            {
              title: 'Opération', key: 'op',
              render: (_, r) => (
                <Space>
                  <Tooltip title="Modifier"><Button size="small" type="text" icon={<EditOutlined />} onClick={() => openEdit(r)} /></Tooltip>
                  <Tooltip title="Recharge / remboursement">
                    <Button size="small" type="text" icon={<DollarOutlined />} onClick={() => {
                      rForm.setFieldsValue({ type: 1, amount: undefined })
                      setRecharge(r)
                    }} />
                  </Tooltip>
                  <Tooltip title="Supprimer">
                    <Button size="small" type="text" danger icon={<DeleteOutlined />}
                      onClick={() => confirmDelete(() => del(`/fleets/${r.id}`), reload)} />
                  </Tooltip>
                </Space>
              ),
            },
          ]}
        />
      </Card>
      <Modal title={edit === 'new' ? 'Ajouter une flotte' : 'Modifier la flotte'} open={edit !== null} onOk={save}
        onCancel={() => setEdit(null)} okText="Enregistrer" cancelText="Annuler" width={760} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false}>
          <FleetForm teamId={edit && edit !== 'new' ? Number((edit as Row).id) : undefined} />
        </Form>
      </Modal>
      <Modal title={`Compte principal — ${recharge?.teamName ?? ''}`} open={!!recharge} onOk={doRecharge}
        onCancel={() => setRecharge(null)} okText="Valider" cancelText="Annuler">
        <Descriptions size="small" column={1} items={[{ key: 'b', label: 'Solde actuel', children: fmtNum(recharge?.masterBalance) }]} />
        <Form form={rForm} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item name="type" label="Opération"><Radio.Group options={OPS} /></Form.Item>
          <Form.Item name="amount" label="Valeur" rules={[{ required: true }]}><InputNumber min={0.01} style={{ width: '100%' }} /></Form.Item>
        </Form>
      </Modal>
    </>
  )
}

// ------------------------------------------------------------------ Chauffeurs

function DistributeModal({ open, info, onClose }: { open: boolean; info: FleetInfo | null; onClose: (done: boolean) => void }) {
  const [type, setType] = useState(1)
  const [drivers, setDrivers] = useState<Row[]>([])
  const [amounts, setAmounts] = useState<Record<number, number | null>>({})
  useEffect(() => {
    if (!open || !info?.teamId) return
    setAmounts({})
    get<{ rows: Row[] }>('/fleet-drivers', { teamId: info.teamId, cardState: 1, size: 1000 }).then((r) => setDrivers(r.rows))
  }, [open, info])
  const total = Object.values(amounts).reduce<number>((a, v) => a + (v ?? 0), 0)
  const submit = async () => {
    const items = Object.entries(amounts).filter(([, v]) => v && v > 0).map(([id, amount]) => ({ id: Number(id), amount }))
    if (!items.length) {
      message.error('Saisissez au moins un montant')
      return
    }
    await post('/fleet-drivers/batch', { type, teamId: info!.teamId, items })
    message.success('Opération réussie')
    onClose(true)
  }
  return (
    <Modal title="Répartition vers les chauffeurs" open={open} onOk={submit} onCancel={() => onClose(false)} width={800}
      okText="Valider" cancelText="Annuler">
      <Space style={{ marginBottom: 12 }} wrap>
        <span>Flotte : <b>{info?.teamName}</b></span>
        <span>Solde principal : <b>{fmtNum(info?.masterBalance)}</b></span>
        <Radio.Group value={type} onChange={(e) => setType(e.target.value)} options={OPS} />
        <span>Total : <b>{fmtNum(total)}</b></span>
      </Space>
      <Table size="small" rowKey="id" dataSource={drivers} pagination={false} scroll={{ y: 400 }} columns={[
        { title: 'Chauffeur', dataIndex: 'driverName' },
        { title: 'Carte', dataIndex: 'mCardId' },
        { title: 'Solde', dataIndex: 'balance', render: (v) => fmtNum(v) },
        {
          title: 'Montant', key: 'a',
          render: (_, r) => (
            <InputNumber min={0} value={amounts[r.id as number] ?? null}
              onChange={(v) => setAmounts({ ...amounts, [r.id as number]: v })} />
          ),
        },
      ]} />
    </Modal>
  )
}

export function FleetDrivers() {
  const [info, refreshInfo] = useFleetInfo()
  const [reloadKey, setReloadKey] = useState(0)
  const [edit, setEdit] = useState<Row | 'new' | null>(null)
  const [distribute, setDistribute] = useState(false)
  const [cards, setCards] = useState<Row[]>([])
  const [form] = Form.useForm()
  const reload = () => setReloadKey((k) => k + 1)

  const openEdit = async (r: Row | 'new') => {
    form.resetFields()
    if (info?.teamId) setCards(await get<Row[]>('/fleets/cards', { teamId: info.teamId, assignedOnly: true }))
    if (r !== 'new') form.setFieldsValue({ ...r, password: '' })
    setEdit(r)
  }
  const save = async () => {
    const v = await form.validateFields()
    if (edit === 'new') await post('/fleet-drivers', { ...v, teamId: info?.teamId })
    else await put(`/fleet-drivers/${(edit as Row).id}`, v)
    message.success('Opération réussie')
    setEdit(null)
    reload()
  }

  if (!info) return null
  return (
    <>
      <PageHeader title="Chauffeurs des flottes" section="Flottes" />
      {!info?.teamId && <Alert showIcon type="info" style={{ marginBottom: 16 }}
        message="Vous n'êtes le contact d'aucune flotte : la création de chauffeurs et la répartition sont réservées au contact de la flotte." />}
      <Card>
        <SearchTable<Row>
          url="/fleet-drivers" rowKey="id" reloadKey={reloadKey} toParams={periodParams}
          initialFilters={info.teamId ? { teamId: info.teamId } : {}}
          extraButtons={info.teamId && (
            <>
              <Button icon={<PlusOutlined />} onClick={() => openEdit('new')}>Ajouter un chauffeur</Button>
              <Button icon={<SwapOutlined />} onClick={() => setDistribute(true)}>Répartition</Button>
            </>
          )}
          filters={
            <>
              <Form.Item name="teamName" label="Flotte"><Input allowClear /></Form.Item>
              <Form.Item name="driverName" label="Chauffeur"><Input allowClear /></Form.Item>
              <Form.Item name="cardState" label="État de la carte"><Select allowClear style={{ width: 120 }} options={STATES} /></Form.Item>
              <Form.Item name="period" label="Création"><DatePicker.RangePicker showTime /></Form.Item>
            </>
          }
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: 'Flotte', dataIndex: 'teamName' },
            { title: 'Type de compte', dataIndex: 'accountType', render: (v) => label(ACCOUNT_TYPES, v) },
            { title: 'Chauffeur', dataIndex: 'driverName' },
            { title: 'Carte', dataIndex: 'mCardId' },
            { title: 'Solde', dataIndex: 'balance', render: (v) => fmtNum(v) },
            { title: 'État', dataIndex: 'cardState', render: (v) => <Tag color={Number(v) === 1 ? 'green' : 'red'}>{label(STATES, v)}</Tag> },
            { title: 'Téléphone', dataIndex: 'phoneNumber' },
            { title: 'Immatriculation', dataIndex: 'numberPlate' },
            { title: 'Création', dataIndex: 'createTime', render: fmtDateTime },
            {
              title: 'Opération', key: 'op',
              render: (_, r) => (
                <Space>
                  <Tooltip title="Modifier"><Button size="small" type="text" icon={<EditOutlined />} onClick={() => openEdit(r)} /></Tooltip>
                  <Tooltip title="Supprimer">
                    <Button size="small" type="text" danger icon={<DeleteOutlined />}
                      onClick={() => confirmDelete(() => del(`/fleet-drivers/${r.id}`), reload)} />
                  </Tooltip>
                </Space>
              ),
            },
          ]}
        />
      </Card>
      <Modal title={edit === 'new' ? 'Ajouter un chauffeur' : 'Modifier le chauffeur'} open={edit !== null} onOk={save}
        onCancel={() => setEdit(null)} okText="Enregistrer" cancelText="Annuler" width={700}>
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}><Form.Item name="driverName" label="Nom du chauffeur" rules={[{ required: true }]}><Input /></Form.Item></Col>
            <Col span={12}>
              <Form.Item name="mCardId" label="Carte" rules={[{ required: true }]}>
                <Select disabled={edit !== 'new'} options={cards.map((c) => ({ value: String(c.cardNo), label: String(c.cardNo) }))} />
              </Form.Item>
            </Col>
            <Col span={12}><Form.Item name="phoneNumber" label="Téléphone"><Input /></Form.Item></Col>
            <Col span={12}><Form.Item name="numberPlate" label="Immatriculation"><Input /></Form.Item></Col>
            <Col span={12}><Form.Item name="password" label="Mot de passe de la carte"><Input.Password autoComplete="new-password" /></Form.Item></Col>
            <Col span={12}><Form.Item name="cardState" label="État de la carte" initialValue={1}><Select options={STATES} /></Form.Item></Col>
            <Col span={24}><Form.Item name="remark" label="Remarque"><Input.TextArea rows={2} /></Form.Item></Col>
          </Row>
        </Form>
      </Modal>
      <DistributeModal open={distribute} info={info} onClose={(done) => {
        setDistribute(false)
        if (done) {
          reload()
          refreshInfo()
        }
      }} />
    </>
  )
}

// ------------------------------------------------------------------ Historiques

function RecordPage({ kind, title }: { kind: 'recharge' | 'distribute' | 'card'; title: string }) {
  const [info] = useFleetInfo()
  if (!info) return null
  const common = [{ title: 'Station', dataIndex: 'stationName' }, { title: 'Flotte', dataIndex: 'teamName' }]
  const columns = {
    recharge: [
      ...common,
      { title: 'Type de compte', dataIndex: 'accountType', render: (v: unknown) => label(ACCOUNT_TYPES, v) },
      { title: 'Date', dataIndex: 'rechargeTime', render: fmtDateTime },
      { title: 'Opération', dataIndex: 'rechargeType', render: (v: unknown) => label(OPS, v) },
      { title: 'Avant', dataIndex: 'beforeBalance', render: (v: unknown) => fmtNum(v) },
      { title: 'Valeur', dataIndex: 'rechargeAmount', render: (v: unknown) => fmtNum(v) },
      { title: 'Après', dataIndex: 'afterBalance', render: (v: unknown) => fmtNum(v) },
      { title: 'Opérateur', dataIndex: 'loginName' },
    ],
    distribute: [
      ...common,
      { title: 'Chauffeur', dataIndex: 'driverName' },
      { title: 'Date', dataIndex: 'distributeTime', render: fmtDateTime },
      { title: 'Opération', dataIndex: 'rechargeType', render: (v: unknown) => label(OPS, v) },
      { title: 'Valeur', dataIndex: 'changeAmount', render: (v: unknown) => fmtNum(v) },
      { title: 'Principal avant', dataIndex: 'masterBeforeBalance', render: (v: unknown) => fmtNum(v) },
      { title: 'Principal après', dataIndex: 'masterAfterBalance', render: (v: unknown) => fmtNum(v) },
      { title: 'Chauffeur avant', dataIndex: 'driverBeforeBalance', render: (v: unknown) => fmtNum(v) },
      { title: 'Chauffeur après', dataIndex: 'driverAfterBalance', render: (v: unknown) => fmtNum(v) },
      { title: 'Opérateur', dataIndex: 'loginName' },
    ],
    card: [
      ...common,
      { title: 'Station de consommation', dataIndex: 'consumeStationName' },
      { title: 'Chauffeur', dataIndex: 'driverName' },
      { title: 'Carte', dataIndex: 'mCardId' },
      { title: 'Immatriculation', dataIndex: 'numberPlate' },
      { title: 'Pistolet', dataIndex: 'gunId' },
      { title: 'Énergie', dataIndex: 'energyName' },
      { title: 'Litres', dataIndex: 'liters', render: (v: unknown) => fmtNum(v) },
      { title: 'Valeur', dataIndex: 'changeAmount', render: (v: unknown) => fmtNum(v) },
      { title: 'Avant', dataIndex: 'beforeBalance', render: (v: unknown) => fmtNum(v) },
      { title: 'Après', dataIndex: 'afterBalance', render: (v: unknown) => fmtNum(v) },
      { title: 'Total', dataIndex: 'totalLiters', render: (v: unknown) => fmtNum(v) },
      { title: 'Date', dataIndex: 'changeTime', render: fmtDateTime },
    ],
  }[kind]
  return (
    <>
      <PageHeader title={title} section="Flottes" />
      <Card>
        <SearchTable<Row>
          url={`/fleet-records/${kind}`} exportUrl={`/fleet-records/${kind}/export`} rowKey="id" toParams={periodParams}
          initialFilters={info.teamId ? { teamId: info.teamId } : {}}
          filters={
            <>
              {kind === 'recharge' && <Form.Item name="teamName" label="Flotte"><Input allowClear /></Form.Item>}
              {kind !== 'recharge' && <Form.Item name="driverName" label="Chauffeur"><Input allowClear /></Form.Item>}
              {kind === 'card' && <Form.Item name="mCardId" label="Carte"><Input allowClear /></Form.Item>}
              {kind !== 'card' && <Form.Item name="rechargeType" label="Opération"><Select allowClear style={{ width: 150 }} options={OPS} /></Form.Item>}
              <Form.Item name="period" label="Période"><DatePicker.RangePicker showTime /></Form.Item>
            </>
          }
          columns={columns}
        />
      </Card>
    </>
  )
}

export const FleetRechargeRecords = () => <RecordPage kind="recharge" title="Factures des flottes" />
export const FleetDistributeRecords = () => <RecordPage kind="distribute" title="Répartitions des flottes" />
export const FleetCardRecords = () => <RecordPage kind="card" title="Consommations des cartes flotte" />
