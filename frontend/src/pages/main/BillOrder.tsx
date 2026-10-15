import { useCallback, useState } from 'react'
import { Button, Card, Modal, Tag, Tooltip, message } from 'antd'
import { EditOutlined } from '@ant-design/icons'
import { get, put } from '../../api/http'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'
import { fmtDateTime, fmtNum } from '../../utils/format'

type Row = Record<string, unknown>

const STATUS = [
  { label: 'Non émise', color: 'red' },
  { label: 'Émise', color: 'blue' },
  { label: 'Refusée', color: 'default' },
]

/** Demandes de facture (Main/BillOrder d'origine). */
export default function BillOrder() {
  const load = useCallback(() => get<Row[]>('/tax-bills'), [])
  const [edit, setEdit] = useState<Row | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const decide = async (statue: number) => {
    await put(`/tax-bills/${edit!.id}`, { statue })
    message.success('Opération réussie')
    setEdit(null)
    setReloadKey((k) => k + 1)
  }
  return (
    <>
      <PageHeader title="Factures" section="Factures" />
      <Card>
        <CrudTable<Row>
          rowKey="id"
          load={load}
          reloadKey={reloadKey}
          columns={[
            { title: 'ID', dataIndex: 'id' },
            { title: 'État', dataIndex: 'statue', render: (v: number) => <Tag color={STATUS[v]?.color}>{STATUS[v]?.label}</Tag> },
            { title: 'Création', dataIndex: 'createTime', render: fmtDateTime },
            { title: 'Société', dataIndex: 'companyName' },
            { title: 'N° fiscal', dataIndex: 'companyTax' },
            { title: 'Montant', dataIndex: 'amount', render: (v) => fmtNum(v) },
            { title: 'E-mail', dataIndex: 'payForEmail' },
            { title: 'Traitement', dataIndex: 'checkTime', render: fmtDateTime },
          ]}
          extraActions={(r) => (
            <Tooltip title="Traiter">
              <Button size="small" type="text" icon={<EditOutlined />} onClick={() => setEdit(r)} />
            </Tooltip>
          )}
        />
      </Card>
      <Modal title="Traitement de la facture" open={!!edit} onCancel={() => setEdit(null)} footer={[
        <Button key="d" danger onClick={() => decide(2)}>Refuser</Button>,
        <Button key="g" type="primary" onClick={() => decide(1)}>Marquer comme émise</Button>,
      ]}>
        <p>Facture n° {String(edit?.id ?? '')} — {String(edit?.companyName ?? '')} — {fmtNum(edit?.amount)}</p>
      </Modal>
    </>
  )
}
