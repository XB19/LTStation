import { useState } from 'react'
import { Button, Card, Form, Input, Modal, Space, Tag, Tooltip, message } from 'antd'
import { DeleteOutlined, UserAddOutlined } from '@ant-design/icons'
import { del, post } from '../../api/http'
import { confirmDelete } from '../../components/confirmDelete'
import PageHeader from '../../components/PageHeader'
import SearchTable from '../../components/SearchTable'

type Row = Record<string, unknown>

/** Attribution des cartes M1 prédécoupées (Main/MCardTranslate d'origine). */
export default function MCardTranslate() {
  const [assign, setAssign] = useState<Row | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [form] = Form.useForm()

  const save = async () => {
    const v = await form.validateFields()
    const r = await post<{ asn: string }>('/card-ops/m1/assign', { ...v, cardNo: assign!.cardNo })
    message.success(`Carte attribuée : ${r.asn}`)
    setAssign(null)
    setReloadKey((k) => k + 1)
  }

  return (
    <>
      <PageHeader title="Attribution de cartes" section="Opérations sur cartes" />
      <Card>
        <SearchTable<Row>
          url="/card-ops/m1"
          rowKey="id"
          pageSize={15}
          reloadKey={reloadKey}
          filters={<Form.Item name="cardNo" label="N° de carte"><Input allowClear /></Form.Item>}
          columns={[
            { title: 'N° de carte', dataIndex: 'cardNo' },
            {
              title: 'État', dataIndex: 'inUse',
              render: (v) => (v ? <Tag color="blue">Attribuée</Tag> : <Tag color="green">Disponible</Tag>),
            },
            {
              title: 'Opération', key: 'op',
              render: (_, r) => (
                <Space>
                  <Tooltip title="Attribuer">
                    <Button size="small" type="text" disabled={!!r.inUse} icon={<UserAddOutlined />}
                      onClick={() => {
                        form.resetFields()
                        setAssign(r)
                      }} />
                  </Tooltip>
                  <Tooltip title="Supprimer">
                    <Button size="small" type="text" danger disabled={!!r.inUse} icon={<DeleteOutlined />}
                      onClick={() => confirmDelete(() => del(`/card-ops/m1/${r.id}`), () => setReloadKey((k) => k + 1))} />
                  </Tooltip>
                </Space>
              ),
            },
          ]}
        />
      </Card>
      <Modal title={`Attribuer la carte ${assign?.cardNo ?? ''}`} open={!!assign} onOk={save} onCancel={() => setAssign(null)}
        okText="Attribuer" cancelText="Annuler">
        <Form form={form} layout="vertical">
          <Form.Item name="phone" label="Téléphone" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="name" label="Nom" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="cno" label="N° de pièce d'identité"><Input /></Form.Item>
          <Form.Item name="carNo" label="Immatriculation"><Input /></Form.Item>
          <Form.Item name="password" label="Mot de passe de la carte"><Input.Password autoComplete="new-password" /></Form.Item>
        </Form>
      </Modal>
    </>
  )
}
