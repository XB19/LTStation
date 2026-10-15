import { useCallback, useState } from 'react'
import { Button, Card, Col, Descriptions, Form, Input, InputNumber, Row, Select, Space, Tabs, message } from 'antd'
import { del, get, post, put } from '../../api/http'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import { fmtDateTime } from '../../utils/format'

type Row = Record<string, unknown>
const base = '/discount-score'
const num = (props: object = {}) => <InputNumber style={{ width: '100%' }} {...props} />

function Discounts() {
  const load = useCallback(() => get<Row[]>(`${base}/discounts`), [])
  return (
    <CrudTable<Row> rowKey="id" load={load} addLabel="Ajouter une remise"
      columns={[
        { title: 'Nom de la remise', dataIndex: 'name' },
        { title: 'Valeur de la remise', dataIndex: 'disCountValue' },
        { title: "Date d'effet", dataIndex: 'createTime', render: fmtDateTime },
      ]}
      editValues={(r) => ({ name: r.name, value: r.disCountValue })}
      renderForm={() => (
        <>
          <Form.Item name="name" label="Nom de la remise" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="value" label="Valeur de la remise" rules={[{ required: true }]}>{num({ min: 0, max: 100 })}</Form.Item>
        </>
      )}
      onCreate={(v) => post(`${base}/discounts`, v)}
      onUpdate={(r, v) => put(`${base}/discounts/${r.id}`, v)}
      onDelete={(r) => del(`${base}/discounts/${r.id}`)}
    />
  )
}

function DepositReturns() {
  const load = useCallback(() => get<Row[]>(`${base}/deposit-returns`), [])
  return (
    <CrudTable<Row> rowKey="id" load={load} addLabel="Ajouter un palier de cashback"
      columns={[
        { title: 'ID', dataIndex: 'id' },
        { title: 'Nom du cashback', dataIndex: 'name' },
        { title: 'Montant de recharge', dataIndex: 'amount' },
        { title: 'Taux de cashback', dataIndex: 'radio' },
        { title: "Date d'effet", dataIndex: 'createTime', render: fmtDateTime },
      ]}
      renderForm={() => (
        <>
          <Form.Item name="name" label="Nom du cashback" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="amount" label="Montant de recharge" rules={[{ required: true }]}>{num({ min: 0 })}</Form.Item>
          <Form.Item name="radio" label="Taux de cashback" rules={[{ required: true }]}>{num({ min: 0, step: 0.01 })}</Form.Item>
        </>
      )}
      onCreate={(v) => post(`${base}/deposit-returns`, v)}
      onUpdate={(r, v) => put(`${base}/deposit-returns/${r.id}`, v)}
      onDelete={(r) => del(`${base}/deposit-returns/${r.id}`)}
    />
  )
}

function ScoreRateFields() {
  const form = Form.useFormInstance()
  const scoreType = Form.useWatch('scoreType', form)
  return (
    <>
      <Form.Item name="scoreType" label="Type de conversion" initialValue={0}>
        <Select options={[{ value: 0, label: 'Consommation' }, { value: 1, label: 'Recharge' }]} />
      </Form.Item>
      {scoreType === 0 && (
        <Form.Item name="oilTypeId" label="Énergie" rules={[{ required: true }]}>
          <RemoteSelect type="OilType" style={{ width: '100%' }}
            onChange={(_, opt) => form.setFieldValue('oilTypeName', (opt as { label?: string })?.label)} />
        </Form.Item>
      )}
      <Form.Item name="oilTypeName" hidden><Input /></Form.Item>
      <Form.Item name="rate" label="Taux de conversion en points" rules={[{ required: true }]}>{num({ min: 0, step: 0.001 })}</Form.Item>
    </>
  )
}

function ScoreRates() {
  const load = useCallback(() => get<Row[]>(`${base}/score-rates`), [])
  return (
    <CrudTable<Row> rowKey="id" load={load} addLabel="Ajouter un taux"
      columns={[
        { title: 'Type de conversion', dataIndex: 'scoreType', render: (v) => (Number(v) === 0 ? 'Consommation' : 'Recharge') },
        { title: 'Énergie', dataIndex: 'oilTypeName' },
        { title: 'Taux de conversion', dataIndex: 'rate' },
      ]}
      renderForm={() => <ScoreRateFields />}
      onCreate={(v) => post(`${base}/score-rates`, v)}
      onUpdate={(r, v) => put(`${base}/score-rates/${r.id}`, v)}
      onDelete={(r) => del(`${base}/score-rates/${r.id}`)}
    />
  )
}

function EmployeeRates() {
  const load = useCallback(() => get<Row[]>(`${base}/employee-rates`), [])
  return (
    <CrudTable<Row> rowKey="id" load={load} addLabel="Ajouter un taux employé"
      columns={[
        { title: 'Niveau de droits', dataIndex: 'authName' },
        { title: 'Taux de conversion en points', dataIndex: 'rate' },
        { title: "Date d'effet", dataIndex: 'createTime', render: fmtDateTime },
      ]}
      editValues={(r) => ({ authorityId: r.authorityId, rate: r.rate })}
      renderForm={() => (
        <>
          <Form.Item name="authorityId" label="Niveau de droits" rules={[{ required: true }]}>
            <RemoteSelect type="Auth" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="rate" label="Taux de conversion en points" rules={[{ required: true }]}>{num({ min: 0, step: 0.001 })}</Form.Item>
        </>
      )}
      onCreate={(v) => post(`${base}/employee-rates`, v)}
      onUpdate={(r, v) => put(`${base}/employee-rates/${r.id}`, v)}
      onDelete={(r) => del(`${base}/employee-rates/${r.id}`)}
    />
  )
}

