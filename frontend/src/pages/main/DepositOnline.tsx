import { useState } from 'react'
import { Button, Card, Col, Descriptions, Form, Input, InputNumber, Row, Select, message } from 'antd'
import { get, post, type SelectItem } from '../../api/http'
import PageHeader from '../../components/PageHeader'

const HOLDER_TYPES = [
  { value: 0, label: 'Utilisateur' },
  { value: 1, label: 'Employé' },
  { value: 2, label: 'Application' },
]

interface CardInfo {
  asn: string
  userName: string
  balance: number
}

function OperationCard({ reverse }: { reverse?: boolean }) {
  const [form] = Form.useForm()
  const [cards, setCards] = useState<SelectItem[]>([])
  const [info, setInfo] = useState<CardInfo | null>(null)
  const [loading, setLoading] = useState(false)

  const search = async () => {
    const v = form.getFieldsValue()
    setInfo(null)
    const list = await get<SelectItem[]>('/card-ops/holder-cards', {
      cardType: v.cardType, userName: v.userName ?? '', phone: v.phone ?? '', cno: v.cno ?? '', reverse: !!reverse,
    })
    setCards(list)
    if (list.length === 0) message.info('Aucune carte trouvée')
  }

  const pick = async (asn: string) => setInfo(await get<CardInfo>(`/card-ops/cards/${asn}`))

  const submit = async () => {
    const { amount } = await form.validateFields(['amount'])
    if (!info) return
    setLoading(true)
    try {
      await post(reverse ? '/card-ops/reverse' : '/card-ops/deposit', { cardNo: info.asn, amount })
      message.success('Opération réussie')
      form.setFieldValue('amount', undefined)
      await pick(info.asn)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card title={reverse ? 'Annulation (contre-passation)' : 'Recharge'}>
      <Form form={form} layout="vertical" initialValues={{ cardType: 0 }}>
        <Form.Item name="cardType" label="Objet de la recherche">
          <Select options={HOLDER_TYPES} />
        </Form.Item>
        <Row gutter={12}>
          <Col span={8}><Form.Item name="userName" label="Nom"><Input /></Form.Item></Col>
          <Col span={8}><Form.Item name="phone" label="Téléphone"><Input /></Form.Item></Col>
          <Col span={8}><Form.Item name="cno" label="N° de pièce"><Input /></Form.Item></Col>
        </Row>
        <Button onClick={search} style={{ marginBottom: 16 }}>Rechercher</Button>
        <Form.Item label="N° de carte">
          <Select placeholder="Choisir une carte" value={info?.asn} onChange={pick}
            options={cards.map((c) => ({ value: c.text, label: c.text }))} />
        </Form.Item>
        {info && (
          <Descriptions size="small" bordered column={1} style={{ marginBottom: 16 }} items={[
            { key: 'n', label: 'Porteur', children: info.userName },
            { key: 'b', label: 'Solde', children: Number(info.balance).toFixed(3) },
          ]} />
        )}
        <Form.Item name="amount" label={reverse ? 'Montant à annuler' : 'Montant de la recharge'}
          rules={[{ required: true, message: 'Saisissez un montant' }]}>
          <InputNumber min={0.01} step={1} style={{ width: '100%' }} />
        </Form.Item>
        <Button type="primary" danger={reverse} block disabled={!info} loading={loading} onClick={submit}>
          {reverse ? 'Annuler le montant' : 'Recharger'}
        </Button>
      </Form>
    </Card>
  )
}

/** Recharge et annulation en ligne (Main/DepositOnline d'origine). */
export default function DepositOnline() {
  return (
    <>
      <PageHeader title="Dépôt" section="Opérations sur cartes" />
      <Row gutter={16}>
        <Col xs={24} lg={12}><OperationCard /></Col>
        <Col xs={24} lg={12}><OperationCard reverse /></Col>
      </Row>
    </>
  )
}
