import { useCallback, useEffect, useState } from 'react'
import { Modal, message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { get, post } from '../../api/http'
import WinForm, { getLayout, useFields, type Option } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { useStation } from '../auth'
import { formatAsn, money, paymentLabel } from '../labels'
import { esc, printHtml } from '../print'

type Row = Record<string, unknown>
const L = getLayout('ShiftForm')
const LC = getLayout('CancleDuty')
const t = (v: unknown) => (v ? dayjs(v as string).format('YYYY-MM-DD HH:mm:ss') : '')

/** Poste courant selon les horaires configurés (date + n° de poste). */
export function useShiftConfigs() {
  const [configs, setConfigs] = useState<Option[]>([])
  const [current, setCurrent] = useState<{ day: string; configId: number } | null>(null)
  useEffect(() => {
    get<Row[]>('/station/setup/shifts').then((c) => setConfigs(c.map((x) => ({ value: x.id as number, label: String(x.name) }))))
    get<{ shift: { day: string; configId: number } | null }>('/station/main').then((m) => m.shift && setCurrent({ day: m.shift.day, configId: m.shift.configId }))
  }, [])
  return { configs, current }
}

/** Rapport et clôture de poste (ShiftForm d'origine). */
export default function ShiftForm({ onClose }: { onClose: () => void }) {
  const { me } = useStation()
  const { configs, current } = useShiftConfigs()
  const f = useFields({ ShiftDT: dayjs() })
  const v = f.values
  const [data, setData] = useState<Row | null>(null)
  const [page, setPage] = useState(1)
  const [busy, setBusy] = useState<string | null>(null)

  useEffect(() => {
    if (current) f.patch({ ShiftDT: dayjs(current.day), ShiftNo: current.configId })
    else if (configs[0]) f.patch({ ShiftNo: configs[0].value })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current, configs])

  const load = useCallback(async (p = 1) => {
    if (!v.ShiftNo || !v.ShiftDT) return
    setData(await get<Row>('/station/shift-reports', { date: (v.ShiftDT as Dayjs).format('YYYY-MM-DD'), configId: v.ShiftNo, page: p, size: 50 }))
    setPage(p)
  }, [v.ShiftNo, v.ShiftDT])
  useEffect(() => {
    load(1)
  }, [load])

  const closed = !!data?.closed
  const print = () => {
    if (!data) return
    const noz = (data.nozzles as Row[]).map((n) => `<tr><td>${esc(n.gunId)}</td><td>${esc(n.gradeName)}</td><td class="n">${money(n.startPump)}</td><td class="n">${money(n.endPump)}</td><td class="n">${money(n.volume)}</td><td class="n">${money(n.amount)}</td><td class="n">${money(n.error)}</td></tr>`).join('')
    const typ = (data.types as Row[]).map((r) => `<tr><td>${esc(r.gradeName)}</td><td class="n">${money(r.price, 3)}</td><td class="n">${money(r.totalVolume)}</td><td class="n">${money(r.totalAmount)}</td></tr>`).join('')
    const emp = (data.employees as Row[]).map((r) => `<tr><td>${esc(r.employeeName)}</td><td class="n">${money(r.totalVolume)}</td><td class="n">${money(r.totalAmount)}</td></tr>`).join('')
    const sh = data.shift as Row
    printHtml('Rapport de poste', `<h1>${esc(me?.station.name)} — Rapport de poste ${esc(sh.shift)}</h1><div class="c muted">${esc(sh.name)} · ${t(sh.start)} → ${t(sh.end)}${closed ? ' · clôturé' : ''}</div>
      <h2>Par pistolet</h2><table><tr><th>Pistolet</th><th>Énergie</th><th class="n">Index début</th><th class="n">Index fin</th><th class="n">Volume</th><th class="n">Montant</th><th class="n">Écart</th></tr>${noz}</table>
      <h2>Par énergie</h2><table><tr><th>Énergie</th><th class="n">Prix</th><th class="n">Volume</th><th class="n">Montant</th></tr>${typ}</table>
      <h2>Par employé</h2><table><tr><th>Employé</th><th class="n">Volume</th><th class="n">Montant</th></tr>${emp}</table>`, 'a4')
  }

  return (
    <Win layout={L} onClose={onClose} title="Clôture de poste" width={Math.min(1400, window.innerWidth - 40)}>
      <WinForm layout={L} values={v} onChange={f.set} busy={busy}
        options={{ ShiftNo: configs }}
        texts={{ CheckedLabel: closed ? 'Poste clôturé' : '', label3: configs.length ? '' : 'Configurez d’abord les postes', label4: 'Les postes suivent les horaires configurés' }}
        buttons={{
          ShiftClose: () => Modal.confirm({
            title: 'Clôturer ce poste ?', content: `Poste ${String((data?.shift as Row)?.shift ?? '')}`, okText: 'Clôturer', cancelText: 'Annuler',
            onOk: async () => {
              setBusy('ShiftClose')
              try {
                setData(await post<Row>('/station/shift-reports/close', { date: (v.ShiftDT as Dayjs).format('YYYY-MM-DD'), configId: v.ShiftNo }))
                message.success('Poste clôturé')
                Modal.confirm({ title: 'Imprimer le rapport de poste ?', okText: 'Imprimer', cancelText: 'Fermer', onOk: print })
              } finally {
                setBusy(null)
              }
            },
          }),
        }}
        disabled={closed ? ['ShiftClose'] : []}
        grids={{
          NozView: {
            rows: (data?.nozzles as Row[]) ?? [], rowKey: (r) => `${r.gunId}-${r.gradeId}`,
            value: { NozID: (r) => r.gunId, OilType: (r) => r.gradeName, OilPrice: (r) => money(r.startPump), Volume: (r) => money(r.endPump), Amount: (r) => money(r.volume),
              TruePay: (r) => money(r.error), TrueTime: (r) => money(r.amount), ASN: (r) => t(r.startTime), BAL: (r) => t(r.endTime) },
          },
          TransactionView: {
            rows: (data?.transactions as Row[]) ?? [], rowKey: 'id',
            pagination: { current: page, total: Number(data?.total ?? 0), pageSize: 50, onChange: load, size: 'small', showSizeChanger: false,
              showTotal: (n) => `${n} enregistrements` },
            value: {
              dataGridViewTextBoxColumn1: (r) => r.posttc, dataGridViewTextBoxColumn2: (r) => r.gunId, dataGridViewTextBoxColumn3: (r) => r.employeeNo,
              dataGridViewTextBoxColumn4: (r) => (String(r.asn).startsWith('0000') ? '' : formatAsn(r.asn as string)), dataGridViewTextBoxColumn5: (r) => money(r.volume),
              dataGridViewTextBoxColumn6: (r) => money(r.truelyPay), dataGridViewTextBoxColumn7: (r) => t(r.trueTime), dataGridViewTextBoxColumn8: (r) => money(r.volumeTotal),
              dataGridViewTextBoxColumn9: (r) => r.gradeName, PPU: (r) => money(r.ppu, 3), PayMent: () => 'Normale', PosP: (r) => money(r.bal),
              POSTTC: (r) => paymentLabel(r.paymentId),
            },
          },
          OilTypeView: {
            rows: (data?.employees as Row[]) ?? [], rowKey: 'employeeName',
            value: { xxxx: (r) => r.employeeName, dataGridViewTextBoxColumn21: (r) => money(r.totalAmount), dataGridViewTextBoxColumn20: (r) => money(r.totalVolume) },
          },
          TotalView: {
            rows: (data?.types as Row[]) ?? [], rowKey: (r) => `${r.gradeId}-${r.price}`,
            value: {
              dataGridViewTextBoxColumn11: (r) => r.gradeName, TypePPU: (r) => money(r.price, 3), dataGridViewTextBoxColumn12: (r) => money(r.totalVolume),
              dataGridViewTextBoxColumn13: (r) => money(r.totalAmount), dataGridViewTextBoxColumn14: (r) => money(r.empVolume), dataGridViewTextBoxColumn15: (r) => money(r.empAmount),
              dataGridViewTextBoxColumn16: (r) => money(r.userVolume), dataGridViewTextBoxColumn17: (r) => money(r.userAmount), dataGridViewTextBoxColumn18: (r) => money(r.creditVolume),
              dataGridViewTextBoxColumn19: (r) => money(r.creditAmount), dataGridViewTextBoxColumn22: (r) => money(r.noCardVolume), dataGridViewTextBoxColumn23: (r) => money(r.noCardAmount),
              CashCouponsV: (r) => money(r.repairVolume), CashCouponsM: (r) => money(r.repairAmount),
            },
          },
        }} />
      <div style={{ textAlign: 'right', marginTop: 6 }}><a onClick={print}>Imprimer le rapport</a></div>
    </Win>
  )
}

/** Annulation de la clôture d'un poste (CancleDuty d'origine). */
export function CancleDuty({ onClose }: { onClose: () => void }) {
  const { configs } = useShiftConfigs()
  const f = useFields({ ShiftDT: dayjs() })
  useEffect(() => {
    if (configs[0] && !f.values.ShiftNo) f.set('ShiftNo', configs[0].value)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [configs])
  return (
    <Win layout={LC} onClose={onClose} title="Annuler la clôture">
      <WinForm layout={LC} values={f.values} onChange={f.set} options={{ ShiftNo: configs }}
        buttons={{
          ShiftClose: () => Modal.confirm({
            title: 'Annuler la clôture de ce poste ?', content: 'Les rapports du poste seront supprimés et le poste rouvert.', okText: 'Valider', cancelText: 'Retour',
            onOk: async () => {
              await post('/station/shift-reports/cancel', { date: (f.values.ShiftDT as Dayjs).format('YYYY-MM-DD'), configId: f.values.ShiftNo })
              message.success('Opération réussie')
            },
          }),
        }} />
    </Win>
  )
}
