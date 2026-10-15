import { useCallback, useEffect, useState } from 'react'
import { message } from 'antd'
import dayjs from 'dayjs'
import { get, post, put, type PageResult } from '../../api/http'
import WinForm, { getLayout, useFields } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { formatAsn, money } from '../labels'
import { STATUS_LABELS } from './statuses'

type Row = Record<string, unknown>

// ------------------------------------------------------------------ Code PIN oublié (PasswordLost)

const LP = getLayout('PasswordLost')

export function PasswordLost({ onClose }: { onClose: () => void }) {
  const f = useFields()
  const v = f.values
  return (
    <Win layout={LP} onClose={onClose} title="Code PIN oublié">
      <WinForm layout={LP} values={v} onChange={f.set} readOnly={['PersonName', 'CustomerID', 'CertificateNo']} passwords={['NewPassWord']}
        texts={{ label6: 'Nouveau code PIN :' }}
        buttons={{
          ReadButton: async () => {
            const asn = String(v.CardNo ?? '').replace(/\s/g, '')
            if (asn.length < 16) return message.warning('Saisissez le numéro de carte')
            const r = await get<Row>(`/station/cardmake/details/${asn}`)
            f.patch({ PersonName: r.name ?? r.companyName ?? r.employeeName, CustomerID: r.accountNo ?? r.account, CertificateNo: r.certificateNo })
          },
          Confirm: async () => {
            if (!v.PersonName) return message.warning('Lisez d’abord la carte')
            if (!/^\d{4,8}$/.test(String(v.NewPassWord ?? ''))) return message.error('Le code PIN doit contenir 4 à 8 chiffres')
            await post(`/station/cards/${String(v.CardNo).replace(/\s/g, '')}/pin`, { pin: v.NewPassWord })
            message.success('Code PIN modifié')
            onClose()
          },
          ShiftClose: onClose,
        }} />
    </Win>
  )
}

// ------------------------------------------------------------------ Stock (StockManager)

const LS = getLayout('StockManager')

export function StockManager({ onClose }: { onClose: () => void }) {
  const [rows, setRows] = useState<Row[]>([])
  useEffect(() => {
    get<Row[]>('/station/misc/stock').then(setRows)
  }, [])
  return (
    <Win layout={LS} onClose={onClose} title="Stock">
      <WinForm layout={LS} values={{}} onChange={() => undefined} hidden={['PageText', 'DirectButton', 'TPage', 'label2']}
        grids={{
          TransactionView: {
            rows, rowKey: 'id',
            value: { TankID: (r) => r.tankName ?? r.id, OilType: (r) => r.gradeName, Height: (r) => money(r.totalHeight), Volume: (r) => money(r.totalVolume),
              OilPrice: (r) => money(r.currentOil) },
          },
        }} />
    </Win>
  )
}

// ------------------------------------------------------------------ Informations client (UserCardMessage)

const LU = getLayout('UserCardMessage')
const UCM_TYPES = [{ value: 0, label: 'Carte utilisateur' }, { value: 1, label: 'Carte de crédit' }, { value: 2, label: 'Carte secondaire' }, { value: 3, label: 'Carte employé' }]
const TYPE_LABEL: Record<string, string> = { '010001': 'Carte utilisateur', '010011': 'Carte de crédit', '010013': 'Carte secondaire', '010004': 'Carte employé', '010006': 'Carte de réparation' }

export function UserCardMessage({ onClose }: { onClose: () => void }) {
  const f = useFields({ CardTypeSelect: 0 })
  const [data, setData] = useState<PageResult<Row>>({ rows: [], total: 0 })
  const [page, setPage] = useState(1)
  const load = useCallback(async (p: number) => {
    setData(await get<PageResult<Row>>('/station/misc/customers', { cardType: f.values.CardTypeSelect, name: f.values.NameQuery ?? '', phone: f.values.PhoneQuery ?? '', page: p, size: 20 }))
    setPage(p)
  }, [f.values])
  return (
    <Win layout={LU} onClose={onClose} title="Informations client">
      <WinForm layout={LU} values={f.values} onChange={f.set} options={{ CardTypeSelect: UCM_TYPES }} hidden={['Cur', 'Next', 'Page']}
        buttons={{ QuerySearch: () => load(1) }}
        grids={{
          UserView: {
            rows: data.rows, rowKey: 'asn', pagination: { current: page, total: data.total, pageSize: 20, onChange: load, size: 'small' },
            value: { CustomerID: (r) => r.customerId, NozID: (r) => r.name, OilType: (r) => formatAsn(r.asn as string),
              OilPrice: (r) => TYPE_LABEL[String(r.asn).slice(0, 6)] ?? 'Carte', Volume: (r) => r.phone, Amount: (r) => r.vehicleNo, TruePay: (r) => money(r.balance) },
          },
        }} />
    </Win>
  )
}

