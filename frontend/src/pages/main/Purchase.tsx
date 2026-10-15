import { useEffect, useState } from 'react'
import { Button, Card, DatePicker, Form, Image, Input, InputNumber, Modal, Select, Space, Tag, Tooltip, message } from 'antd'
import { CheckOutlined, DeleteOutlined, EditOutlined, ShoppingCartOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { del, get, post, put } from '../../api/http'
import { confirmDelete } from '../../components/confirmDelete'
import ImageListUpload from '../../components/ImageListUpload'
import PageHeader from '../../components/PageHeader'
import SearchTable, { serializeFilters } from '../../components/SearchTable'
import { fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>

const STATES = [
  { value: 0, label: "En cours d'achat", color: 'orange' },
  { value: 1, label: 'Expédiée', color: 'blue' },
  { value: 2, label: 'Reçue', color: 'green' },
]

function usePlatforms() {
  const [platforms, setPlatforms] = useState<Row[]>([])
  useEffect(() => {
    get<Row[]>('/purchase/platforms').then(setPlatforms)
  }, [])
  return platforms
}

function PlatformFilters() {
  const platforms = usePlatforms()
  const form = Form.useFormInstance()
  const platformId = Form.useWatch('platformId', form)
  const [supplies, setSupplies] = useState<Row[]>([])
  useEffect(() => {
    if (platformId == null) setSupplies([])
    else get<Row[]>('/purchase/oil-supplies', { platformId }).then(setSupplies)
  }, [platformId])
  return (
    <>
      <Form.Item name="platformId" label="Fournisseur">
        <Select allowClear style={{ width: 180 }} options={platforms.map((p) => ({ value: p.platformId as number, label: String(p.platformName) }))}
          onChange={() => form.setFieldValue('oilSupplyId', undefined)} />
      </Form.Item>
      <Form.Item name="oilSupplyId" label="Point d'approvisionnement">
        <Select allowClear style={{ width: 180 }} options={supplies.map((s) => ({ value: s.id as number, label: String(s.oilSupplyName) }))} />
      </Form.Item>
      <Form.Item name="commoName" label="Produit"><Input allowClear /></Form.Item>
    </>
  )
}

/** Liste des prix carburant publiés par les fournisseurs (Main/PurchaseOilPriceList d'origine). */
export function PurchaseOilPriceList() {
  const [order, setOrder] = useState<Row | null>(null)
  const [form] = Form.useForm()
  const submit = async () => {
    const v = await form.validateFields()
    const r = await post<{ orderNumber: string }>('/purchase/orders', {
      releaseId: order!.id, oilValue: v.oilValue, storageTime: v.storageTime?.format('YYYY-MM-DDTHH:mm:ss'),
    })
    message.success(`Commande ${r.orderNumber} créée`)
    setOrder(null)
  }
  return (
    <>
      <PageHeader title="Liste des prix carburant" section="Achats" />
      <Card>
        <SearchTable<Row>
          url="/purchase/prices" rowKey="id" filters={<PlatformFilters />}
          columns={[
            { title: 'Fournisseur', dataIndex: 'platformName' },
            { title: "Point d'approvisionnement", dataIndex: 'oilSupplyName' },
            { title: 'Produit', dataIndex: 'commoName' },
            { title: 'Spécification', dataIndex: 'specificat' },
            { title: 'Prix unitaire', render: (_, r) => `${fmtNum(r.unitPrice)} ${r.priceUnit ?? ''}/${r.oilUnit ?? ''}` },
            { title: 'Dernière publication', dataIndex: 'releaseTime', render: fmtDateTime },
            {
              title: 'Opération', key: 'op',
              render: (_, r) => (
                <Tooltip title="Commander">
                  <Button size="small" type="text" icon={<ShoppingCartOutlined />} onClick={() => {
                    form.resetFields()
                    setOrder(r)
                  }} />
                </Tooltip>
              ),
            },
          ]}
        />
      </Card>
      <Modal title={`Commander — ${order?.commoName ?? ''}`} open={!!order} onOk={submit} onCancel={() => setOrder(null)}
        okText="Commander" cancelText="Annuler">
        <p>Prix unitaire : {fmtNum(order?.unitPrice)} {String(order?.priceUnit ?? '')}/{String(order?.oilUnit ?? '')}</p>
        <Form form={form} layout="vertical">
          <Form.Item name="oilValue" label={`Quantité (${String(order?.oilUnit ?? '')})`} rules={[{ required: true }]}>
            <InputNumber min={0.01} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="storageTime" label="Réception souhaitée"><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
        </Form>
      </Modal>
    </>
  )
}

/** Commandes d'achat de la station (Main/PurchaseOrder d'origine). */
export function PurchaseOrders() {
  const [reloadKey, setReloadKey] = useState(0)
  const [edit, setEdit] = useState<Row | null>(null)
  const [receipt, setReceipt] = useState<Row | null>(null)
  const [eForm] = Form.useForm()
  const [rForm] = Form.useForm()
  const reload = () => setReloadKey((k) => k + 1)

  return (
    <>
      <PageHeader title="Commandes d'achat" section="Achats" />
      <Card>
        <SearchTable<Row>
          url="/purchase/orders" exportUrl="/purchase/orders/export" rowKey="id" reloadKey={reloadKey}
          toParams={(v) => {
            const { period, ...rest } = v as { period?: [dayjs.Dayjs, dayjs.Dayjs] }
            return serializeFilters({ ...rest, start: period?.[0], end: period?.[1] })
          }}
          filters={
            <>
              <PlatformFilters />
              <Form.Item name="purchaseState" label="État"><Select allowClear style={{ width: 160 }} options={STATES} /></Form.Item>
              <Form.Item name="period" label="Date d'achat"><DatePicker.RangePicker showTime /></Form.Item>
            </>
          }
          columns={[
            { title: 'N° de commande', dataIndex: 'orderNumber' },
            {
              title: 'État', dataIndex: 'purchaseState',
              render: (v) => {
                const s = STATES.find((x) => x.value === Number(v))
                return s ? <Tag color={s.color}>{s.label}</Tag> : null
              },
            },
            { title: 'Fournisseur', dataIndex: 'platformLabel' },
            { title: "Point d'approvisionnement", dataIndex: 'oilSupplyLabel' },
            { title: 'Produit', dataIndex: 'commoLabel' },
            { title: 'Quantité', render: (_, r) => `${fmtNum(r.oilValue)} ${r.oilUnit ?? ''}` },
            { title: 'Prix unitaire', dataIndex: 'unitPrice', render: (v) => fmtNum(v) },
            { title: 'Montant', render: (_, r) => `${fmtNum(r.purchaseAmount)} ${r.priceUnit ?? ''}` },
            { title: "Date d'achat", dataIndex: 'purchaseTime', render: fmtDateTime },
            { title: 'Réception prévue', dataIndex: 'storageTime', render: fmtDateTime },
            { title: 'Vendeur', render: (_, r) => [r.sellerEmployeeName, r.sellerTelephone].filter(Boolean).join(' — ') },
            { title: 'Livreur', render: (_, r) => [r.deliverName, r.deliverTelephone].filter(Boolean).join(' — ') },
            { title: 'Expédition', dataIndex: 'shipTime', render: fmtDateTime },
            { title: 'Réception', dataIndex: 'realStorageTime', render: fmtDateTime },
            { title: 'Quantité reçue', dataIndex: 'realOilValue', render: (v) => fmtNum(v) },
            {
              title: 'Bons de livraison', dataIndex: 'purchaseFileIds',
              render: (v) => (
                <Image.PreviewGroup>
                  <Space>{String(v ?? '').split(',').filter(Boolean).map((u) => <Image key={u} src={u} width={36} height={36} />)}</Space>
                </Image.PreviewGroup>
              ),
            },
            {
              title: 'Opération', key: 'op', fixed: 'right',
              render: (_, r) => (
                <Space>
                  {Number(r.purchaseState) === 0 && (
                    <>
                      <Tooltip title="Modifier"><Button size="small" type="text" icon={<EditOutlined />} onClick={() => {
                        eForm.setFieldsValue({ oilValue: r.oilValue, storageTime: r.storageTime ? dayjs(r.storageTime as string) : undefined })
                        setEdit(r)
                      }} /></Tooltip>
                      <Tooltip title="Annuler la commande">
                        <Button size="small" type="text" danger icon={<DeleteOutlined />}
                          onClick={() => confirmDelete(() => del(`/purchase/orders/${r.id}`), reload, 'Annuler cette commande ?')} />
                      </Tooltip>
                    </>
                  )}
                  {Number(r.purchaseState) === 1 && (
                    <Tooltip title="Confirmer la réception"><Button size="small" type="text" icon={<CheckOutlined />} onClick={() => {
                      rForm.setFieldsValue({ realOilValue: r.oilValue, files: [] })
                      setReceipt(r)
                    }} /></Tooltip>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>
      <Modal title="Modifier la commande" open={!!edit} onCancel={() => setEdit(null)} okText="Enregistrer" cancelText="Annuler"
        onOk={async () => {
          const v = await eForm.validateFields()
          await put(`/purchase/orders/${edit!.id}`, { oilValue: v.oilValue, storageTime: v.storageTime?.format('YYYY-MM-DDTHH:mm:ss') })
          message.success('Opération réussie')
          setEdit(null)
          reload()
        }}>
        <Form form={eForm} layout="vertical">
          <Form.Item name="oilValue" label="Quantité" rules={[{ required: true }]}><InputNumber min={0.01} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="storageTime" label="Réception souhaitée"><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
        </Form>
      </Modal>
      <Modal title="Confirmer la réception" open={!!receipt} onCancel={() => setReceipt(null)} okText="Confirmer" cancelText="Annuler"
        onOk={async () => {
          await post(`/purchase/orders/${receipt!.id}/receipt`, await rForm.validateFields())
          message.success('Réception confirmée')
          setReceipt(null)
          reload()
        }}>
        <Form form={rForm} layout="vertical">
          <Form.Item name="realOilValue" label="Quantité réellement reçue" rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="files" label="Bons de livraison"><ImageListUpload /></Form.Item>
        </Form>
      </Modal>
    </>
  )
}
