import { useEffect, useState } from 'react'
import { message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { get } from '../../api/http'
import WinForm, { getLayout, useFields, type Option } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { useStation } from '../auth'
import { formatAsn, money, paymentLabel } from '../labels'

type Row = Record<string, unknown>
const L = getLayout('OilTransacation')

const SHIFT_STATES = [{ value: 0, label: 'Tous' }, { value: 1, label: 'Postes clôturés' }, { value: 2, label: 'Postes non clôturés' }]
const ORDERS = [{ value: 0, label: 'Date' }, { value: 1, label: 'Pistolet' }]
const CARD_TYPES = [{ value: 0, label: 'Carte employé' }, { value: 1, label: 'Carte utilisateur' }, { value: 2, label: 'Carte de crédit' }, { value: 3, label: 'Carte secondaire' }]
const CARD_LABEL: Record<string, string> = { TOTAL: 'Total', '000000': 'Sans carte', '010001': 'Carte utilisateur', '010004': 'Carte employé', '010011': 'Carte de crédit',
  '010013': 'Carte secondaire', '010006': 'Carte de réparation' }
const iso = (d: unknown) => (d ? (d as Dayjs).format('YYYY-MM-DDTHH:mm:ss') : undefined)

/** Recherche des transactions (OilTransacation d'origine). */
export default function OilTransacation({ onClose }: { onClose: () => void }) {
  const { me } = useStation()
  const f = useFields({
    ShiftSelect: 0, OrderSelect: 0, CardTypeSelect: 0, ShiftDate: dayjs(), DateCheck: true,
    StartDate: dayjs().subtract(3, 'day').startOf('day'), EndDate: dayjs().add(1, 'hour'),
  })
  const v = f.values
  const [lists, setLists] = useState<Record<string, Option[]>>({})
  const [data, setData] = useState<{ rows: Row[]; total: number; summary: Row[] }>({ rows: [], total: 0, summary: [] })
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    const sid = me?.station.stationId
    Promise.all([
      get<Row[]>('/station/setup/all-grades'), get<Row[]>('/station/setup/nozzles'), get<Row[]>('/employees', { stationId: sid }), get<Row[]>('/station/setup/shifts'),
    ]).then(([g, n, e, s]) => {
      setLists({
        OilCodeSelect: g.map((x) => ({ value: x.gradeId as number, label: String(x.name) })),
        NozSelect: n.map((x) => ({ value: x.nozId as number, label: String(x.nozId) })),
        EmpSelect: e.map((x) => ({ value: x.employeeId as number, label: String(x.name) })),
        ShiftDuty: s.map((x) => ({ value: x.id as number, label: String(x.name) })),
      })
    })
  }, [me])

  const params = (p: number) => ({
    gradeId: v.OilCodeCheck ? v.OilCodeSelect : undefined,
    shiftState: v.ShiftCheck ? v.ShiftSelect : undefined,
    shiftDate: v.ShiftDutyCheck && v.ShiftDate ? (v.ShiftDate as Dayjs).format('YYYY-MM-DD') : undefined,
    shiftConfigId: v.ShiftDutyCheck ? v.ShiftDuty : undefined,
    order: v.OrderCheck ? v.OrderSelect : 0,
    cardType: v.CardTypeCheck ? v.CardTypeSelect : undefined,
    nozId: v.NozCheck ? v.NozSelect : undefined,
    asn: v.AsnCheck ? v.AsnSelect : undefined,
    carNo: v.CardNoCheck ? v.CardNoSelect : undefined,
    employeeId: v.EmpCheck ? v.EmpSelect : undefined,
    customer: v.UserCheck ? v.UserSelect : undefined,
    start: v.DateCheck ? iso(v.StartDate) : undefined,
    end: v.DateCheck ? iso((v.EndDate as Dayjs)?.endOf('day')) : undefined,
    page: p,
    size: 50,
  })

  const load = async (p: number) => {
    if (v.DateCheck && v.StartDate && v.EndDate && (v.EndDate as Dayjs).isBefore(v.StartDate as Dayjs)) return message.error('La date de fin est antérieure à la date de début')
    setLoading(true)
    try {
      setData(await get('/station/transactions', params(p)))
      setPage(p)
    } finally {
      setLoading(false)
    }
  }

  const exportXls = (p: number) => {
    const q = new URLSearchParams()
    Object.entries(params(p)).forEach(([k, val]) => val !== undefined && val !== null && val !== '' && q.append(k, String(val)))
    window.open(`/api/station/transactions/export?${q}`, '_blank')
  }

  return (
    <Win layout={L} onClose={onClose} title="Recherche des transactions" width={Math.min(1400, window.innerWidth - 40)}>
      <WinForm layout={L} values={v} onChange={f.set} options={{ ...lists, ShiftSelect: SHIFT_STATES, OrderSelect: ORDERS, CardTypeSelect: CARD_TYPES }}
        hidden={['label2']}
        buttons={{ QuerySearch: () => load(1), ExportPage: () => exportXls(page), Export: () => exportXls(0) }}
        texts={{ ExportPage: 'Exporter la page', Export: 'Exporter tout' }}
        grids={{
          TransactionView: {
            rows: data.rows, rowKey: 'id', loading,
            pagination: { current: page, total: data.total, pageSize: 50, showSizeChanger: false, onChange: load, size: 'small',
              showTotal: (t) => `${t} enregistrements, 50 par page` },
            value: {
              NozID: (r) => r.gunId, OilType: (r) => r.gradeName, OilPrice: (r) => money(r.price, 3), Volume: (r) => money(r.volume), Amount: (r) => money(r.amount),
              TruePay: (r) => money(r.truelyPay), TrueTime: (r) => dayjs(r.trueTime as string).format('YYYY-MM-DD HH:mm:ss'),
              ASN: (r) => (String(r.asn).startsWith('0000') ? '' : formatAsn(r.asn as string)), BAL: (r) => money(r.bal), TType: () => 'Normale',
              PayMent: (r) => paymentLabel(r.paymentId), PosP: () => '', POSTTC: (r) => r.posttc, EMP: (r) => r.employeeName, VolumeTotal: (r) => money(r.volumeTotal),
              Shift: (r) => (r.shift ? `${r.shift}${Number(r.shiftStatus) === 1 ? ' (clôturé)' : ''}` : ''), CardNo: (r) => r.carNumber, Mileage: (r) => r.mileage,
              DisCount: (r) => money(r.discount), Coupon: (r) => r.coupon, CouponValue: (r) => r.couponValue,
            },
          },
          TotalSumView: {
            rows: data.summary, rowKey: 'cardType',
            value: { Count: (r) => CARD_LABEL[String(r.cardType)] ?? 'Autre', TotalVolume: (r) => money(r.totalVolume), TotalAmount: (r) => money(r.totalAmount),
              TotalUserCard: (r) => r.cnt },
          },
        }} />
    </Win>
  )
}
