import { useState, type ReactNode } from 'react'
import { Alert, Button, Modal, Result, Space, Steps, Table, Tag, Typography, Upload } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { UploadFile } from 'antd/es/upload/interface'
import { DownloadOutlined, InboxOutlined } from '@ant-design/icons'
import { http } from '../api/http'

type Row = Record<string, unknown> & { line: number; action: string; errors: string[] }

interface ImportResult {
  rows: Row[]
  total: number
  errorCount: number
  dryRun: boolean
  imported: number
}

interface Props {
  open: boolean
  onClose: () => void
  /** Appelé après un import réussi (pour recharger la liste) */
  onDone?: () => void
  title: string
  /** Modèle Excel à télécharger (GET) */
  templateUrl: string
  /** Import (POST multipart, paramètre dryRun) */
  importUrl: string
  /** Colonnes du fichier à afficher dans l'aperçu */
  columns: ColumnsType<Row>
  /** Champs supplémentaires envoyés avec le fichier (ex. droits) */
  extraFields?: () => Record<string, string[] | string | undefined>
  /** Réglages affichés au-dessus du dépôt de fichier */
  settings?: ReactNode
  help?: ReactNode
}

/**
 * Import Excel en trois étapes : modèle et dépôt du fichier, contrôle ligne par ligne (rien n'est enregistré),
 * puis import (tout ou rien).
 */
export default function ExcelImportModal(p: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [check, setCheck] = useState<ImportResult | null>(null)
  const [done, setDone] = useState<ImportResult | null>(null)
  const [busy, setBusy] = useState(false)

  const reset = () => {
    setFile(null)
    setCheck(null)
    setDone(null)
  }
  const close = () => {
    reset()
    p.onClose()
  }

  const send = async (dryRun: boolean) => {
    if (!file) return
    const fd = new FormData()
    fd.append('file', file)
    Object.entries(p.extraFields?.() ?? {}).forEach(([k, v]) => {
      if (Array.isArray(v)) v.forEach((x) => fd.append(k, x))
      else if (v !== undefined) fd.append(k, v)
    })
    setBusy(true)
    try {
      const r = (await http.post<ImportResult>(p.importUrl, fd, { params: { dryRun }, headers: { 'Content-Type': 'multipart/form-data' } })).data
      if (dryRun || r.errorCount > 0) setCheck(r)
      else {
        setDone(r)
        p.onDone?.()
      }
    } finally {
      setBusy(false)
    }
  }

  const step = done ? 2 : check ? 1 : 0
  const columns: ColumnsType<Row> = [
    { title: 'Ligne', dataIndex: 'line', width: 70, fixed: 'left' },
    {
      title: 'Résultat', key: 'status', width: 120, fixed: 'left',
      render: (_, r) => (r.errors.length ? <Tag color="error">Erreur</Tag> : <Tag color="success">{r.action}</Tag>),
    },
    ...p.columns,
    {
      title: 'Erreurs', dataIndex: 'errors',
      render: (e: string[]) => e.map((x) => <div key={x}><Typography.Text type="danger">{x}</Typography.Text></div>),
    },
  ]

  const files: UploadFile[] = file ? [{ uid: '1', name: file.name, status: 'done' }] : []

  return (
    <Modal title={p.title} open={p.open} onCancel={close} width={1100} destroyOnClose
      footer={
        done ? <Button type="primary" onClick={close}>Fermer</Button> : (
          <Space>
            <Button onClick={close}>Annuler</Button>
            {check && <Button onClick={() => setCheck(null)}>Changer de fichier</Button>}
            {!check && <Button type="primary" disabled={!file} loading={busy} onClick={() => send(true)}>Vérifier le fichier</Button>}
            {check && (
              <Button type="primary" disabled={check.errorCount > 0} loading={busy} onClick={() => send(false)}>
                Importer {check.total} ligne{check.total > 1 ? 's' : ''}
              </Button>
            )}
          </Space>
        )
      }>
      <Steps size="small" current={step} style={{ marginBottom: 20 }}
        items={[{ title: 'Fichier' }, { title: 'Contrôle' }, { title: 'Import' }]} />
      {step === 0 && (
        <>
          {p.help && <Alert type="info" showIcon style={{ marginBottom: 16 }} message={p.help} />}
          <Space style={{ marginBottom: 16 }}>
            <Button icon={<DownloadOutlined />} onClick={() => window.open(`/api${p.templateUrl}`, '_blank')}>
              Télécharger le modèle Excel
            </Button>
          </Space>
          {p.settings}
          <Upload.Dragger accept=".xlsx,.xls" maxCount={1} fileList={files}
            beforeUpload={(f) => { setFile(f); return false }} onRemove={() => { setFile(null) }}>
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p className="ant-upload-text">Cliquez ou déposez le fichier Excel ici</p>
            <p className="ant-upload-hint">Fichier .xlsx ou .xls, première feuille, en-têtes du modèle sur la première ligne</p>
          </Upload.Dragger>
        </>
      )}
      {step === 1 && check && (
        <>
          {check.errorCount > 0 ? (
            <Alert type="error" showIcon style={{ marginBottom: 12 }}
              message={`${check.errorCount} ligne(s) en erreur sur ${check.total} : corrigez le fichier puis vérifiez-le à nouveau. Rien n'a été enregistré.`} />
          ) : (
            <Alert type="success" showIcon style={{ marginBottom: 12 }}
              message={`${check.total} ligne(s) prête(s) à importer. Rien n'est encore enregistré : cliquez sur « Importer » pour confirmer.`} />
          )}
          <Table<Row> size="small" rowKey="line" dataSource={check.rows} columns={columns} scroll={{ x: 'max-content', y: 420 }}
            pagination={{ pageSize: 50, hideOnSinglePage: true }}
            rowClassName={(r) => (r.errors.length ? 'lt-row-error' : '')} />
        </>
      )}
      {step === 2 && done && (
        <Result status="success" title={`${done.imported} ligne(s) importée(s)`}
          subTitle="Les enregistrements apparaissent maintenant dans la liste." />
      )}
    </Modal>
  )
}
