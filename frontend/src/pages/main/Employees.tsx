import { useCallback, useState } from 'react'
import { Card, Checkbox, Col, Form, Input, Row, Tabs } from 'antd'
import { del, get, post, put } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import StationFilter, { type StationFilterValue } from '../../components/StationFilter'

type Row = Record<string, unknown>

function EmployeeFormFields({ mode }: { mode: 'create' | 'edit' }) {
  const { me } = useAuth()
  const form = Form.useFormInstance()
  const isSystem = me?.userId === -1
  const myPc = new Set(me?.auth ?? [])
  const myPhone = new Set((me?.gzhAuth ?? '').split(',').filter(Boolean))
  const pcOptions = (me?.pcMenus ?? [])
    .filter((m) => isSystem || myPc.has(String(m.value)))
    .map((m) => ({ value: String(m.value), label: m.label }))
  const phoneOptions = (me?.phoneMenus ?? [])
    .filter((m) => isSystem || myPhone.has(String(m.value)))
    .map((m) => ({ value: String(m.value), label: m.label }))

  const onStation = async (stationId?: number) => {
    if (mode !== 'create' || stationId == null) return
    form.setFieldsValue(await get('/employees/next-codes', { stationId }))
  }

  return (
    <Row gutter={16}>
      <Col span={12}>
        <Form.Item name="stationId" label="Station" rules={[{ required: true }]}>
          <RemoteSelect type="Station" style={{ width: '100%' }} onChange={onStation} />
        </Form.Item>
      </Col>
      {mode === 'create' && (
        <>
          <Col span={6}>
            <Form.Item name="employeeNo" label="N° d'employé">
              <Input readOnly />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item name="codeInner" label="Code interne">
              <Input readOnly />
            </Form.Item>
          </Col>
        </>
      )}
      <Col span={12}>
        <Form.Item name="name" label="Nom" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="loginName" label="Identifiant de connexion" rules={[{ required: true }]}>
          <Input readOnly={mode === 'edit'} autoComplete="off" />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item
          name="password"
          label="Mot de passe de connexion"
          rules={mode === 'create' ? [{ required: true }] : []}
          extra={mode === 'edit' ? 'Laisser vide pour ne pas le changer' : undefined}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item name="phone" label="Téléphone">
          <Input />
        </Form.Item>
      </Col>
      <Col span={24}>
        <Tabs
          items={[
            {
              key: 'pc',
              label: 'Droits ordinateur',
              children: (
                <Form.Item name="pcMenuAuth">
                  <Checkbox.Group style={{ width: '100%' }}>
                    <Row>
                      {pcOptions.map((o) => (
                        <Col span={8} key={o.value}>
                          <Checkbox value={o.value}>{o.label}</Checkbox>
                        </Col>
                      ))}
                    </Row>
                  </Checkbox.Group>
                </Form.Item>
              ),
            },
            {
              key: 'phone',
              label: 'Droits mobile',
              children: (
                <Form.Item name="gzhAuth">
                  <Checkbox.Group style={{ width: '100%' }}>
                    <Row>
                      {phoneOptions.map((o) => (
                        <Col span={8} key={o.value}>
                          <Checkbox value={o.value}>{o.label}</Checkbox>
                        </Col>
                      ))}
                    </Row>
                  </Checkbox.Group>
                </Form.Item>
              ),
            },
          ]}
        />
      </Col>
    </Row>
  )
}

/** Configuration des employés (Main/EmpConfigure d'origine). */
export default function Employees() {
  const [filter, setFilter] = useState<StationFilterValue>({})
  const load = useCallback(() => get<Row[]>('/employees', filter), [filter])
  return (
    <>
      <PageHeader title="Configuration des employés" section="Configuration système" />
      <Card>
        <CrudTable<Row>
          rowKey="employeeId"
          load={load}
          addLabel="Ajouter un employé"
          modalWidth={820}
          toolbar={<StationFilter value={filter} onChange={setFilter} />}
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: 'Nom', dataIndex: 'name' },
            { title: 'Identifiant', dataIndex: 'loginName' },
            { title: 'Téléphone', dataIndex: 'telephone' },
            { title: 'Niveau', dataIndex: 'postName' },
          ]}
          modalTitle={(m) => (m === 'create' ? 'Ajouter un employé' : "Modifier l'employé")}
          createValues={async () => ({ ...(await get('/employees/next-codes')), pcMenuAuth: [], gzhAuth: [] })}
          editValues={(r) => get(`/employees/${r.employeeId}`)}
          renderForm={(mode) => <EmployeeFormFields mode={mode} />}
          onCreate={(v) => post('/employees', v)}
          onUpdate={(r, v) => put(`/employees/${r.employeeId}`, v)}
          onDelete={(r) => del(`/employees/${r.employeeId}`)}
        />
      </Card>
    </>
  )
}
