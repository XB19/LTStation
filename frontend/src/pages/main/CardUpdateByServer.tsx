import { useState } from 'react'
import { Button, Card, Checkbox, Col, Divider, Form, Input, InputNumber, Row, Select, Space, message } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { get, put } from '../../api/http'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import { fmtDateTime } from '../../utils/format'

/** Modification de carte par le serveur (Main/CardUpdateByServer d'origine). */
export default function CardUpdateByServer() {
  const [search] = Form.useForm()
  const [form] = Form.useForm()
  const [cards, setCards] = useState<string[]>([])
  const [asn, setAsn] = useState<string>()
  const limitTypeId = Form.useWatch('limitTypeId', form)

  const find = async () => {
    const list = await get<string[]>('/card-server/search', search.getFieldsValue())
    setCards(list)
    if (list.length === 0) message.info('Aucune carte trouvée')
  }
  const load = async (a: string) => {
    setAsn(a)
    const d = await get<Record<string, unknown>>(`/card-server/${a}`)
    form.setFieldsValue({ ...d, limitOil: d.limitOil ? Number(d.limitOil) : undefined, validDatetime: fmtDateTime(d.validDatetime) })
  }
  const save = async () => {
    const v = await form.validateFields()
    await put(`/card-server/${asn}`, { ...v, limitOil: v.limitOil == null ? null : String(v.limitOil) })
    message.success('Opération réussie')
  }
  const num = (name: string, label: string) => (
    <Col xs={24} md={8}>
      <Form.Item name={name} label={label}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
    </Col>
  )

  return (
    <>
      <PageHeader title="Modification de carte" section="Opérations sur cartes" />
      <Card style={{ marginBottom: 16 }}>
        <Form form={search} layout="inline" style={{ rowGap: 8 }}>
          <Form.Item name="userName" label="Nom"><Input allowClear /></Form.Item>
          <Form.Item name="phone" label="Téléphone"><Input allowClear /></Form.Item>
          <Form.Item name="carNumber" label="Immatriculation"><Input allowClear /></Form.Item>
          <Space>
            <Button type="primary" icon={<SearchOutlined />} onClick={find}>Rechercher</Button>
            <Select style={{ width: 260 }} placeholder="Choisir une carte" value={asn} onChange={load}
              options={cards.map((c) => ({ value: c, label: c }))} />
          </Space>
        </Form>
      </Card>
      {asn && (
        <Card title={`Carte ${asn}`} extra={<Button type="primary" onClick={save}>Enregistrer</Button>}>
          <Form form={form} layout="vertical">
            <Row gutter={16}>
              <Col xs={24} md={8}><Form.Item name="asn" label="N° de carte"><Input disabled /></Form.Item></Col>
              <Col xs={24} md={8}><Form.Item name="validDatetime" label="Validité"><Input disabled /></Form.Item></Col>
              <Col xs={24} md={8}><Form.Item name="accountNo" label="Compte"><Input disabled /></Form.Item></Col>
              <Col xs={24} md={8}>
                <Form.Item name="password" label="Mot de passe de la carte" extra="Laisser vide pour ne pas le changer">
                  <Input.Password autoComplete="new-password" />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}><Form.Item name="name" label="Nom du porteur" rules={[{ required: true }]}><Input /></Form.Item></Col>
              <Col xs={24} md={8}><Form.Item name="code" label="N° du porteur"><Input disabled /></Form.Item></Col>
              <Col xs={24} md={8}>
                <Form.Item name="certificateTypeId" label="Type de pièce" rules={[{ required: true }]}>
                  <RemoteSelect type="CertificateType" style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}><Form.Item name="certificateNo" label="N° de pièce" rules={[{ required: true }]}><Input /></Form.Item></Col>
              <Col xs={24} md={8}><Form.Item name="telephone" label="Téléphone" rules={[{ required: true }]}><Input /></Form.Item></Col>
              <Col xs={24} md={8}><Form.Item name="postCode" label="Code postal"><Input /></Form.Item></Col>
              <Col xs={24} md={8}><Form.Item name="email" label="E-mail"><Input /></Form.Item></Col>
              <Col xs={24} md={8}><Form.Item name="address" label="Adresse"><Input /></Form.Item></Col>
            </Row>
            <Divider orientation="left">Remise et véhicule</Divider>
            <Row gutter={16}>
              <Col xs={24} md={8}>
                <Form.Item name="discountId" label="Remise"><RemoteSelect type="CardDisCount" style={{ width: '100%' }} /></Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="isLimitVehicleNo" valuePropName="checked" label=" ">
                  <Checkbox>Limiter à une immatriculation</Checkbox>
                </Form.Item>
              </Col>
              <Col xs={24} md={8}><Form.Item name="limitVehicleNo" label="Immatriculation autorisée"><Input /></Form.Item></Col>
              <Col xs={24} md={8}>
                <Form.Item name="vehicleColorId" label="Couleur de plaque"><RemoteSelect type="CarColor" style={{ width: '100%' }} /></Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="vehicleTypeId" label="Type de véhicule"><RemoteSelect type="CarType" style={{ width: '100%' }} /></Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="limitOil" label="Énergie autorisée"><RemoteSelect type="OilType" style={{ width: '100%' }} /></Form.Item>
              </Col>
            </Row>
            <Divider orientation="left">Plafonds</Divider>
            <Row gutter={16}>
              {num('limitVolume', 'Litres par plein')}
              {num('limitTime', 'Pleins par jour')}
              {num('limitAmount', 'Montant par jour')}
              {num('limitVolumeMonthly', 'Litres par mois')}
              {num('limitAmountMonthly', 'Montant par mois')}
              {num('limitMileage', 'Consommation aux 100 km')}
              {num('mileageTolerance', 'Tolérance de kilométrage')}
            </Row>
            <Divider orientation="left">Lieux autorisés</Divider>
            <Row gutter={16}>
              <Col xs={24} md={8}>
                <Form.Item name="limitTypeId" label="Limite par">
                  <Select allowClear options={[
                    { value: 0, label: 'Aucune limite' }, { value: 1, label: 'Région' }, { value: 2, label: 'Ville' }, { value: 3, label: 'Station' },
                  ]} onChange={() => form.setFieldValue('useConfine', [])} />
                </Form.Item>
              </Col>
              <Col xs={24} md={16}>
                <Form.Item name="useConfine" label="Lieux">
                  <RemoteSelect type="LimitTypeValue" mode="multiple" params={{ limitType: limitTypeId }}
                    enabled={!!limitTypeId} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
          </Form>
        </Card>
      )}
    </>
  )
}