function MemberRates() {
  const load = useCallback(() => get<Row[]>(`${base}/member-rates`), [])
  return (
    <CrudTable<Row> rowKey="id" load={load} addLabel="Ajouter un niveau"
      columns={[
        { title: 'N°', dataIndex: 'id' },
        { title: 'Niveau de membre', dataIndex: 'memberName' },
        { title: 'Remise appliquée', dataIndex: 'discount' },
        { title: 'Consommation requise', dataIndex: 'totalConsume' },
        { title: 'Création', dataIndex: 'createTime', render: fmtDateTime },
      ]}
      renderForm={() => (
        <>
          <Form.Item name="memberName" label="Nom du niveau" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="discount" label="Remise appliquée" rules={[{ required: true }]}>{num({ min: 0, step: 0.01 })}</Form.Item>
          <Form.Item name="totalConsume" label="Consommation requise" rules={[{ required: true }]}>{num({ min: 0 })}</Form.Item>
        </>
      )}
      onCreate={(v) => post(`${base}/member-rates`, v)}
      onUpdate={(r, v) => put(`${base}/member-rates/${r.id}`, v)}
      onDelete={(r) => del(`${base}/member-rates/${r.id}`)}
    />
  )
}

function ScoreConsume() {
  const [form] = Form.useForm()
  const [cards, setCards] = useState<string[]>([])
  const [info, setInfo] = useState<{ account?: unknown; score?: number; cardNo?: string; employeeId?: number } | null>(null)
  const [deal, setDeal] = useState<number | null>(null)

  const search = async () => {
    const v = form.getFieldsValue()
    setInfo(null)
    setCards([])
    const r = await get<{ cards?: string[]; account?: number; score?: number }>(`${base}/holders`, v)
    if (r.cards) setCards(r.cards)
    else setInfo({ account: r.account, score: r.score, employeeId: r.account })
  }
  const pickCard = async (asn: string) => {
    const r = await get<{ account: unknown; score: number }>(`${base}/cards/${asn}`, { type: form.getFieldValue('type') })
    setInfo({ account: r.account, score: r.score, cardNo: asn })
  }
  const consume = async () => {
    if (!info) return
    const r = await post<{ score: number }>(`${base}/consume`, {
      type: form.getFieldValue('type'), cardNo: info.cardNo, employeeId: info.employeeId, score: deal,
    })
    message.success('Opération réussie')
    setInfo({ ...info, score: r.score })
    setDeal(null)
  }

  return (
    <Row gutter={24}>
      <Col xs={24} lg={12}>
        <Form form={form} layout="vertical" initialValues={{ type: 0, name: '', phone: '' }}>
          <Form.Item name="type" label="Type d'utilisateur">
            <Select options={[{ value: 0, label: 'Utilisateur' }, { value: 1, label: 'Employé' }, { value: 2, label: 'Groupe' }]} />
          </Form.Item>
          <Form.Item name="name" label="Nom"><Input /></Form.Item>
          <Form.Item name="phone" label="Téléphone"><Input /></Form.Item>
          <Button type="primary" onClick={search}>Rechercher</Button>
        </Form>
      </Col>
      <Col xs={24} lg={12}>
        {cards.length > 0 && (
          <Form.Item label="N° de carte">
            <Select placeholder="Choisir une carte" onChange={pickCard} options={cards.map((c) => ({ value: c, label: c }))} />
          </Form.Item>
        )}
        {info && (
          <>
            <Descriptions bordered column={1} size="small" items={[
              { key: 'a', label: 'Compte de la carte', children: String(info.account ?? '') },
              { key: 's', label: 'Points actuels', children: String(info.score ?? 0) },
            ]} />
            <Space style={{ marginTop: 16 }}>
              <InputNumber placeholder="Points à consommer" min={0} value={deal} onChange={setDeal} />
              <Button type="primary" onClick={consume} disabled={!deal}>Consommer</Button>
            </Space>
          </>
        )}
      </Col>
    </Row>
  )
}

/** Remises & points (Main/EmpoyeeScore d'origine). */
export default function DiscountScore() {
  return (
    <>
      <PageHeader title="Remises & points" section="Configuration système" />
      <Card>
        <Tabs destroyInactiveTabPane items={[
          { key: 'discount', label: 'Remises', children: <Discounts /> },
          { key: 'deposit', label: 'Cashback sur recharge', children: <DepositReturns /> },
          { key: 'score', label: 'Points utilisateurs', children: <ScoreRates /> },
          { key: 'consume', label: 'Consommation de points', children: <ScoreConsume /> },
          { key: 'emp', label: 'Points employés', children: <EmployeeRates /> },
          { key: 'member', label: 'Niveaux de membre', children: <MemberRates /> },
        ]} />
      </Card>
    </>
  )
}
