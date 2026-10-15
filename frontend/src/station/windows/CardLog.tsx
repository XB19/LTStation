import { useState } from 'react'
import { message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { get } from '../../api/http'
import WinForm, { getLayout, useFields } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { cardOpLabel, formatAsn, money } from '../labels'

type Row = Record<string, unknown>
const L = getLayout('CardLog')
const LB = getLayout('BusinessReportMess')
const t = (v: unknown) => (v ? dayjs(v as string).format('YYYY-MM-DD HH:mm:ss') : '')
const join = (d: unknown, h: unknown) => `${(d as Dayjs).format('YYYY-MM-DD')}T${(h as Dayjs).format('HH:mm:ss')}`

const BUSINESS_CARD_TYPES = ['Toutes', 'Carte utilisateur', 'Carte employé', 'Carte de crédit', 'Carte de groupe', 'Carte principale', 'Carte secondaire']
  .map((label, value) => ({ value, label }))
const OP_TYPES = [{ value: 0, label: 'Toutes' }, ...[1, 2, 3, 4, 6, 8, 9, 10, 11, 12, 13, 14, 16].map((v) => ({ value: v, label: cardOpLabel(v) }))]

interface LogData {
  rows: Row[]
  total: number
  byUser: Row[]
  totals: Row
}

const logValue = (deposit: string, gift: string, consume: string, volume: string, time: string, balance: string, asnCol: string, nameCol: string, opCol: string) => ({
  [asnCol]: (r: Row) => formatAsn(r.asn as string),
  [nameCol]: (r: Row) => r.userName,
  [opCol]: (r: Row) => cardOpLabel(r.transactionTypeId),
  [deposit]: (r: Row) => (Number(r.transactionTypeId) === 2 ? money(r.amount) : ''),
  [gift]: (r: Row) => (Number(r.transactionTypeId) === 2 && Number(r.discountAmount) ? money(r.discountAmount) : ''),
  [consume]: (r: Row) => ([11, 12, 13].includes(Number(r.transactionTypeId)) ? money(Math.abs(Number(r.amount))) : ''),
  [volume]: (r: Row) => (r.volume !== null && r.volume !== undefined ? money(r.volume) : ''),
  [time]: (r: Row) => t(r.transDatetime),
  [balance]: (r: Row) => money(r.balance),
})

/** Journal des cartes (CardLog d'origine). */
export default function CardLog({ onClose }: { onClose: () => void }) {
  const f = useFields({ DTStartDate: dayjs().subtract(7, 'day'), DTStartTime: dayjs().startOf('day'), DTEndDate: dayjs(), DTEndTime: dayjs().endOf('day') })
  const v = f.values
  const [data, setData] = useState<LogData | null>(null)
  const [page, setPage] = useState(1)
  const params = (p: number) => ({ start: join(v.DTStartDate, v.DTStartTime), end: join(v.DTEndDate, v.DTEndTime), asn: v.CardNo ?? '', certNo: v.CertificateNo ?? '',
    carNo: v.CardNoRequest ?? '', page: p, size: 50 })
  const load = async (p: number) => {
    setData(await get<LogData>('/station/misc/card-logs', params(p)))
    setPage(p)
  }
  return (
    <Win layout={L} onClose={onClose} title="Journal des cartes" width={Math.min(1260, window.innerWidth - 40)}>
      <WinForm layout={L} values={v} onChange={f.set} timeOnly={['DTStartTime', 'DTEndTime']}
        texts={{ CardNoSearch: 'Rechercher la carte', label109: 'La recherche par pièce ne concerne que les cartes utilisateur' }}
        buttons={{
          CardNoSearch: () => (String(v.CardNo ?? '').length < 16 ? message.warning('Saisissez le numéro de carte') : load(1)),
          LogSearch: () => load(1),
          Export: () => window.open(`/api/station/misc/card-logs/export?${new URLSearchParams(Object.entries(params(1)).map(([k, x]) => [k, String(x)]))}`, '_blank'),
        }}
        grids={{
          CardLogView: {
            rows: data?.rows ?? [], rowKey: 'id',
            pagination: { current: page, total: data?.total ?? 0, pageSize: 50, onChange: load, size: 'small', showSizeChanger: false, showTotal: (n) => `${n} enregistrements` },
            value: logValue('dataGridViewTextBoxColumn3', 'dataGridViewTextBoxColumn4', 'dataGridViewTextBoxColumn5', 'dataGridViewTextBoxColumn6',
              'dataGridViewTextBoxColumn8', 'dataGridViewTextBoxColumn7', 'dataGridViewTextBoxColumn1', 'dataGridViewTextBoxColumn2', 'CardOperate'),
          },
          LogTotalView: {
            rows: data?.byUser ?? [], rowKey: (r) => String(r.userName),
            value: { OilType: (r) => r.userName, OilPrice: (r) => money(r.deposit), DecutionAmount: (r) => money(r.charge), Volume: (r) => money(r.gift),
              Amount: (r) => money(r.consume), TruePay: (r) => money(r.volume) },
          },
        }} />
    </Win>
  )
}

/** Rapport d'activité (BusinessReportMess d'origine). */
export function BusinessReportMess({ onClose }: { onClose: () => void }) {
  const f = useFields({ DTStartDate: dayjs().startOf('month'), DTStartTime: dayjs().startOf('day'), DTEndDate: dayjs(), DTEndTime: dayjs().endOf('day'),
    CardTypeSelect: 0, OperateType: 0 })
  const v = f.values
  const [data, setData] = useState<LogData | null>(null)
  const [page, setPage] = useState(1)
  const params = (p: number) => ({ start: join(v.DTStartDate, v.DTStartTime), end: join(v.DTEndDate, v.DTEndTime), cardType: v.CardTypeSelect, opType: v.OperateType,
    page: p, size: 50 })
  const load = async (p: number) => {
    setData(await get<LogData>('/station/misc/card-logs', params(p)))
    setPage(p)
  }
  const tot = data?.totals ?? {}
  return (
    <Win layout={LB} onClose={onClose} title="Activité" width={Math.min(1400, window.innerWidth - 40)}>
      <WinForm layout={LB} values={v} onChange={f.set} timeOnly={['DTStartTime', 'DTEndTime']}
        options={{ CardTypeSelect: BUSINESS_CARD_TYPES, OperateType: OP_TYPES }}
        buttons={{
          SearchButton: () => load(1),
          Export: () => window.open(`/api/station/misc/card-logs/export?${new URLSearchParams(Object.entries(params(1)).map(([k, x]) => [k, String(x)]))}`, '_blank'),
        }}
        grids={{
          BusinessLogView: {
            rows: data?.rows ?? [], rowKey: 'id',
            pagination: { current: page, total: data?.total ?? 0, pageSize: 50, onChange: load, size: 'small', showSizeChanger: false, showTotal: (n) => `${n} enregistrements` },
            value: logValue('dataGridViewTextBoxColumn3', 'dataGridViewTextBoxColumn4', 'dataGridViewTextBoxColumn5', 'dataGridViewTextBoxColumn6',
              'dataGridViewTextBoxColumn8', 'dataGridViewTextBoxColumn7', 'dataGridViewTextBoxColumn1', 'dataGridViewTextBoxColumn2', 'CardOperate'),
          },
          BussinessTotalView: {
            rows: data ? [tot] : [], rowKey: () => 'total',
            hideColumns: ['dataGridViewTextBoxColumn15', 'dataGridViewTextBoxColumn16', 'dataGridViewTextBoxColumn17'],
            value: {
              dataGridViewTextBoxColumn9: (r) => r.cnt, dataGridViewTextBoxColumn10: (r) => money(r.deposit), TotalDisAmount: (r) => money(r.gift),
              dataGridViewTextBoxColumn11: (r) => money(r.charge), dataGridViewTextBoxColumn12: (r) => money(r.nonFuel), dataGridViewTextBoxColumn13: (r) => money(r.consume),
              dataGridViewTextBoxColumn14: (r) => money(r.volume),
            },
          },
        }} />
    </Win>
  )
}
