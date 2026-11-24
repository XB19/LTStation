import { Card, Form, Input, Select } from 'antd'
import PageHeader from '../../components/PageHeader'
import RemoteSelect from '../../components/RemoteSelect'
import SearchTable from '../../components/SearchTable'
import { fmtDateTime } from '../../utils/format'

type Row = Record<string, unknown>

export const SEARCH_TYPES = [
  { value: 0, label: 'Utilisateurs' },
  { value: 1, label: 'Employés' },
  { value: 2, label: 'Groupes' },
  { value: 3, label: 'Application' },
]

/** Liste des utilisateurs (Main/CustomerList d'origine). */
export default function CustomerList() {
  return (
    <>
      <PageHeader title="Liste des utilisateurs" section="Consultation des enregistrements" />
      <Card>
        <SearchTable<Row>
          url="/customers"
          exportUrl="/customers/export"
          rowKey={(r) => String(r.asn)}
          initialFilters={{ searchType: 0 }}
          filters={
            <>
              <Form.Item name="searchType" label="Type de recherche"><Select style={{ width: 150 }} options={SEARCH_TYPES} /></Form.Item>
              <Form.Item name="userName" label="Nom"><Input allowClear /></Form.Item>
              <Form.Item name="phone" label="Téléphone"><Input allowClear /></Form.Item>
              <Form.Item name="certificateNo" label="N° de pièce"><Input allowClear /></Form.Item>
              <Form.Item name="carNumber" label="Immatriculation"><RemoteSelect type="CardNumSearch" textAsValue /></Form.Item>
              <Form.Item name="cardNo" label="N° de carte"><Input allowClear /></Form.Item>
            </>
          }
          columns={[
            { title: 'N° de carte', dataIndex: 'asn' },
            { title: 'Nom', dataIndex: 'customerName' },
            { title: 'ID utilisateur', dataIndex: 'cusId' },
            { title: 'Groupe', dataIndex: 'groupName' },
            { title: 'Immatriculation', dataIndex: 'carNo' },
            { title: 'Téléphone', dataIndex: 'phone' },
            { title: 'N° de pièce', dataIndex: 'cno' },
            { title: 'Émission', dataIndex: 'issueDatetime', render: fmtDateTime },
            { title: 'Validité', dataIndex: 'validDatetime', render: fmtDateTime },
            { title: 'État de la carte', dataIndex: 'cardStatue' },
            { title: "Station d'ouverture", dataIndex: 'stationName' },
            { title: 'Opérateur', dataIndex: 'operateName' },
            { title: 'Solde', dataIndex: 'balance' },
          ]}
        />
      </Card>
    </>
  )
}
