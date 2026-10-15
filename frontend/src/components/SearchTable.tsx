import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Button, Form, Space, Table, type FormInstance } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { DownloadOutlined, SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { get } from '../api/http'

export interface SearchResult<T> {
  rows: T[]
  total: number
  [k: string]: unknown
}

interface Props<T extends object> {
  url: string
  rowKey: string | ((r: T) => string)
  columns: ColumnsType<T>
  /** Champs de recherche (Form.Item) */
  filters?: ReactNode
  initialFilters?: Record<string, unknown>
  /** Transforme les valeurs du formulaire en paramètres de requête */
  toParams?: (values: Record<string, unknown>) => Record<string, unknown>
  /** Affichage des totaux renvoyés par le serveur */
  summary?: (result: SearchResult<T>) => ReactNode
  /** URL d'export Excel (mêmes paramètres) */
  exportUrl?: string
  pageSize?: number
  /** Incrémenter pour recharger */
  reloadKey?: unknown
  form?: FormInstance
  extraButtons?: ReactNode
  /** Charger dès l'affichage (sinon seulement après « Rechercher ») */
  autoLoad?: boolean
}

/** Convertit les dayjs du formulaire en chaînes ISO pour l'API. */
export function serializeFilters(values: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  Object.entries(values).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return
    if (dayjs.isDayjs(v)) out[k] = v.format('YYYY-MM-DDTHH:mm:ss')
    else out[k] = v
  })
  return out
}

/** Formulaire de recherche + tableau paginé côté serveur. */
export default function SearchTable<T extends object>(p: Props<T>) {
  const [ownForm] = Form.useForm()
  const form = p.form ?? ownForm
  const [result, setResult] = useState<SearchResult<T>>({ rows: [], total: 0 })
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(p.pageSize ?? 10)
  const [armed, setArmed] = useState(p.autoLoad ?? true)
  const [params, setParams] = useState<Record<string, unknown>>(() =>
    (p.toParams ?? serializeFilters)(p.initialFilters ?? {}),
  )
  const { url } = p

  const load = useCallback(async () => {
    if (!armed) return
    setLoading(true)
    try {
      setResult(await get<SearchResult<T>>(url, { ...params, page, size }))
    } finally {
      setLoading(false)
    }
  }, [url, params, page, size, armed])

  useEffect(() => {
    load()
  }, [load, p.reloadKey])

  const search = () => {
    const values = form.getFieldsValue()
    setParams((p.toParams ?? serializeFilters)(values))
    setPage(1)
    setArmed(true)
  }

  const doExport = () => {
    const qs = new URLSearchParams(
      Object.entries(params).map(([k, v]) => [k, String(v)] as [string, string]),
    ).toString()
    window.open(`/api${p.exportUrl}${qs ? `?${qs}` : ''}`, '_blank')
  }

  return (
    <>
      {p.filters && (
        <Form form={form} layout="inline" initialValues={p.initialFilters} onFinish={search} style={{ marginBottom: 16, rowGap: 8 }}>
          {p.filters}
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                Rechercher
              </Button>
              {p.exportUrl && (
                <Button icon={<DownloadOutlined />} onClick={doExport}>
                  Exporter
                </Button>
              )}
              {p.extraButtons}
            </Space>
          </Form.Item>
        </Form>
      )}
      {p.summary && <div style={{ marginBottom: 12 }}>{p.summary(result)}</div>}
      <Table<T>
        size="middle"
        rowKey={p.rowKey}
        loading={loading}
        dataSource={result.rows}
        columns={p.columns}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: page,
          pageSize: size,
          total: result.total,
          showSizeChanger: true,
          showTotal: (t) => `${t} enregistrement(s)`,
          onChange: (pg, sz) => {
            setPage(pg)
            setSize(sz)
          },
        }}
      />
    </>
  )
}
