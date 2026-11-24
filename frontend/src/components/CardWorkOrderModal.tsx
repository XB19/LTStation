import { useEffect, useState } from 'react'
import { Alert, Button, DatePicker, Descriptions, Divider, Empty, Form, Input, List, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { del, get, post } from '../api/http'
import { fmtDateTime } from '../utils/format'

type Row = Record<string, unknown>

interface CardInfo {
  asn: string
  kind: string
  holder: string
  cardStatus: string
  stationName: string
  current: Row | null
  history: Row[]
}

/** Liste déroulante d'équipements (désignation · ordre de travail · n° de série), avec recherche côté serveur. */
export function EquipmentSelect(props: { value?: number; onChange?: (v?: number, row?: Row) => void; style?: React.CSSProperties; placeholder?: string }) {
  const [options, setOptions] = useState<Row[]>([])
  const load = (q = '') => get<Row[]>('/equipment/options', { q }).then(setOptions)
  useEffect(() => {
    load()
  }, [])
  return (
    <Select allowClear showSearch filterOption={false} value={props.value} style={props.style} placeholder={props.placeholder ?? 'Équipement'}
      onSearch={(q) => load(q)} onChange={(v) => props.onChange?.(v, options.find((o) => o.id === v))}
      options={options.map((o) => ({
        value: o.id as number,
        label: [o.name, o.workOrderNo && `OT ${o.workOrderNo}`, o.serialNo && `S/N ${o.serialNo}`].filter(Boolean).join(' · '),
      }))} />
  )
}

/**
 * Recherche de carte : n° complet ou 5 derniers chiffres (ou nom du porteur).
 * Une seule carte trouvée : elle est choisie ; plusieurs : la liste s'affiche pour choisir.
 */
function CardSearch({ onPick }: { onPick: (asn: string) => void }) {
  const [q, setQ] = useState('')
  const [rows, setRows] = useState<Row[] | null>(null)
  const [loading, setLoading] = useState(false)
  const search = async () => {
    setLoading(true)
    try {
      const r = await get<Row[]>('/card-work-orders/cards', { q })
      if (r.length === 1) onPick(r[0].asn as string)
      else setRows(r)
    } finally {
      setLoading(false)
    }
  }
  return (
    <>
      <Input.Search placeholder="N° de carte, 5 derniers chiffres ou nom du porteur" enterButton={<><SearchOutlined /> Rechercher</>}
        value={q} onChange={(e) => setQ(e.target.value)} onSearch={search} loading={loading} allowClear />
      {rows && rows.length === 0 && <Empty style={{ marginTop: 16 }} description="Aucune carte trouvée dans votre périmètre" />}
      {rows && rows.length > 1 && (
        <>
          <Alert style={{ marginTop: 12 }} type="info" showIcon message={`${rows.length} cartes correspondent : choisissez la bonne.`} />
          <List size="small" style={{ marginTop: 8 }} bordered dataSource={rows}
            renderItem={(r) => (
              <List.Item actions={[<Button key="pick" type="link" onClick={() => onPick(r.asn as string)}>Choisir</Button>]}>
                <List.Item.Meta title={<Space><Typography.Text code>{r.asn as string}</Typography.Text><Tag>{r.kind as string}</Tag>
                  <Tag color={r.cardStatus === 'Normale' ? 'green' : undefined}>{r.cardStatus as string}</Tag></Space>}
                  description={[r.holder, r.stationName, r.workOrderNo && `OT ${r.workOrderNo}`].filter(Boolean).join(' · ')} />
              </List.Item>
            )} />
        </>
      )}
    </>
  )
}

/** Ordre de travail d'une carte : ordre en cours, nouvel ordre (n° et/ou équipement, date d'effet), historique. */
export default function CardWorkOrderModal(p: { open: boolean; asn?: string; equipmentId?: number; onClose: () => void; onSaved?: () => void }) {
  const [asn, setAsn] = useState<string | undefined>(p.asn)
  const [card, setCard] = useState<CardInfo | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => {
    if (p.open) setAsn(p.asn)
    else setCard(null)
  }, [p.open, p.asn])

  useEffect(() => {
    if (!p.open || !asn) return
    get<CardInfo>('/card-work-orders/card', { asn }).then((c) => {
      setCard(c)
      form.resetFields()
      form.setFieldsValue({ equipmentId: p.equipmentId ?? c.current?.equipmentId, workOrderNo: p.equipmentId ? undefined : c.current?.workOrderNo,
        validFrom: dayjs() })
    })
  }, [p.open, asn, p.equipmentId, form])

  const save = async () => {
    const v = await form.validateFields()
    setSaving(true)
    try {
      setCard(await post<CardInfo>('/card-work-orders', {
        asn, workOrderNo: v.workOrderNo, equipmentId: v.equipmentId, memo: v.memo,
        validFrom: v.validFrom ? (v.validFrom as dayjs.Dayjs).format('YYYY-MM-DDTHH:mm:ss') : undefined,
      }))
      message.success('Ordre de travail enregistré')
      p.onSaved?.()
    } finally {
      setSaving(false)
    }
  }

  const end = async () => {
    setCard(await del<CardInfo>('/card-work-orders', { asn }))
    message.success("L'ordre de travail en cours est terminé")
    p.onSaved?.()
  }

  return (
    <Modal title="Ordre de travail de la carte" open={p.open} onCancel={p.onClose} width={860} destroyOnClose
      footer={card ? (
        <Space>
          {card.current && (
            <Popconfirm title="Mettre fin à l'ordre de travail en cours ?" description="La carte n'aura plus d'ordre de travail à partir de maintenant ; l'historique est conservé."
              okText="Terminer" cancelText="Annuler" onConfirm={end}>
              <Button danger>Terminer l'ordre en cours</Button>
            </Popconfirm>
          )}
          <Button onClick={p.onClose}>Fermer</Button>
          <Button type="primary" loading={saving} onClick={save}>Enregistrer</Button>
        </Space>
      ) : <Button onClick={p.onClose}>Fermer</Button>}>
      {!asn && <CardSearch onPick={setAsn} />}
      {asn && card && (
        <>
          <Descriptions size="small" bordered column={2} items={[
            { label: 'N° de carte', children: <Typography.Text code copyable>{card.asn}</Typography.Text> },
            { label: 'Type', children: card.kind },
            { label: 'Porteur', children: card.holder },
            { label: 'État', children: card.cardStatus },
            { label: 'Station', children: card.stationName },
            {
              label: 'Ordre en cours',
              children: card.current ? (
                <Space direction="vertical" size={0}>
                  <Typography.Text strong>{(card.current.workOrderNo as string) ?? '—'}</Typography.Text>
                  {!!card.current.equipmentName && <Typography.Text type="secondary">{card.current.equipmentName as string}</Typography.Text>}
                  <Typography.Text type="secondary">depuis le {fmtDateTime(card.current.validFrom)}</Typography.Text>
                </Space>
              ) : <Typography.Text type="secondary">Aucun</Typography.Text>,
            },
          ]} />
          {!p.asn && <Button type="link" style={{ paddingLeft: 0 }} onClick={() => { setAsn(undefined); setCard(null) }}>Choisir une autre carte</Button>}
          <Divider orientation="left" plain>{card.current ? "Changer d'ordre de travail" : 'Attribuer un ordre de travail'}</Divider>
          <Form form={form} layout="vertical">
            <Space wrap align="start" style={{ width: '100%' }}>
              <Form.Item name="equipmentId" label="Équipement" style={{ width: 360 }}
                extra="Sans n° saisi, l'ordre de travail de l'équipement est repris.">
                <EquipmentSelect style={{ width: '100%' }} onChange={(_, row) => {
                  form.setFieldValue('equipmentId', row?.id)
                  if (row?.workOrderNo) form.setFieldValue('workOrderNo', row.workOrderNo)
                }} />
              </Form.Item>
              <Form.Item name="workOrderNo" label="N° d'ordre de travail" style={{ width: 260 }}
                rules={[{ max: 64, message: '64 caractères au plus' }, ({ getFieldValue }) => ({
                  validator: (_, v) => (v || getFieldValue('equipmentId') ? Promise.resolve()
                    : Promise.reject(new Error("Saisissez un n° ou choisissez un équipement"))),
                })]}>
                <Input placeholder="ex. 4500012873345" allowClear />
              </Form.Item>
              <Form.Item name="validFrom" label="En vigueur à partir du" style={{ width: 210 }}
                extra="Les transactions à partir de cette date porteront cet ordre.">
                <DatePicker showTime style={{ width: '100%' }} />
              </Form.Item>
            </Space>
            <Form.Item name="memo" label="Observations"><Input.TextArea rows={2} maxLength={500} /></Form.Item>
          </Form>
          <Divider orientation="left" plain>Historique</Divider>
          <Table size="small" rowKey="id" dataSource={card.history} pagination={false} locale={{ emptyText: 'Aucun ordre de travail' }}
            columns={[
              { title: 'Ordre de travail', dataIndex: 'workOrderNo' },
              { title: 'Équipement', dataIndex: 'equipmentName' },
              { title: 'Du', dataIndex: 'validFrom', render: fmtDateTime },
              { title: 'Au', dataIndex: 'validTo', render: (v) => (v ? fmtDateTime(v) : <Tag color="green">En cours</Tag>) },
              { title: 'Observations', dataIndex: 'memo' },
              { title: 'Saisi par', dataIndex: 'createBy' },
            ]} />
        </>
      )}
    </Modal>
  )
}
