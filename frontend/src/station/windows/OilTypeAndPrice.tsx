import { useCallback, useEffect, useState } from 'react'
import { DatePicker, InputNumber, Modal, Space, message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { del, get, post, put } from '../../api/http'
import WinForm, { getLayout, useFields } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { money } from '../labels'

type Row = Record<string, unknown>
const L = getLayout('OilTypeAndPrice')

/** Liste des énergies et prix (OilTypeAndPrice) : Ajouter un prix, Supprimer l'énergie, Valider ; double-clic pour modifier le prix ou la date d'effet. */
export default function OilTypeAndPrice({ onClose }: { onClose: () => void }) {
  const f = useFields()
  const [rows, setRows] = useState<Row[]>([])
  const [sel, setSel] = useState<Row | null>(null)
  const [create, setCreate] = useState(false)
  const [edit, setEdit] = useState<{ row: Row; price: number; time: Dayjs } | null>(null)
  const load = useCallback(() => get<Row[]>('/station/setup/grades').then(setRows), [])
  useEffect(() => {
    load()
  }, [load])

  return (
    <Win layout={L} onClose={onClose}>
      <WinForm layout={L} values={f.values} onChange={f.set}
        hidden={['skinLabel1']}
        buttons={{
          Add: () => setCreate(true),
          DeleteButton: () => {
            if (!sel) return message.warning('Choisissez une énergie dans la liste')
            Modal.confirm({
              title: 'Supprimer cette énergie ?', content: String(sel.name), okText: 'Supprimer', okType: 'danger', cancelText: 'Annuler',
              onOk: async () => { await del(`/station/setup/grades/${sel.gradeId}`); setSel(null); load() },
            })
          },
          NextButton: onClose,
        }}
        texts={{ NextButton: 'Valider' }}
        grids={{
          OilPriceView: {
            rows, rowKey: 'gradeId', selectedKey: sel?.gradeId,
            onClick: setSel,
            onDoubleClick: (r) => setEdit({ row: r, price: Number(r.price), time: r.validateTime ? dayjs(r.validateTime as string) : dayjs() }),
            value: { ID: (r) => r.code, OilType: (r) => r.name, OilPrice: (r) => money(r.price, 3), ValidTime: (r) => (r.validateTime ? dayjs(r.validateTime as string).format('YYYY-MM-DD HH:mm:ss') : '') },
          },
        }} />
      {create && <CreateOilPrice onClose={() => { setCreate(false); load() }} />}
      <Modal title={`Modifier le prix — ${String(edit?.row.name ?? '')}`} open={!!edit} onCancel={() => setEdit(null)} okText="Enregistrer" cancelText="Annuler"
        onOk={async () => {
          await put(`/station/setup/grades/${edit!.row.gradeId}`, { price: edit!.price, validTime: edit!.time.format('YYYY-MM-DDTHH:mm:ss') })
          message.success('Prix enregistré')
          setEdit(null)
          load()
        }}>
        {edit && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <span>Prix</span>
            <InputNumber min={0.001} style={{ width: '100%' }} value={edit.price} onChange={(v) => setEdit({ ...edit, price: Number(v) })} />
            <span>Date d'effet</span>
            <DatePicker showTime style={{ width: '100%' }} value={edit.time} onChange={(v) => v && setEdit({ ...edit, time: v })} allowClear={false} />
          </Space>
        )}
      </Modal>
    </Win>
  )
}

const LP = getLayout('CreateOilPrice')

/** Nouveau prix (CreateOilPrice) : énergie, code, nom, prix, densité, date d'effet ; « Ajouter » crée une énergie. */
export function CreateOilPrice({ onClose }: { onClose: () => void }) {
  const f = useFields({ ValidTime: dayjs(), DensityT: '0' })
  const [grades, setGrades] = useState<Row[]>([])
  const [newType, setNewType] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const loadGrades = useCallback(() => get<Row[]>('/station/setup/all-grades').then(setGrades), [])
  useEffect(() => {
    loadGrades()
  }, [loadGrades])
  return (
    <Win layout={LP} onClose={onClose}>
      <WinForm layout={LP} values={f.values} busy={busy}
        onChange={(n, v) => {
          f.set(n, v)
          if (n === 'ID') {
            const g = grades.find((x) => x.gradeId === v)
            f.patch({ ID: v, Code: g?.code ?? '', OilTypeName: g?.name ?? '' })
          }
        }}
        hidden={['label6']}
        readOnly={['Code', 'OilTypeName']}
        options={{ ID: grades.map((g) => ({ value: g.gradeId as number, label: String(g.name) })) }}
        buttons={{
          AddOiLType: () => setNewType(true),
          CanelButton: onClose,
          SaveButton: async () => {
            const v = f.values
            if (!v.ID || !v.OilPrice || v.DensityT === '' || v.DensityT === undefined) return message.error('Informations manquantes')
            if (!/^-?\d+(\.\d+)?$/.test(String(v.OilPrice)) || !/^-?\d+(\.\d+)?$/.test(String(v.DensityT))) return message.error('Format du prix incorrect')
            setBusy('SaveButton')
            try {
              await post('/station/setup/grades', { gradeId: v.ID, price: Number(v.OilPrice), density: Number(v.DensityT), validTime: (v.ValidTime as Dayjs).format('YYYY-MM-DDTHH:mm:ss') })
              message.success('Prix enregistré')
              onClose()
            } finally {
              setBusy(null)
            }
          },
        }} />
      {newType && <CreateOilType onClose={() => { setNewType(false); loadGrades() }} />}
    </Win>
  )
}

const LT = getLayout('CreateOilType')

/** Nouvelle énergie (CreateOilType). */
export function CreateOilType({ onClose }: { onClose: () => void }) {
  const f = useFields()
  useEffect(() => {
    get<{ code: string }>('/station/setup/fuel-types/next-code').then((r) => f.set('OilCode', r.code))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return (
    <Win layout={LT} onClose={onClose}>
      <WinForm layout={LT} values={f.values} onChange={f.set}
        buttons={{
          CanelButton: onClose,
          SaveButton: async () => {
            if (!f.values.OilCode || !f.values.OilName) return message.error('Informations manquantes')
            await post('/station/setup/fuel-types', { code: f.values.OilCode, name: f.values.OilName })
            message.success('Énergie créée')
            onClose()
          },
        }} />
    </Win>
  )
}
