import { Card, DatePicker, Form, InputNumber, Typography } from 'antd'
import { useAuth } from '../../auth/AuthContext'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import SearchTable from '../../components/SearchTable'
import { fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>

/** Recharges d'activation d'appareils (Main/JoyFillRenewOrder d'origine). */
export default function RenewOrder() {
  const { me } = useAuth()
  return (
    <>
      <PageHeader title="Recharges d'activation d'appareils" section="Paramètres de base" />
      <Card>
        <SearchTable<Row>
          url="/renew-orders"
          rowKey="id"
          filters={
            <>
              {me?.isCenter && (
                <Form.Item name="stationId" label="Station">
                  <RemoteSelect type="TCPAllStation" />
                </Form.Item>
              )}
              <Form.Item name="gunNumber" label="N° de pistolet">
                <InputNumber min={0} />
              </Form.Item>
              <Form.Item name="start" label="Début">
                <DatePicker showTime />
              </Form.Item>
              <Form.Item name="end" label="Fin">
                <DatePicker showTime />
              </Form.Item>
            </>
          }
          summary={(r) => (
            <Typography.Text strong>Montant payé : {fmtNum(r.totalAmount)}</Typography.Text>
          )}
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: 'N° de pistolet', dataIndex: 'gunNumber' },
            { title: 'Heure de paiement', dataIndex: 'payTime', render: fmtDateTime },
            { title: 'Montant payé', dataIndex: 'amount', render: (v) => fmtNum(v) },
            { title: 'Échéance avant recharge', dataIndex: 'expireDateBefore', render: fmtDateTime },
            { title: 'Échéance après recharge', dataIndex: 'expireDateAfter', render: fmtDateTime },
            { title: 'N° de commande marchand', dataIndex: 'outTradeNo' },
          ]}
        />
      </Card>
    </>
  )
}
