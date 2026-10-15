import { Card, DatePicker, Descriptions, Form, Input } from 'antd'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import SearchTable, { serializeFilters } from '../../components/SearchTable'
import { fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>

/** Historique des utilisateurs (Main/LogSearch d'origine). */
export default function LogSearch() {
  return (
    <>
      <PageHeader title="Historique des utilisateurs" section="Consultation des enregistrements" />
      <Card>
        <SearchTable<Row>
          url="/user-logs"
          exportUrl="/user-logs/export"
          rowKey="id"
          pageSize={15}
          initialFilters={{ period: [dayjs().startOf('month'), dayjs()] }}
          toParams={(v) => {
            const { period, ...rest } = v as { period?: [dayjs.Dayjs, dayjs.Dayjs] }
            return serializeFilters({ ...rest, start: period?.[0], end: period?.[1] })
          }}
          filters={
            <>
              <Form.Item name="period" label="Période" rules={[{ required: true }]}><DatePicker.RangePicker showTime /></Form.Item>
              <Form.Item name="phone" label="Téléphone"><Input allowClear /></Form.Item>
              <Form.Item name="certificateNo" label="N° de pièce"><Input allowClear /></Form.Item>
              <Form.Item name="name" label="Nom"><Input allowClear /></Form.Item>
              <Form.Item name="type" label="Opération"><RemoteSelect type="TransactionType" style={{ width: 200 }} /></Form.Item>
              <Form.Item name="carNumber" label="Immatriculation"><RemoteSelect type="CardNumSearch" textAsValue /></Form.Item>
            </>
          }
          summary={(r) => {
            const t = (r.totals ?? {}) as Record<string, unknown>
            return (
              <Descriptions size="small" bordered column={5} items={[
                { key: 'd', label: 'Total rechargé', children: fmtNum(t.depositAmount, 3) },
                { key: 'p', label: 'Total offert', children: fmtNum(t.disCountAmount, 3) },
                { key: 's', label: 'Total consommé', children: fmtNum(t.salerAmount, 3) },
                { key: 'v', label: 'Volume total', children: fmtNum(t.salerVolume, 3) },
                { key: 'r', label: 'Total annulé', children: fmtNum(t.reBackAmount, 3) },
              ]} />
            )
          }}
          columns={[
            { title: 'Nom', dataIndex: 'userName' },
            { title: 'N° de pièce', dataIndex: 'certificateNo' },
            { title: 'N° de carte', dataIndex: 'asn' },
            { title: 'Station', dataIndex: 'stationName' },
            { title: 'Immatriculation', dataIndex: 'carNumber' },
            { title: 'Opération', dataIndex: 'transactionType' },
            { title: 'Rechargé', dataIndex: 'depositAmount' },
            { title: 'Offert', dataIndex: 'disCountAmount' },
            { title: 'Consommé', dataIndex: 'salerAmount' },
            { title: 'Volume', dataIndex: 'salerVolume' },
            { title: 'Annulé', dataIndex: 'reBackAmount' },
            { title: 'Solde', dataIndex: 'balance' },
            { title: 'Date', dataIndex: 'operateTime', render: fmtDateTime },
            { title: 'Opérateur', dataIndex: 'employeeName' },
          ]}
        />
      </Card>
    </>
  )
}
