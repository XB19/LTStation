import { Card, DatePicker, Form, Typography } from 'antd'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import PeriodChart from '../../components/PeriodChart'
import SearchTable, { serializeFilters } from '../../components/SearchTable'
import { fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>

/** Historique des recharges (Main/ChargeList d'origine). */
export default function ChargeList() {
  return (
    <>
      <PageHeader title="Recharges" section="Consultation des enregistrements" />
      <PeriodChart title="Graphique des recharges" url="/charges/chart" series={[{ key: 'values', name: 'Recharges' }]} />
      <Card>
        <SearchTable<Row>
          url="/charges"
          rowKey="id"
          pageSize={15}
          initialFilters={{ period: [dayjs().startOf('day'), dayjs()] }}
          toParams={(v) => {
            const p = v.period as [dayjs.Dayjs, dayjs.Dayjs] | undefined
            return serializeFilters({ start: p?.[0], end: p?.[1] })
          }}
          filters={<Form.Item name="period" label="Période"><DatePicker.RangePicker showTime /></Form.Item>}
          summary={(r) => <Typography.Text strong>Montant total des recharges : {fmtNum(r.totalAmount)}</Typography.Text>}
          columns={[
            { title: 'N° de carte', dataIndex: 'asn' },
            { title: 'Immatriculation', dataIndex: 'carNumber' },
            { title: 'Type de recharge', dataIndex: 'chargeTypeName' },
            { title: 'N° de commande', dataIndex: 'orderNo' },
            { title: 'Montant', dataIndex: 'amount', render: (v) => fmtNum(v) },
            { title: 'Date', dataIndex: 'createTime', render: fmtDateTime },
            { title: 'Opérateur', dataIndex: 'operateName' },
          ]}
        />
      </Card>
    </>
  )
}
