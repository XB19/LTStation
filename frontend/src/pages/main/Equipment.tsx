import { useEffect, useState } from 'react'
import { AutoComplete, Button, Card, Col, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Modal, Row, Select, Space, Switch, Table, Tabs, Tag, Tooltip, Typography, message } from 'antd'
import { DeleteOutlined, EditOutlined, LinkOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { del, get, post, put } from '../../api/http'
import CardWorkOrderModal, { EquipmentSelect } from '../../components/CardWorkOrderModal'
import { confirmDelete } from '../../components/confirmDelete'
import ExcelImportModal from '../../components/ExcelImportModal'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import SearchTable, { serializeFilters } from '../../components/SearchTable'
import { fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>

const EQUIPMENT_STATUS = [
  { value: 1, label: 'En service', color: 'green' },
  { value: 2, label: 'En maintenance', color: 'orange' },
  { value: 3, label: 'Hors service', color: 'red' },
]

const statusTag = (v: unknown) => {
  const s = EQUIPMENT_STATUS.find((x) => x.value === v)
  return s ? <Tag color={s.color}>{s.label}</Tag> : null
}

function useCategories() {
  const [cats, setCats] = useState<string[]>([])
  useEffect(() => {
    get<string[]>('/equipment/categories').then(setCats)
  }, [])
  return cats
}

/** Formulaire d'un équipement : identification, ordre de travail, caractéristiques, rattachement. */
function EquipmentForm({ categories }: { categories: string[] }) {
  return (
    <Row gutter={16}>
      <Col span={14}>
        <Form.Item name="name" label="Désignation" rules={[{ required: true, message: 'La désignation est obligatoire' }, { max: 100 }]}>
          <Input placeholder="ex. Reach stacker RS-04" />
        </Form.Item>
      </Col>
      <Col span={10}>
        <Form.Item name="workOrderNo" label="N° d'ordre de travail" rules={[{ max: 64 }]}
          extra="Reporté sur les cartes rattachées s'il change.">
          <Input placeholder="ex. 4500012873345" />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item name="category" label="Catégorie" rules={[{ max: 50 }]}>
          <AutoComplete options={categories.map((c) => ({ value: c }))} filterOption placeholder="Choisir ou saisir" />
        </Form.Item>
      </Col>
      <Col span={8}><Form.Item name="brand" label="Marque" rules={[{ max: 50 }]}><Input /></Form.Item></Col>
      <Col span={8}><Form.Item name="model" label="Modèle" rules={[{ max: 50 }]}><Input /></Form.Item></Col>
      <Col span={8}><Form.Item name="serialNo" label="N° de série / châssis" rules={[{ max: 64 }]}><Input /></Form.Item></Col>
      <Col span={8}><Form.Item name="fleetNo" label="N° de parc / immatriculation" rules={[{ max: 30 }]}><Input /></Form.Item></Col>
      <Col span={8}>
        <Form.Item name="status" label="Statut">
          <Select options={EQUIPMENT_STATUS.map(({ value, label }) => ({ value, label }))} />
        </Form.Item>
      </Col>
      <Col span={8}><Form.Item name="gradeCode" label="Énergie"><RemoteSelect type="OilType" allowClear style={{ width: '100%' }} /></Form.Item></Col>
      <Col span={8}>
        <Form.Item name="tankCapacity" label="Capacité du réservoir (L)">
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
      </Col>
      <Col span={8}><Form.Item name="stationId" label="Station de rattachement"><RemoteSelect type="Station" allowClear style={{ width: '100%' }} /></Form.Item></Col>
      <Col span={24}><Form.Item name="memo" label="Observations"><Input.TextArea rows={2} maxLength={1000} /></Form.Item></Col>
    </Row>
  )
}

/** Fiche d'un équipement : caractéristiques et cartes rattachées. */
function EquipmentDrawer({ id, onClose, onChanged }: { id?: number; onClose: () => void; onChanged: () => void }) {
  const [e, setE] = useState<Row | null>(null)
  const [link, setLink] = useState<{ asn?: string } | null>(null)
  const reload = () => id && get<Row>(`/equipment/${id}`).then(setE)
  useEffect(() => {
    setE(null)
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])
  return (
    <Drawer title={e ? (e.name as string) : 'Équipement'} open={!!id} onClose={onClose} width={720}
      extra={<Button type="primary" icon={<LinkOutlined />} onClick={() => setLink({})}>Rattacher une carte</Button>}>
      {e && (
        <>
          <Descriptions size="small" bordered column={2} items={[
            { label: 'Ordre de travail', children: <Typography.Text strong copyable={!!e.workOrderNo}>{(e.workOrderNo as string) ?? '—'}</Typography.Text> },
            { label: 'Statut', children: statusTag(e.status) },
            { label: 'Catégorie', children: e.category as string },
            { label: 'Marque / modèle', children: [e.brand, e.model].filter(Boolean).join(' ') },
            { label: 'N° de série', children: e.serialNo as string },
            { label: 'N° de parc', children: e.fleetNo as string },
            { label: 'Énergie', children: e.gradeName as string },
            { label: 'Réservoir', children: e.tankCapacity != null ? `${fmtNum(e.tankCapacity, 0)} L` : '' },
            { label: 'Station', children: e.stationName as string },
            { label: 'Créé', children: `${fmtDateTime(e.createTime)} · ${e.createBy ?? ''}` },
            { label: 'Observations', children: e.memo as string, span: 2 },
          ]} />
          <Typography.Title level={5} style={{ marginTop: 20 }}>Cartes rattachées ({(e.cards as Row[]).length})</Typography.Title>
          <Table size="small" rowKey="asn" dataSource={e.cards as Row[]} pagination={false} locale={{ emptyText: 'Aucune carte rattachée' }}
            columns={[
              { title: 'N° de carte', dataIndex: 'asn' },
              { title: 'Type', dataIndex: 'kind' },
              { title: 'Porteur', dataIndex: 'holder' },
              { title: 'Ordre de travail', dataIndex: 'workOrderNo' },
              { title: 'Depuis', dataIndex: 'validFrom', render: fmtDateTime },
              { title: '', key: 'a', render: (_, r) => <Button size="small" type="link" onClick={() => setLink({ asn: r.asn as string })}>Modifier</Button> },
            ]} />
        </>
      )}
      <CardWorkOrderModal open={!!link} asn={link?.asn} equipmentId={link?.asn ? undefined : id} onClose={() => setLink(null)}
        onSaved={() => { reload(); onChanged() }} />
    </Drawer>
  )
}

function EquipmentList() {
  const categories = useCategories()
  const [reloadKey, setReloadKey] = useState(0)
  const [modal, setModal] = useState<{ row?: Row } | null>(null)
  const [drawer, setDrawer] = useState<number>()
  const [importing, setImporting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()
  const reload = () => setReloadKey((k) => k + 1)

  const open = async (row?: Row) => {
    form.resetFields()
    const v = row ? await get<Row>(`/equipment/${row.id}`) : { status: 1 }
    // Liste des énergies : valeurs numériques (code)
    form.setFieldsValue({ ...v, gradeCode: v.gradeCode != null && v.gradeCode !== '' ? Number(v.gradeCode) : undefined })
    setModal({ row })
  }
  const save = async () => {
    const v = await form.validateFields()
    setSaving(true)
    try {
      const r = modal?.row ? await put<Row>(`/equipment/${modal.row.id}`, v) : await post<Row>('/equipment', v)
      message.success(r.cardsUpdated ? `Équipement enregistré ; ${r.cardsUpdated} carte(s) passée(s) au nouvel ordre de travail` : 'Équipement enregistré')
      setModal(null)
      reload()
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <SearchTable<Row>
        url="/equipment" exportUrl="/equipment/export" rowKey="id" pageSize={15} reloadKey={reloadKey}
        initialFilters={{}}
        filters={
          <>
            <Form.Item name="q" label="Recherche"><Input allowClear placeholder="Désignation, OT, n° de série, carte…" style={{ width: 260 }} /></Form.Item>
            <Form.Item name="category" label="Catégorie">
              <Select allowClear style={{ width: 190 }} options={categories.map((c) => ({ value: c, label: c }))} />
            </Form.Item>
            <Form.Item name="status" label="Statut">
              <Select allowClear style={{ width: 150 }} options={EQUIPMENT_STATUS.map(({ value, label }) => ({ value, label }))} />
            </Form.Item>
            <Form.Item name="stationId" label="Station"><RemoteSelect type="Station" allowClear style={{ width: 180 }} /></Form.Item>
          </>
        }
        extraButtons={
          <>
            <Button type="primary" ghost icon={<PlusOutlined />} onClick={() => open()}>Ajouter un équipement</Button>
            <Button icon={<UploadOutlined />} onClick={() => setImporting(true)}>Importer depuis Excel</Button>
          </>
        }
        columns={[
          { title: 'Désignation', dataIndex: 'name', render: (v, r) => <Typography.Link onClick={() => setDrawer(r.id as number)}>{v as string}</Typography.Link> },
          { title: 'Ordre de travail', dataIndex: 'workOrderNo', render: (v) => v && <Typography.Text code>{v as string}</Typography.Text> },
          { title: 'Catégorie', dataIndex: 'category' },
          { title: 'Marque / modèle', key: 'bm', render: (_, r) => [r.brand, r.model].filter(Boolean).join(' ') },
          { title: 'N° de série', dataIndex: 'serialNo' },
          { title: 'N° de parc', dataIndex: 'fleetNo' },
          { title: 'Énergie', dataIndex: 'gradeName' },
          { title: 'Station', dataIndex: 'stationName' },
          { title: 'Statut', dataIndex: 'status', render: statusTag },
          { title: 'Cartes', dataIndex: 'cardCount', align: 'center' },
          {
            title: 'Opération', key: 'ops', fixed: 'right', render: (_, r) => (
              <Space>
                <Tooltip title="Cartes rattachées"><Button size="small" type="text" icon={<LinkOutlined />} onClick={() => setDrawer(r.id as number)} /></Tooltip>
                <Tooltip title="Modifier"><Button size="small" type="text" icon={<EditOutlined />} onClick={() => open(r)} /></Tooltip>
                <Tooltip title="Supprimer">
                  <Button size="small" type="text" danger icon={<DeleteOutlined />}
                    onClick={() => confirmDelete(() => del(`/equipment/${r.id}`), reload)} />
                </Tooltip>
              </Space>
            ),
          },
        ]}
      />
      <Modal title={modal?.row ? "Modifier l'équipement" : 'Ajouter un équipement'} open={!!modal} onCancel={() => setModal(null)}
        onOk={save} confirmLoading={saving} okText="Enregistrer" cancelText="Annuler" width={820} destroyOnClose>
        <Form form={form} layout="vertical"><EquipmentForm categories={categories} /></Form>
      </Modal>
      <EquipmentDrawer id={drawer} onClose={() => setDrawer(undefined)} onChanged={reload} />
      <ExcelImportModal open={importing} onClose={() => setImporting(false)} onDone={reload}
        title="Importer des équipements" templateUrl="/equipment/import-template" importUrl="/equipment/import"
        help="Téléchargez le modèle, remplissez une ligne par équipement, puis déposez le fichier. Un équipement dont le n° de série existe déjà est mis à jour. La colonne « N° de carte » rattache directement les cartes à l'équipement."
        columns={[
          { title: 'Désignation', dataIndex: 'name' },
          { title: 'Ordre de travail', dataIndex: 'workOrderNo' },
          { title: 'Catégorie', dataIndex: 'category' },
          { title: 'N° de série', dataIndex: 'serialNo' },
          { title: 'N° de parc', dataIndex: 'fleetNo' },
          { title: 'Énergie', dataIndex: 'grade' },
          { title: 'Station', dataIndex: 'station' },
          { title: 'Statut', dataIndex: 'status' },
          { title: 'Cartes', dataIndex: 'cards' },
        ]} />
    </>
  )
}

function CardWorkOrders() {
  const [reloadKey, setReloadKey] = useState(0)
  const [modal, setModal] = useState<{ asn?: string } | null>(null)
  return (
    <>
      <SearchTable<Row>
        url="/card-work-orders" exportUrl="/card-work-orders/export" rowKey="id" pageSize={15} reloadKey={reloadKey}
        initialFilters={{ history: false }}
        filters={
          <>
            <Form.Item name="q" label="Recherche">
              <Input allowClear placeholder="5 derniers chiffres, OT, porteur, équipement…" style={{ width: 280 }} />
            </Form.Item>
            <Form.Item name="equipmentId" label="Équipement"><EquipmentSelect style={{ width: 240 }} /></Form.Item>
            <Form.Item name="history" label="Historique complet" valuePropName="checked"><Switch /></Form.Item>
          </>
        }
        extraButtons={<Button type="primary" ghost icon={<LinkOutlined />} onClick={() => setModal({})}>Attribuer un ordre de travail</Button>}
        columns={[
          { title: 'N° de carte', dataIndex: 'asn' },
          { title: 'Type', dataIndex: 'kind' },
          { title: 'Porteur', dataIndex: 'holder' },
          { title: 'Station', dataIndex: 'stationName' },
          { title: 'Ordre de travail', dataIndex: 'workOrderNo', render: (v) => v && <Typography.Text code>{v as string}</Typography.Text> },
          { title: 'Équipement', dataIndex: 'equipmentName' },
          { title: 'Depuis', dataIndex: 'validFrom', render: fmtDateTime },
          { title: "Jusqu'au", dataIndex: 'validTo', render: (v) => (v ? fmtDateTime(v) : <Tag color="green">En cours</Tag>) },
          { title: 'Saisi par', dataIndex: 'createBy' },
          {
            title: 'Opération', key: 'ops', fixed: 'right',
            render: (_, r) => <Button size="small" type="link" onClick={() => setModal({ asn: r.asn as string })}>Modifier</Button>,
          },
        ]}
      />
      <CardWorkOrderModal open={!!modal} asn={modal?.asn} onClose={() => setModal(null)} onSaved={() => setReloadKey((k) => k + 1)} />
    </>
  )
}

function Consumption() {
  const categories = useCategories()
  return (
    <SearchTable<Row>
      url="/equipment/consumption" exportUrl="/equipment/consumption/export" rowKey={(r) => `${r.workOrderNo}-${r.equipmentId}-${r.oilName}`}
      pageSize={15}
      initialFilters={{ period: [dayjs().startOf('month'), dayjs()] }}
      toParams={(v) => {
        const { period, ...rest } = v as { period?: [dayjs.Dayjs, dayjs.Dayjs] }
        return serializeFilters({ ...rest, start: period?.[0], end: period?.[1] })
      }}
      filters={
        <>
          <Form.Item name="period" label="Période"><DatePicker.RangePicker showTime /></Form.Item>
          <Form.Item name="workOrderNo" label="Ordre de travail"><Input allowClear style={{ width: 180 }} /></Form.Item>
          <Form.Item name="equipmentId" label="Équipement"><EquipmentSelect style={{ width: 220 }} /></Form.Item>
          <Form.Item name="category" label="Catégorie">
            <Select allowClear style={{ width: 180 }} options={categories.map((c) => ({ value: c, label: c }))} />
          </Form.Item>
        </>
      }
      summary={(r) => (
        <Typography.Text strong>
          {fmtNum(r.totalTransactions, 0)} transaction(s) — Volume : {fmtNum(r.totalVolume)} — Montant : {fmtNum(r.totalAmount)}
        </Typography.Text>
      )}
      columns={[
        { title: 'Ordre de travail', dataIndex: 'workOrderNo', render: (v) => v && <Typography.Text code>{v as string}</Typography.Text> },
        { title: 'Équipement', dataIndex: 'equipmentName' },
        { title: 'Catégorie', dataIndex: 'category' },
        { title: 'N° de série', dataIndex: 'serialNo' },
        { title: 'Énergie', dataIndex: 'oilName' },
        { title: 'Transactions', dataIndex: 'transactionCount', align: 'right' },
        { title: 'Cartes', dataIndex: 'cardCount', align: 'right' },
        { title: 'Volume', dataIndex: 'volume', align: 'right', render: (v) => fmtNum(v) },
        { title: 'Montant', dataIndex: 'amount', align: 'right', render: (v) => fmtNum(v) },
        { title: 'Première', dataIndex: 'firstTime', render: fmtDateTime },
        { title: 'Dernière', dataIndex: 'lastTime', render: fmtDateTime },
      ]}
    />
  )
}

/** Équipements (engins, véhicules, machines), ordres de travail des cartes et consommation par ordre de travail. */
export default function Equipment() {
  return (
    <>
      <PageHeader title="Équipements et ordres de travail" section="Configuration système" />
      <Card>
        <Tabs destroyInactiveTabPane items={[
          { key: 'eq', label: 'Équipements', children: <EquipmentList /> },
          { key: 'cards', label: 'Cartes et ordres de travail', children: <CardWorkOrders /> },
          { key: 'conso', label: 'Consommation par ordre de travail', children: <Consumption /> },
        ]} />
      </Card>
    </>
  )
}
