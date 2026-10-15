import { useCallback, useState } from 'react'
import { Button, Card, Descriptions, Form, Input, InputNumber, Modal, Tag, Tooltip, message } from 'antd'
import { AuditOutlined } from '@ant-design/icons'
import { del, get, post, put } from '../../api/http'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import { fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>

const STATUS = [
  { label: 'En attente', color: 'orange' },
  { label: 'Approuvée', color: 'green' },
  { label: 'Refusée', color: 'red' },
]

function OrderFields() {
  const form = Form.useFormInstance()
  const user = Form.useWatch('user', form) ?? ''
  const certificateNo = Form.useWatch('certificateNo', form) ?? ''
  return (
    <>
      <Form.Item name="user" label="Nom du porteur"><Input /></Form.Item>
      <Form.Item name="certificateNo" label="N° de pièce"><Input /></Form.Item>
      <Form.Item name="asn" label="Carte" rules={[{ required: true }]}>
        <RemoteSelect type="OrderAsn" textAsValue params={{ user, certificateNo }} enabled={!!(user || certificateNo)}
          style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item name="amount" label="Montant à valider" rules={[{ required: true }]}>
        <InputNumber min={0.01} style={{ width: '100%' }} />
      </Form.Item>
    </>
  )
}

/** Audit de certification : validation des recharges (Main/OrderSubmit d'origine). */
export default function OrderSubmit() {
  const load = useCallback(() => get<Row[]>('/order-checks'), [])
  const [check, setCheck] = useState<Row | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const decide = async (pass: boolean) => {
    await post(`/order-checks/${check!.id}/check`, { pass })
    message.success('Opération réussie')
    setCheck(null)
    setReloadKey((k) => k + 1)
  }

  return (
    <>
      <PageHeader title="Validation des commandes" section="Audit de certification" />
      <Card>
        <CrudTable<Row>
          rowKey="id"
          load={load}
          reloadKey={reloadKey}
          addLabel="Nouvelle demande"
          columns={[
            { title: 'N° de commande', dataIndex: 'orderNo' },
            { title: 'N° de carte', dataIndex: 'asn' },
            { title: 'Porteur', dataIndex: 'customerName' },
            { title: 'Montant', dataIndex: 'amount', render: (v) => fmtNum(v) },
            { title: 'Création', dataIndex: 'createTime', render: fmtDateTime },
            { title: 'Demandeur', dataIndex: 'submitName' },
            {
              title: 'État', dataIndex: 'statue',
              render: (v: number) => <Tag color={STATUS[v]?.color}>{STATUS[v]?.label}</Tag>,
            },
            { title: 'Validateur', dataIndex: 'checkName' },
          ]}
          editValues={(r) => ({ asn: r.asn, amount: r.amount })}
          renderForm={() => <OrderFields />}
          onCreate={(v) => post('/order-checks', { asn: v.asn, amount: v.amount })}
          onUpdate={(r, v) => put(`/order-checks/${r.id}`, { asn: v.asn, amount: v.amount })}
          onDelete={(r) => del(`/order-checks/${r.id}`)}
          extraActions={(r) =>
            r.needCheck && Number(r.statue) === 0 ? (
              <Tooltip title="Valider">
                <Button size="small" type="text" icon={<AuditOutlined />} onClick={() => setCheck(r)} />
              </Tooltip>
            ) : null
          }
        />
      </Card>
      <Modal title="Validation de la demande" open={!!check} onCancel={() => setCheck(null)} footer={[
        <Button key="r" danger onClick={() => decide(false)}>Refuser</Button>,
        <Button key="a" type="primary" onClick={() => decide(true)}>Approuver</Button>,
      ]}>
        {check && (
          <Descriptions bordered size="small" column={1} items={[
            { key: 'c', label: 'N° de carte', children: String(check.asn) },
            { key: 'u', label: 'Porteur', children: String(check.customerName ?? '') },
            { key: 'a', label: 'Montant', children: fmtNum(check.amount) },
            { key: 's', label: 'Demandeur', children: String(check.submitName ?? '') },
          ]} />
        )}
      </Modal>
    </>
  )
}
