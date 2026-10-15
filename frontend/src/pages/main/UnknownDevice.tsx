import { useCallback, useState } from 'react'
import { Button, Card, Form, Modal, Tooltip, message } from 'antd'
import { LinkOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { del, get, post } from '../../api/http'
import AirDeviceFormFields from '../../components/AirDeviceFormFields'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'

type Row = Record<string, unknown>

/** Nouveaux appareils : appareils connectés mais non déclarés (Main/UnKnowSeting d'origine). */
export default function UnknownDevice() {
  const load = useCallback(() => get<Row[]>('/air-devices/unknown'), [])
  const [bind, setBind] = useState<Row | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [form] = Form.useForm()

  const save = async () => {
    const v = await form.validateFields()
    await post('/air-devices', v)
    message.success('Opération réussie')
    setBind(null)
    setReloadKey((k) => k + 1)
  }

  return (
    <>
      <PageHeader title="Nouveaux appareils" section="Paramètres de base" />
      <Card>
        <CrudTable<Row>
          rowKey="id"
          load={load}
          reloadKey={reloadKey}
          columns={[
            { title: "N° d'appareil inconnu", dataIndex: 'unKnowDeviceNo' },
            {
              title: "Heure d'enregistrement",
              dataIndex: 'dateTime',
              render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : ''),
            },
          ]}
          onDelete={(r) => del(`/air-devices/unknown/${r.id}`)}
          extraActions={(r) => (
            <Tooltip title="Déclarer l'appareil">
              <Button
                size="small"
                type="text"
                icon={<LinkOutlined />}
                onClick={() => {
                  form.resetFields()
                  form.setFieldsValue({ deviceNo: r.unKnowDeviceNo, unknownId: r.id })
                  setBind(r)
                }}
              />
            </Tooltip>
          )}
        />
      </Card>
      <Modal
        title="Déclarer l'appareil"
        open={bind !== null}
        onOk={save}
        onCancel={() => setBind(null)}
        okText="Enregistrer"
        cancelText="Annuler"
        width={700}
      >
        <Form form={form} layout="vertical">
          <AirDeviceFormFields deviceNoReadOnly />
        </Form>
      </Modal>
    </>
  )
}
