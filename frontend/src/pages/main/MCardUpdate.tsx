import { useState } from 'react'
import { Button, Card, Form, Input, Modal, Tooltip, message } from 'antd'
import { EditOutlined } from '@ant-design/icons'
import { get, put } from '../../api/http'
import PageHeader from '../../components/PageHeader'
import SearchTable from '../../components/SearchTable'

type Row = Record<string, unknown>

/** Modification des cartes utilisateur (Main/MCardUpdate d'origine). */
export default function MCardUpdate() {
  const [edit, setEdit] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [form] = Form.useForm()

  const open = async (asn: string) => {
    form.setFieldsValue(await get(`/card-ops/customer-cards/${asn}`))
    setEdit(asn)
  }
  const save = async () => {
    await put(`/card-ops/customer-cards/${edit}`, await form.validateFields())
    message.success('Opération réussie')
    setEdit(null)
    setReloadKey((k) => k + 1)
  }

  return (
    <>
      <PageHeader title="Modification de cartes" section="Opérations sur cartes" />
      <Card>
        <SearchTable<Row>
          url="/customers"
          rowKey={(r) => String(r.asn)}
          reloadKey={reloadKey}
          initialFilters={{ searchType: 0 }}
          filters={
            <>
              <Form.Item name="searchType" hidden><Input /></Form.Item>
              <Form.Item name="cardNo" label="N° de carte"><Input allowClear /></Form.Item>
            </>
          }
          columns={[
            { title: 'N° de carte', dataIndex: 'asn' },
            { title: 'Nom', dataIndex: 'customerName' },
            { title: 'Groupe', dataIndex: 'groupName' },
            { title: 'Immatriculation', dataIndex: 'carNo' },
            { title: 'Téléphone', dataIndex: 'phone' },
            { title: 'État de la carte', dataIndex: 'cardStatue' },
            { title: 'Solde', dataIndex: 'balance' },
            {
              title: 'Opération', key: 'op',
              render: (_, r) => (
                <Tooltip title="Modifier">
                  <Button size="small" type="text" icon={<EditOutlined />} onClick={() => open(String(r.asn))} />
                </Tooltip>
              ),
            },
          ]}
        />
      </Card>
      <Modal title="Modifier la carte" open={!!edit} onOk={save} onCancel={() => setEdit(null)} okText="Enregistrer" cancelText="Annuler">
        <Form form={form} layout="vertical">
          <Form.Item name="cardNo" label="N° de carte"><Input disabled /></Form.Item>
          <Form.Item name="name" label="Nom" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="carNo" label="Immatriculation"><Input /></Form.Item>
          <Form.Item name="phone" label="Téléphone"><Input /></Form.Item>
          <Form.Item name="password" label="Mot de passe de la carte" extra="Laisser vide pour ne pas le changer">
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