// ------------------------------------------------------------------ Liste des cartes (CustomerUserList)

const LC = getLayout('CustomerUserList')

export function CustomerUserList({ onClose }: { onClose: () => void }) {
  const f = useFields()
  const [data, setData] = useState<PageResult<Row>>({ rows: [], total: 0 })
  const [page, setPage] = useState(1)
  const load = useCallback(async (p: number) => {
    const v = f.values
    const r = await get<PageResult<Row>>('/station/misc/customers', { name: v.UserName ?? '', phone: v.Phone ?? '', carNo: v.CarNumber ?? '', status: v.CardStatue, page: p, size: 20 })
    setData(r)
    setPage(p)
    f.set('Total', String(r.total))
  }, [f])
  useEffect(() => {
    load(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return (
    <Win layout={LC} onClose={onClose} title="Liste des cartes">
      <WinForm layout={LC} values={f.values} onChange={f.set} readOnly={['Total']} hidden={['Cur', 'Page', 'TotalNumber', 'Next']}
        options={{ CardStatue: Object.entries(STATUS_LABELS).map(([value, label]) => ({ value: Number(value), label })) }}
        buttons={{ EmpReadButton: () => load(1) }}
        grids={{
          UserView: {
            rows: data.rows, rowKey: 'asn', pagination: { current: page, total: data.total, pageSize: 20, onChange: load, size: 'small' },
            value: { dataGridViewTextBoxColumn1: (r) => formatAsn(r.asn as string), dataGridViewTextBoxColumn2: (r) => r.name,
              CardOperate: (r) => STATUS_LABELS[Number(r.status)] ?? r.status, dataGridViewTextBoxColumn3: (r) => r.phone, dataGridViewTextBoxColumn4: (r) => r.vehicleNo },
          },
        }} />
    </Win>
  )
}

// ------------------------------------------------------------------ Points virtuels (VirtualScore, AddVirtualScore, VirtualScoreUpdate, CardScore)

const LV = getLayout('VirtualScore')
const LVA = getLayout('AddVirtualScore')
const LVU = getLayout('VirtualScoreUpdate')
const LCS = getLayout('CardScore')

export function VirtualScore({ onClose }: { onClose: () => void }) {
  const f = useFields()
  const [data, setData] = useState<PageResult<Row>>({ rows: [], total: 0 })
  const [page, setPage] = useState(1)
  const [add, setAdd] = useState(false)
  const [edit, setEdit] = useState<Row | null>(null)
  const [cardScore, setCardScore] = useState(false)
  const load = useCallback(async (p: number) => {
    setData(await get<PageResult<Row>>('/station/misc/virtual-users', { name: f.values.QueryName ?? '', phone: f.values.QueryPhone ?? '', page: p }))
    setPage(p)
  }, [f.values])
  useEffect(() => {
    load(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return (
    <Win layout={LV} onClose={onClose} title="Points virtuels">
      <WinForm layout={LV} values={f.values} onChange={f.set} hidden={['CurrentPage', 'DiretToButton', 'label4', 'label3']}
        buttons={{ QueryButton: () => load(1), AddButton: () => setAdd(true), CardScore: () => setCardScore(true) }}
        grids={{
          TransactionView: {
            rows: data.rows, rowKey: 'id', onDoubleClick: setEdit, pagination: { current: page, total: data.total, pageSize: 20, onChange: load, size: 'small' },
            value: { ID: (r) => r.id, OilType: (r) => r.userName, OilPrice: (r) => r.phone, Volume: (r) => money(r.score, 0) },
          },
        }} />
      {add && <AddVirtualUser onClose={() => { setAdd(false); load(1) }} />}
      {edit && <VirtualScoreUpdate row={edit} onClose={() => { setEdit(null); load(page) }} />}
      {cardScore && <CardScore onClose={() => setCardScore(false)} />}
    </Win>
  )
}

function AddVirtualUser({ onClose }: { onClose: () => void }) {
  const f = useFields()
  return (
    <Win layout={LVA} onClose={onClose} title="Ajouter un utilisateur">
      <WinForm layout={LVA} values={f.values} onChange={f.set}
        buttons={{ QueryButton: async () => { await post('/station/misc/virtual-users', { userName: f.values.UserName, phone: f.values.UserPhone }); message.success('Utilisateur ajouté'); onClose() } }} />
    </Win>
  )
}

function VirtualScoreUpdate({ row, onClose }: { row: Row; onClose: () => void }) {
  const f = useFields({ UpdateUserName: row.userName, UpdatePhone: row.phone, ScoreTypeSelect: 0 })
  const [hist, setHist] = useState<PageResult<Row>>({ rows: [], total: 0 })
  const [page, setPage] = useState(1)
  const load = useCallback(async (p: number) => {
    setHist(await get<PageResult<Row>>(`/station/misc/virtual-users/${row.id}/history`, { page: p }))
    setPage(p)
  }, [row.id])
  useEffect(() => {
    load(1)
  }, [load])
  return (
    <Win layout={LVU} onClose={onClose} title="Utilisateur">
      <WinForm layout={LVU} values={f.values} onChange={f.set} hidden={['label6', 'CurrentPage', 'DirectBtn', 'TotalPage']}
        options={{ ScoreTypeSelect: [{ value: 0, label: 'Ajouter des points' }, { value: 1, label: 'Retirer des points' }] }}
        buttons={{
          UpdateBtn: async () => { await put(`/station/misc/virtual-users/${row.id}`, { userName: f.values.UpdateUserName, phone: f.values.UpdatePhone }); message.success('Enregistré') },
          ScoreDealBtn: async () => {
            await post(`/station/misc/virtual-users/${row.id}/score`, { type: f.values.ScoreTypeSelect, value: Number(f.values.ScoreValue) })
            message.success('Points enregistrés')
            f.set('ScoreValue', '')
            load(1)
          },
        }}
        grids={{
          TransactionView: {
            rows: hist.rows, rowKey: 'id', pagination: { current: page, total: hist.total, pageSize: 20, onChange: load, size: 'small' },
            value: { ID: (r) => r.scoreName, OilType: (r) => r.scorePhone, OilPrice: (r) => (Number(r.scoreType) === 1 ? 'Retrait' : 'Ajout'), Volume: (r) => money(r.scoreValue, 0),
              FinalScore: (r) => money(r.finalScore, 0), CreateTime: (r) => dayjs(r.createTime as string).format('YYYY-MM-DD HH:mm:ss'), Employee: (r) => r.employee },
          },
        }} />
    </Win>
  )
}

function CardScore({ onClose }: { onClose: () => void }) {
  const f = useFields()
  const [data, setData] = useState<PageResult<Row>>({ rows: [], total: 0 })
  const [page, setPage] = useState(1)
  const load = async (p: number) => {
    setData(await get<PageResult<Row>>('/station/misc/customers', { cardType: 0, name: f.values.QueryName ?? '', phone: f.values.QueryPhone ?? '', carNo: f.values.QueryCarNo ?? '', page: p, size: 20 }))
    setPage(p)
  }
  return (
    <Win layout={LCS} onClose={onClose} title="Points des cartes">
      <WinForm layout={LCS} values={f.values} onChange={f.set} hidden={['label4', 'PreBtn', 'NextBtn']}
        buttons={{ QueryBtn: () => load(1) }}
        grids={{
          TransactionView: {
            rows: data.rows, rowKey: 'asn', pagination: { current: page, total: data.total, pageSize: 20, onChange: load, size: 'small' },
            value: { ID: (r) => formatAsn(r.asn as string), OilType: (r) => r.name, OilPrice: (r) => r.phone, Volume: (r) => money(r.score) },
          },
        }} />
    </Win>
  )
}
