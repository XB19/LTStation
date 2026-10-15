import { useCallback, useEffect, useState } from 'react'
import { InputNumber, Modal, Select, Space, message } from 'antd'
import { del, get, post, put } from '../../api/http'
import WinForm, { getLayout, useFields } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { useStation } from '../auth'
import { money } from '../labels'

type Row = Record<string, unknown>

// ------------------------------------------------------------------ Règles de points (ScoreDeal / AddScoreSet)

const LS = getLayout('ScoreDeal')
const LSA = getLayout('AddScoreSet')
const SCORE_TYPES = [{ value: 0, label: 'Litres' }, { value: 1, label: 'Montant' }]

export function ScoreDeal({ onClose }: { onClose: () => void }) {
  const [rows, setRows] = useState<Row[]>([])
  const [add, setAdd] = useState(false)
  const load = useCallback(() => get<Row[]>('/station/misc/score-rules').then(setRows), [])
  useEffect(() => {
    load()
  }, [load])
  return (
    <Win layout={LS} onClose={onClose} title="Gestion des points">
      <WinForm layout={LS} values={{}} onChange={() => undefined} buttons={{ NextButton: () => setAdd(true) }}
        grids={{
          TransactionView: {
            rows, rowKey: 'id',
            onDoubleClick: (r) => Modal.confirm({ title: 'Supprimer cette règle ?', okText: 'Supprimer', okType: 'danger', cancelText: 'Annuler',
              onOk: async () => { await del(`/station/misc/score-rules/${r.id}`); load() } }),
            value: { NozID: (r) => r.id, OilType: (r) => r.oilTypeName, OilPrice: (r) => (Number(r.scoreType) === 1 ? 'Montant' : 'Litres'), Volume: (r) => r.rate },
          },
        }} />
      {add && <AddScoreSet onClose={() => { setAdd(false); load() }} />}
    </Win>
  )
}

function AddScoreSet({ onClose }: { onClose: () => void }) {
  const f = useFields({ ScoreType: 0 })
  const [grades, setGrades] = useState<Row[]>([])
  useEffect(() => {
    get<Row[]>('/station/setup/all-grades').then(setGrades)
  }, [])
  return (
    <Win layout={LSA} onClose={onClose} title="Nouvelle règle de points">
      <WinForm layout={LSA} values={f.values} onChange={f.set}
        options={{ GradeType: grades.map((g) => ({ value: g.gradeId as number, label: String(g.name) })), ScoreType: SCORE_TYPES }}
        buttons={{
          ConfirmBtn: async () => {
            await post('/station/misc/score-rules', { gradeId: f.values.GradeType, scoreType: f.values.ScoreType, rate: Number(f.values.ScoreRate) })
            message.success('Règle ajoutée')
            onClose()
          },
        }} />
    </Win>
  )
}

// ------------------------------------------------------------------ Remises (CenterDisCount / AddDisCountForm)

const LD = getLayout('CenterDisCount')
const LDA = getLayout('AddDisCountForm')

export function CenterDisCount({ onClose }: { onClose: () => void }) {
  const [rows, setRows] = useState<Row[]>([])
  const [edit, setEdit] = useState<Row | 'new' | null>(null)
  const load = useCallback(() => get<Row[]>('/station/misc/discounts').then(setRows), [])
  useEffect(() => {
    load()
  }, [load])
  return (
    <Win layout={LD} onClose={onClose} title="Remises">
      <WinForm layout={LD} values={{}} onChange={() => undefined} buttons={{ AddDisCount: () => setEdit('new') }}
        texts={{ label1: 'Remise en % du prix : pour 90 %, saisissez 90' }}
        grids={{ DisCountView: { rows, rowKey: 'id', onDoubleClick: setEdit, value: { ID: (r) => r.id, OilType: (r) => r.name, Discount: (r) => `${r.disCount} %` } } }} />
      {edit && <AddDisCount row={edit === 'new' ? null : edit} onClose={() => { setEdit(null); load() }} />}
    </Win>
  )
}

function AddDisCount({ row, onClose }: { row: Row | null; onClose: () => void }) {
  const f = useFields(row ? { DisCountID: row.id, DisCountName: row.name, DisCountValue: String(row.disCount) } : {})
  return (
    <Win layout={LDA} onClose={onClose} title={row ? 'Modifier la remise' : 'Nouvelle remise'}>
      <WinForm layout={LDA} values={f.values} onChange={f.set} readOnly={['DisCountID']} hidden={row ? [] : ['DeleteButton']}
        buttons={{
          SaveButton: async () => {
            const body = { name: f.values.DisCountName, value: Number(f.values.DisCountValue) }
            if (row) await put(`/station/misc/discounts/${row.id}`, body)
            else await post('/station/misc/discounts', body)
            message.success('Remise enregistrée')
            onClose()
          },
          DeleteButton: async () => { await del(`/station/misc/discounts/${row!.id}`); onClose() },
        }} />
    </Win>
  )
}

