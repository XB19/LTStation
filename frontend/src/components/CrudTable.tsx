import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Button, Form, Modal, Space, Table, Tooltip, message, type FormInstance } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { confirmDelete } from './confirmDelete'

type Values = Record<string, unknown>

export interface CrudTableProps<T extends object> {
  rowKey: string | ((r: T) => string | number)
  columns: ColumnsType<T>
  load: () => Promise<T[]>
  /** Libellé du bouton d'ajout ; sans onCreate, pas de bouton */
  addLabel?: string
  modalTitle?: (mode: 'create' | 'edit') => string
  /** Champs du formulaire (Form.Item) */
  renderForm?: (mode: 'create' | 'edit', form: FormInstance) => ReactNode
  /** Valeurs initiales du formulaire de création (ex. code suivant) */
  createValues?: () => Promise<Values> | Values
  /** Valeurs du formulaire d'édition à partir de la ligne */
  editValues?: (row: T) => Promise<Values> | Values
  onCreate?: (values: Values) => Promise<unknown>
  onUpdate?: (row: T, values: Values) => Promise<unknown>
  onDelete?: (row: T) => Promise<unknown>
  extraActions?: (row: T, reload: () => void) => ReactNode
  toolbar?: ReactNode
  pageSize?: number
  modalWidth?: number
  /** Incrémenter pour forcer un rechargement depuis le parent */
  reloadKey?: unknown
}

/** Tableau + modale d'ajout/modification + suppression confirmée, utilisé par les écrans de configuration. */
export default function CrudTable<T extends object>(p: CrudTableProps<T>) {
  const [rows, setRows] = useState<T[]>([])
  const [loading, setLoading] = useState(false)
  const [modal, setModal] = useState<{ mode: 'create' | 'edit'; row?: T } | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()
  const { load } = p

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await load())
    } finally {
      setLoading(false)
    }
  }, [load])

  useEffect(() => {
    reload()
  }, [reload, p.reloadKey])

  const open = async (mode: 'create' | 'edit', row?: T) => {
    form.resetFields()
    const init = mode === 'create' ? await p.createValues?.() : row ? await (p.editValues?.(row) ?? (row as Values)) : undefined
    if (init) form.setFieldsValue(init)
    setModal({ mode, row })
  }

  const save = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (modal?.mode === 'create') await p.onCreate?.(values)
      else if (modal?.row) await p.onUpdate?.(modal.row, values)
      message.success('Opération réussie')
      setModal(null)
      reload()
    } finally {
      setSaving(false)
    }
  }

  const hasActions = !!(p.onUpdate || p.onDelete || p.extraActions)
  const columns: ColumnsType<T> = hasActions
    ? [
        ...p.columns,
        {
          title: 'Opération',
          key: '__actions',
          width: 120,
          render: (_: unknown, row: T) => (
            <Space>
              {p.onUpdate && (
                <Tooltip title="Modifier">
                  <Button size="small" type="text" icon={<EditOutlined />} onClick={() => open('edit', row)} />
                </Tooltip>
              )}
              {p.onDelete && (
                <Tooltip title="Supprimer">
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => confirmDelete(() => p.onDelete!(row), reload)}
                  />
                </Tooltip>
              )}
              {p.extraActions?.(row, reload)}
            </Space>
          ),
        },
      ]
    : p.columns

  return (
    <>
      <Space style={{ marginBottom: 12 }}>
        {p.onCreate && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => open('create')}>
            {p.addLabel ?? 'Ajouter'}
          </Button>
        )}
        <Button icon={<ReloadOutlined />} onClick={reload}>
          Actualiser
        </Button>
        {p.toolbar}
      </Space>
      <Table<T>
        size="middle"
        rowKey={p.rowKey}
        loading={loading}
        dataSource={rows}
        columns={columns}
        pagination={{ pageSize: p.pageSize ?? 10, showSizeChanger: true }}
        scroll={{ x: 'max-content' }}
      />
      <Modal
        title={p.modalTitle?.(modal?.mode ?? 'create') ?? (modal?.mode === 'edit' ? 'Modifier' : 'Ajouter')}
        open={modal !== null}
        onOk={save}
        confirmLoading={saving}
        onCancel={() => setModal(null)}
        okText="Enregistrer"
        cancelText="Annuler"
        width={p.modalWidth ?? 560}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          {modal && p.renderForm?.(modal.mode, form)}
        </Form>
      </Modal>
    </>
  )
}
