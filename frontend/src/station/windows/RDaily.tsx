import { useCallback, useEffect, useState } from 'react'
import { Modal, message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { get, post } from '../../api/http'
import WinForm, { getLayout, useFields } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { useStation } from '../auth'
import { money } from '../labels'
import { esc, printHtml } from '../print'

type Row = Record<string, unknown>
const L = getLayout('RDaily')
const CARD: Record<string, string> = { '000000': 'Sans carte', '010001': 'Carte utilisateur', '010004': 'Carte employé', '010011': 'Carte de crédit', '010013': 'Carte secondaire',
  '010006': 'Carte de réparation', '010002': 'Carte de groupe', '010014': 'Carte principale', '010012': 'Carte de crédit de groupe', FFFFFF: 'Carte M1' }
const t = (v: unknown) => (v ? dayjs(v as string).format('YYYY-MM-DD HH:mm:ss') : '')

/** Rapport journalier (month = false) ou mensuel (month = true) (RDaily d'origine). */
export default function RDaily({ onClose, month }: { onClose: () => void; month: boolean }) {
  const { me } = useStation()
  const f = useFields({ DTDate: dayjs().add(1, 'day').startOf('day'), DTTime: dayjs().startOf('day') })
  const v = f.values
  const [data, setData] = useState<Row | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const params = useCallback(() => ({ date: (v.DTDate as Dayjs).format('YYYY-MM-DD'), time: (v.DTTime as Dayjs).format('HH:mm'), month }), [v.DTDate, v.DTTime, month])
  const load = useCallback(async () => setData(await get<Row>('/station/shift-reports/daily', params())), [params])
  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const totals = (data?.totals as Row) ?? {}
  const print = () => {
    if (!data) return
    const noz = (data.nozzles as Row[]).map((n) => `<tr><td>${esc(n.gunId)}</td><td>${esc(n.gradeName)}</td><td class="n">${money(n.startPump)}</td><td class="n">${money(n.endPump)}</td><td class="n">${money(n.volume)}</td><td class="n">${money(n.amount)}</td></tr>`).join('')
    const typ = (data.types as Row[]).map((r) => `<tr><td>${esc(r.gradeName)}</td><td class="n">${money(r.price, 3)}</td><td class="n">${money(r.totalVolume)}</td><td class="n">${money(r.totalAmount)}</td></tr>`).join('')
    printHtml(month ? 'Rapport mensuel' : 'Rapport journalier', `<h1>${esc(me?.station.name)} — ${month ? 'Rapport mensuel' : 'Rapport journalier'}</h1>
      <div class="c muted">${t(data.start)} → ${t(data.end)}</div>
      <h2>Par pistolet</h2><table><tr><th>Pistolet</th><th>Énergie</th><th class="n">Index début</th><th class="n">Index fin</th><th class="n">Volume</th><th class="n">Montant</th></tr>${noz}</table>
      <h2>Par énergie</h2><table><tr><th>Énergie</th><th class="n">Prix</th><th class="n">Volume</th><th class="n">Montant</th></tr>${typ}</table>
      <h2>Synthèse</h2><table><tr><td>Nombre de ventes</td><td class="n">${esc(totals.cnt)}</td></tr><tr><td>Volume total</td><td class="n">${money(totals.totalVolume)}</td></tr>
      <tr><td>Montant total</td><td class="n">${money(totals.totalAmount)}</td></tr></table>`, 'a4')
  }

  return (
    <Win layout={L} onClose={onClose} title={month ? 'Rapport mensuel' : 'Rapport journalier'} width={Math.min(1400, window.innerWidth - 40)}>
      <WinForm layout={L} values={v} onChange={f.set} busy={busy} timeOnly={['DTTime']}
        texts={{ EndLabel: data?.cleared ? 'Enregistré' : '', label3: month ? 'Date et heure de fin : le rapport couvre le mois qui précède' : 'Date et heure de fin : le rapport couvre les 24 heures qui précèdent' }}
        buttons={{
          SearchButton: () => load(),
          DailyReport: () => Modal.confirm({
            title: month ? 'Enregistrer le rapport mensuel ?' : 'Enregistrer le rapport journalier ?', okText: 'Enregistrer', cancelText: 'Annuler',
            onOk: async () => {
              setBusy('DailyReport')
              try {
                setData(await post<Row>('/station/shift-reports/daily/clear', params()))
                message.success('Rapport enregistré')
                print()
              } finally {
                setBusy(null)
              }
            },
          }),
        }}
        grids={{
          NozView: {
            rows: (data?.nozzles as Row[]) ?? [], rowKey: (r) => `${r.gunId}-${r.gradeId}`,
            value: {
              dataGridViewTextBoxColumn10: (r) => r.gunId, dataGridViewTextBoxColumn11: (r) => r.gradeName, dataGridViewTextBoxColumn12: (r) => money(r.startPump),
              dataGridViewTextBoxColumn13: (r) => money(r.endPump), dataGridViewTextBoxColumn14: (r) => money(r.volume), dataGridViewTextBoxColumn15: (r) => money(r.amount),
              dataGridViewTextBoxColumn16: (r) => t(r.startTime), dataGridViewTextBoxColumn17: (r) => t(r.endTime), dataGridViewTextBoxColumn18: (r) => money(r.error),
            },
          },
          TankView: {
            rows: (data?.types as Row[]) ?? [], rowKey: (r) => `${r.gradeId}-${r.price}`,
            value: {
              dataGridViewTextBoxColumn1: (r) => r.gradeName, dataGridViewTextBoxColumn2: (r) => money(r.price, 3), dataGridViewTextBoxColumn3: (r) => money(r.density, 3),
              dataGridViewTextBoxColumn5: (r) => money(r.totalAmount), dataGridViewTextBoxColumn4: (r) => money(r.totalVolume), EmpCardA: (r) => money(r.empAmount),
              EmpCardV: (r) => money(r.empVolume), PerCardA: (r) => money(r.userAmount), PreCardV: (r) => money(r.userVolume), CreCardA: (r) => money(r.creditAmount),
              CreCardV: (r) => money(r.creditVolume), CashCouponsM: (r) => money(r.repairAmount), CashCouponsV: (r) => money(r.repairVolume),
              NotCardA: (r) => money(r.noCardAmount), NotCardV: (r) => money(r.noCardVolume),
            },
          },
          CardView: {
            rows: (data?.cards as Row[]) ?? [], rowKey: 'cardType',
            value: { dataGridViewTextBoxColumn6: (r) => CARD[String(r.cardType)] ?? 'Autre', TCount: (r) => r.cnt, dataGridViewTextBoxColumn7: (r) => money(r.totalVolume),
              dataGridViewTextBoxColumn8: (r) => money(r.totalAmount), StartTime: (r) => t(r.startTime), EndTime: (r) => t(r.endTime) },
          },
          TotalSumView: {
            rows: data ? [totals] : [], rowKey: () => 'total',
            value: {
              Count: (r) => r.cnt, TotalVolume: (r) => money(r.totalVolume), TotalAmount: (r) => money(r.totalAmount), TotalUserCard: (r) => money(r.userVolume),
              TotalUserCardAmount: (r) => money(r.userAmount), TotalEmpVolume: (r) => money(r.empVolume), TotalEmpAmount: (r) => money(r.empAmount),
              TotalCreditVolume: (r) => money(r.creditVolume), TotalCreditAmount: (r) => money(r.creditAmount), TotalCashCouponsV: (r) => money(r.repairVolume),
              TotalCashCouponsM: (r) => money(r.repairAmount), TotalNotICVolume: (r) => money(r.noCardVolume), TotalNotICAmount: (r) => money(r.noCardAmount),
            },
          },
        }} />
      <div style={{ textAlign: 'right', marginTop: 6 }}><a onClick={print}>Imprimer le rapport</a></div>
    </Win>
  )
}