// ------------------------------------------------------------------ Informations de la station (BaseMessage)

const LBM = getLayout('BaseMessage')

export function BaseMessage({ onClose }: { onClose: () => void }) {
  const { refresh } = useStation()
  const f = useFields()
  useEffect(() => {
    get<Row>('/station/misc/station').then((s) => f.patch({
      StationCode: s.code, RegionSelect: 1, CitySelect: 1, StationName: s.name, StationAddress: s.address, StationManager: s.manager, Phone: s.phone, Email: s.email,
      regionLabel: s.regionDesc, cityLabel: s.cityDesc,
    }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  const v = f.values
  return (
    <Win layout={LBM} onClose={onClose} title="Informations de la station">
      <WinForm layout={LBM} values={v} onChange={f.set} readOnly={['StationCode']} disabled={['RegionSelect', 'CitySelect']}
        options={{ RegionSelect: [{ value: 1, label: String(v.regionLabel ?? '') }], CitySelect: [{ value: 1, label: String(v.cityLabel ?? '') }] }}
        hidden={['CreateStation']} texts={{ NextButton: 'Enregistrer', skinCaptionPanel1: 'Informations de la station' }}
        buttons={{
          NextButton: async () => {
            await put('/station/misc/station', { name: v.StationName, address: v.StationAddress, manager: v.StationManager, phone: v.Phone, email: v.Email })
            message.success('Enregistré')
            refresh()
            onClose()
          },
        }} />
    </Win>
  )
}

// ------------------------------------------------------------------ Pistolets (StationNozzleSet)

const LN = getLayout('StationNozzleSet')

export function StationNozzleSet({ onClose }: { onClose: () => void }) {
  const [rows, setRows] = useState<Row[]>([])
  const [grades, setGrades] = useState<Row[]>([])
  const [sel, setSel] = useState<Row | null>(null)
  const [edit, setEdit] = useState<{ id?: number; nozId?: number; gradeId?: number } | null>(null)
  const load = useCallback(async () => {
    setRows(await get<Row[]>('/station/setup/nozzles'))
    setGrades(await get<Row[]>('/station/setup/grades'))
  }, [])
  useEffect(() => {
    load()
  }, [load])
  return (
    <Win layout={LN} onClose={onClose} title="Configuration des pistolets">
      <WinForm layout={LN} values={{}} onChange={() => undefined}
        texts={{ label1: 'Double-cliquez sur un pistolet pour le modifier', NextButton: 'Valider' }}
        buttons={{
          Add: () => setEdit({}),
          Delete: () => {
            if (!sel) return message.warning('Choisissez un pistolet')
            Modal.confirm({ title: `Supprimer le pistolet ${sel.nozId} ?`, okText: 'Supprimer', okType: 'danger', cancelText: 'Annuler',
              onOk: async () => { await del(`/station/setup/nozzles/${sel.id}`); setSel(null); load() } })
          },
          NextButton: onClose,
        }}
        grids={{
          NozzleConfigView: {
            rows, rowKey: 'id', selectedKey: sel?.id, onClick: setSel, onDoubleClick: (r) => setEdit({ id: r.id as number, nozId: r.nozId as number, gradeId: r.gradeId as number }),
            hideColumns: ['TankType', 'PosType', 'PosIP'],
            headers: { OilType: 'Énergie' },
            value: { ID: (r) => r.nozId, OilType: (r) => r.gradeName, Type: (r) => r.gradeName },
          },
          TankView: {
            rows: grades, rowKey: 'gradeId',
            hideColumns: ['dataGridViewTextBoxColumn1'],
            headers: { dataGridViewComboBoxColumn1: 'Énergie', dataGridViewComboBoxColumn2: 'Prix' },
            value: { dataGridViewComboBoxColumn1: (r) => r.name, dataGridViewComboBoxColumn2: (r) => money(r.price, 3) },
          },
        }} />
      <Modal title={edit?.id ? 'Modifier le pistolet' : 'Nouveau pistolet'} open={!!edit} onCancel={() => setEdit(null)} okText="Enregistrer" cancelText="Annuler"
        onOk={async () => {
          const body = { nozId: edit?.nozId, gradeId: edit?.gradeId }
          if (edit?.id) await put(`/station/setup/nozzles/${edit.id}`, body)
          else await post('/station/setup/nozzles', body)
          setEdit(null)
          load()
        }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <span>N° du pistolet</span>
          <InputNumber min={1} style={{ width: '100%' }} value={edit?.nozId} onChange={(x) => setEdit({ ...edit, nozId: Number(x) })} />
          <span>Énergie</span>
          <Select style={{ width: '100%' }} value={edit?.gradeId} onChange={(x) => setEdit({ ...edit, gradeId: x })}
            options={grades.map((g) => ({ value: g.gradeId as number, label: `${g.name} (${money(g.price, 3)})` }))} />
        </Space>
      </Modal>
    </Win>
  )
}

// ------------------------------------------------------------------ Postes (ShiftItemForm)

const LSI = getLayout('ShiftItemForm')
const HOURS = Array.from({ length: 25 }, (_, h) => ({ value: h, label: `${String(h).padStart(2, '0')}:00` }))

export function ShiftItemForm({ onClose }: { onClose: () => void }) {
  const [rows, setRows] = useState<Row[]>([])
  const [sel, setSel] = useState<Row | null>(null)
  const [edit, setEdit] = useState<Row | null>(null)
  const load = useCallback(() => get<Row[]>('/station/setup/shifts').then(setRows), [])
  useEffect(() => {
    load()
  }, [load])
  return (
    <Win layout={LSI} onClose={onClose} title="Configuration des postes">
      <WinForm layout={LSI} values={{}} onChange={() => undefined}
        buttons={{
          AddShift: () => setEdit({ name: '', startHour: 6, endHour: 14, scope: 0 }),
          Delete: () => {
            if (!sel) return message.warning('Choisissez un poste')
            Modal.confirm({ title: `Supprimer le poste ${sel.name} ?`, okText: 'Supprimer', okType: 'danger', cancelText: 'Annuler',
              onOk: async () => { await del(`/station/setup/shifts/${sel.id}`); setSel(null); load() } })
          },
        }}
        grids={{
          ShiftItemView: {
            rows, rowKey: 'id', selectedKey: sel?.id, onClick: setSel, onDoubleClick: setEdit,
            value: { ID: (r) => rows.indexOf(r) + 1, OilType: (r) => r.name, OilPrice: (r) => HOURS[Number(r.startHour)]?.label, EndTime: (r) => HOURS[Number(r.endHour)]?.label,
              Scope: (r) => r.scope },
          },
        }} />
      <Modal title={edit?.id ? 'Modifier le poste' : 'Nouveau poste'} open={!!edit} onCancel={() => setEdit(null)} okText="Enregistrer" cancelText="Annuler"
        onOk={async () => {
          const body = { name: edit!.name, startHour: edit!.startHour, endHour: edit!.endHour, scope: edit!.scope }
          if (edit?.id) await put(`/station/setup/shifts/${edit.id}`, body)
          else await post('/station/setup/shifts', body)
          setEdit(null)
          load()
        }}>
        {edit && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <span>Nom du poste</span>
            <input className="ant-input" value={String(edit.name ?? '')} maxLength={20} onChange={(e) => setEdit({ ...edit, name: e.target.value })} />
            <span>Heure de début</span>
            <Select style={{ width: '100%' }} options={HOURS.slice(0, 24)} value={edit.startHour as number} onChange={(x) => setEdit({ ...edit, startHour: x })} />
            <span>Heure de fin</span>
            <Select style={{ width: '100%' }} options={HOURS.slice(1)} value={edit.endHour as number} onChange={(x) => setEdit({ ...edit, endHour: x })} />
            <span>Intervalle (minutes)</span>
            <InputNumber min={0} style={{ width: '100%' }} value={edit.scope as number} onChange={(x) => setEdit({ ...edit, scope: Number(x) })} />
          </Space>
        )}
      </Modal>
    </Win>
  )
}

// ------------------------------------------------------------------ Autres réglages (OtherSetForm)

const LO = getLayout('OtherSetForm')
const SPEEDS = [{ value: 5, label: 'Rapide (5 s)' }, { value: 15, label: 'Normale (15 s)' }, { value: 30, label: 'Lente (30 s)' }]

export function OtherSetForm({ onClose }: { onClose: () => void }) {
  const f = useFields({ SpeedSelectBox: 15 })
  useEffect(() => {
    get<Record<string, string>>('/station/settings').then((s) => s['refresh.seconds'] && f.set('SpeedSelectBox', Number(s['refresh.seconds'])))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return (
    <Win layout={LO} onClose={onClose} title="Autres réglages">
      <WinForm layout={LO} values={f.values} onChange={f.set} options={{ SpeedSelectBox: SPEEDS }}
        texts={{ label1: 'Actualisation de l’écran :' }}
        buttons={{
          Confirm: async () => {
            await put('/station/settings', { 'refresh.seconds': String(f.values.SpeedSelectBox) })
            message.success('Enregistré')
            onClose()
          },
        }} />
    </Win>
  )
}
