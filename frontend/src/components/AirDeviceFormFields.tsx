import { Col, Form, Input, InputNumber, Row, Select } from 'antd'
import RemoteSelect from './RemoteSelect'

export const DEVICE_TYPES = [
  { value: 1, label: '664' },
  { value: 2, label: '886' },
  { value: 3, label: 'Atomiseur' },
]

export const deviceTypeFromDigits = (digitsType?: string) => (digitsType === '886' ? 2 : digitsType === '3' ? 3 : 1)

/** Champs du formulaire d'appareil cloud (Home/AirDeviceChange). */
export default function AirDeviceFormFields({ deviceNoReadOnly }: { deviceNoReadOnly?: boolean }) {
  const form = Form.useFormInstance()
  const stationId = Form.useWatch('stationId', form)
  const digits = <RemoteSelect type="Digits" style={{ width: '100%' }} allowClear={false} />
  return (
    <Row gutter={16}>
      <Col span={12}>
        <Form.Item name="stationId" label="Station" rules={[{ required: true }]}>
          <RemoteSelect type="Station" style={{ width: '100%' }} onChange={() => form.setFieldValue('tankId', undefined)} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="tankId" label="Cuve" rules={[{ required: true }]}>
          <RemoteSelect type="TankSelect" params={{ stationId }} enabled={stationId != null} style={{ width: '100%' }} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="nozId" label="N° de pistolet" rules={[{ required: true }]}>
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="deviceType" label="Type d'appareil" initialValue={1}>
          <Select options={DEVICE_TYPES} />
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item name="amountDigits" label="Décimales montant" initialValue={2}>
          {digits}
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item name="volumeDigits" label="Décimales volume" initialValue={2}>
          {digits}
        </Form.Item>
      </Col>
      <Col span={8}>
        <Form.Item name="priceDigits" label="Décimales prix" initialValue={2}>
          {digits}
        </Form.Item>
      </Col>
      <Col span={24}>
        <Form.Item name="deviceNo" label="N° d'appareil" rules={[{ required: true }]}>
          <Input readOnly={deviceNoReadOnly} />
        </Form.Item>
      </Col>
      <Form.Item name="unknownId" hidden>
        <Input />
      </Form.Item>
    </Row>
  )
}
