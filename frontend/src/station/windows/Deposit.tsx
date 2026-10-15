import { useCallback, useEffect, useState } from 'react'
import { Modal, message } from 'antd'
import { get, post, type PageResult } from '../../api/http'
import WinForm, { getLayout, useFields } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { useStation } from '../auth'
import { formatAsn, money } from '../labels'
import { askPin } from './PinPrompt'
import { printTicket } from './printing'

type Row = Record<string, unknown>
const L = getLayout('Deposit')
const LR = getLayout('RepayFrom')

const PAY_TYPES = [{ value: 0, label: 'Espèces' }, { value: 1, label: 'Banque' }, { value: 2, label: 'Paiement en ligne' }]
const MASTER_TYPES = [{ value: 0, label: 'Carte utilisateur' }, { value: 1, label: 'Carte de crédit' }, { value: 2, label: 'Carte principale' }]
const ST_TYPES = [{ value: 0, label: 'Virement' }, { value: 1, label: 'Réception' }]
const BILL_STATUS: Record<number, string> = { 0: 'Non remboursée', 1: 'Remboursement partiel', 2: 'Remboursée' }
const amt = (v: unknown) => (v === undefined || v === '' ? undefined : Number(String(v).replace(',', '.')))

/** Recharge / débit (Deposit d'origine) : saisir le n° de carte puis « Lire la carte » remplace la lecture de la puce. */
export default function Deposit({ onClose }: { onClose: () => void }) {
  const { me } = useStation()
  const f = useFields({ PayMent: 0, MasterPayMent: 0, CardType: 2, STTypeSelect: 0 })
  const v = f.values
  const [busy, setBusy] = useState<string | null>(null)
  const [masterAsns, setMasterAsns] = useState<string[]>([])
  const [members, setMembers] = useState<string[]>([])
  const [bills, setBills] = useState<PageResult<Row>>({ rows: [], total: 0 })
  const [billPage, setBillPage] = useState(1)
  const [repay, setRepay] = useState<Row | null>(null)
  const [repayAmount, setRepayAmount] = useState('')

  const loadBills = useCallback(() => get<PageResult<Row>>('/station/balance/bills', { page: billPage, size: 10 }).then(setBills), [billPage])
  useEffect(() => {
    loadBills()
  }, [loadBills])

  const read = async (field: string) => {
    const asn = String(v[field] ?? '').replace(/\s/g, '')
    if (asn.length < 16) {
      message.warning('Saisissez le numéro de la carte')
      return null
    }
    return get<Row>(`/station/balance/card/${asn}`)
  }

  const act = async (btn: string, fn: () => Promise<void>) => {
    setBusy(btn)
    try {
      await fn()
    } finally {
      setBusy(null)
    }
  }

  const depositTicket = (asn: string, amount: unknown, gift: unknown, balance: unknown) => printTicket('Recharge', [
    ['N° de carte', formatAsn(asn)], ['Montant de la recharge', money(amount)], ['Bonus', money(gift)], ['Solde', money(balance)],
    ['Date de la recharge', new Date().toLocaleString('fr-FR')], ['Opérateur', me?.employeeName],
  ], me?.station.name)

  const debit = (btn: string, asnField: string, amountField: string, kind: 'CHARGE' | 'CONSUME', clear: string[]) => act(btn, async () => {
    const asn = String(v[asnField] ?? '')
    if (!asn) return void message.warning('Lisez d’abord la carte')
    if (!(amt(v[amountField]) ?? 0)) return void message.warning('Saisissez le montant')
    const pin = await askPin()
    if (pin === null) return
    const r = await post<Row>('/station/balance/debit', { asn, amount: amt(v[amountField]), kind, pin })
    message.success('Opération réussie')
    printTicket(kind === 'CONSUME' ? 'Consommation' : 'Débit', [['N° de carte', formatAsn(asn)], ['Montant débité', money(r.amount)], ['Solde', money(r.balance)],
      ['Date', new Date().toLocaleString('fr-FR')], ['Opérateur', me?.employeeName]], me?.station.name)
    f.patch(Object.fromEntries(clear.map((k) => [k, ''])))
  })

  return (
    <Win layout={L} onClose={onClose} title="Recharge / débit">
      <WinForm layout={L} values={v} busy={busy}
        onChange={async (n, val) => {
          f.set(n, val)
          if (n === 'AsnSelect') {
            const r = await get<Row>(`/station/balance/card/${val}`)
            f.patch({ AsnSelect: val, MasterBalance: money(r.balance), UserNameReturn: r.holder })
          }
          if (n === 'SaveToAsn') {
            const r = await get<Row>(`/station/balance/card/${val}`)
            f.patch({ SaveToAsn: val, SaveToName: r.holder })
          }
        }}
        options={{
          PayMent: PAY_TYPES, MasterPayMent: PAY_TYPES, CardType: MASTER_TYPES, STTypeSelect: ST_TYPES,
          AsnSelect: masterAsns.map((a) => ({ value: a, label: formatAsn(a) })), SaveToAsn: members.map((a) => ({ value: a, label: formatAsn(a) })),
        }}
        readOnly={['UserName', 'Balance', 'DeductionName', 'DeductionBalance', 'ConsumeName', 'ConsumeBalance', 'TransferName', 'TransferBalance', 'TransferAmount',
          'MasterBalance', 'UserNameReturn', 'STBalance', 'SaveToName']}
        disabled={v.STTypeSelect === 1 ? ['STAmount', 'STTypeSelect'] : ['STTypeSelect']}
        hidden={v.STTypeSelect === 1 ? ['label33', 'SaveToAsn', 'label28', 'SaveToName'] : []}
        buttons={{
          SaveButton: () => act('SaveButton', async () => {
            const r = await read('ASN')
            if (r) f.patch({ ASN: r.asn, UserName: r.holder, Balance: money(r.balance) })
          }),
          DepositButton: () => act('DepositButton', async () => {
            if (!v.ASN || !v.UserName) return void message.warning('Lisez d’abord la carte')
            const r = await post<Row>('/station/balance/deposit', { asn: v.ASN, amount: amt(v.DepositAmount), gift: amt(v.DiscountAmount), payType: v.PayMent, score: amt(v.Score) })
            message.success('Recharge réussie')
            depositTicket(String(v.ASN), r.amount, r.gift, r.balance)
            f.patch({ ASN: '', UserName: '', Balance: '', DepositAmount: '', DiscountAmount: '', Score: '' })
          }),
          CardReader: () => act('CardReader', async () => {
            const r = await read('DeductionASN')
            if (r) f.patch({ DeductionASN: r.asn, DeductionName: r.holder, DeductionBalance: money(r.balance) })
          }),
          DeductionButton: () => debit('DeductionButton', 'DeductionASN', 'DeductionAmount', 'CHARGE', ['DeductionASN', 'DeductionName', 'DeductionBalance', 'DeductionAmount']),
          CardConsumeRead: () => act('CardConsumeRead', async () => {
            const r = await read('ConsumeASN')
            if (r) f.patch({ ConsumeASN: r.asn, ConsumeName: r.holder, ConsumeBalance: money(r.balance) })
          }),
          ConsumeButton: () => debit('ConsumeButton', 'ConsumeASN', 'ConsumeAmount', 'CONSUME', ['ConsumeASN', 'ConsumeName', 'ConsumeBalance', 'ConsumeAmount']),
          TransferReader: () => act('TransferReader', async () => {
            const r = await read('TransferAsn')
            if (r) f.patch({ TransferAsn: r.asn, TransferName: r.holder, TransferBalance: money(r.balance), TransferAmount: money(r.pendingOnline) })
          }),
          TransferBtn: () => act('TransferBtn', async () => {
            if (!v.TransferAsn || !v.TransferName) return void message.warning('Lisez d’abord la carte')
            const r = await post<Row>(`/station/balance/online/${v.TransferAsn}`)
            message.success(`Recharge en ligne de ${money(r.amount)} reportée sur la carte`)
            f.patch({ TransferBalance: money(r.balance), TransferAmount: money(0) })
          }),
          QueryButton: () => act('QueryButton', async () => {
            const r = await get<string[]>('/station/balance/search', { cardType: v.CardType, name: v.MasterName ?? '', phone: v.PhoneSearch ?? '', certNo: v.CNoSearch ?? '', carNo: v.CarNoSearch ?? '' })
            setMasterAsns(r)
            if (!r.length) message.info('Aucune carte trouvée')
            else {
              const c = await get<Row>(`/station/balance/card/${r[0]}`)
              f.patch({ AsnSelect: r[0], MasterBalance: money(c.balance), UserNameReturn: c.holder })
            }
          }),
          MasterDeposter: () => act('MasterDeposter', async () => {
            if (!v.AsnSelect) return void message.warning('Recherchez la carte')
            const r = await post<Row>('/station/balance/deposit', { asn: v.AsnSelect, amount: amt(v.MasterAmn), gift: amt(v.MasterDisCount), payType: v.MasterPayMent, score: amt(v.MasterScore) })
            message.success('Recharge réussie')
            depositTicket(String(v.AsnSelect), r.amount, r.gift, r.balance)
            f.patch({ MasterBalance: money(r.balance), MasterAmn: '', MasterDisCount: '', MasterScore: '' })
          }),
          STRead: () => act('STRead', async () => {
            const r = await read('STAsn')
            if (!r) return
            const group = String(r.asn).startsWith('010002') || String(r.asn).startsWith('010014')
            f.patch({ STAsn: r.asn, STBalance: money(r.balance), STTypeSelect: group ? 0 : 1, STAmount: group ? '' : money(r.pendingTransfer), SaveToAsn: undefined, SaveToName: '' })
            setMembers(group ? ((r.members as string[]) ?? []) : [])
          }),
          STConfirm: () => act('STConfirm', async () => {
            if (!v.STAsn) return void message.warning('Lisez d’abord la carte')
            if (v.STTypeSelect === 1) {
              const r = await post<Row>(`/station/balance/transfer-load/${v.STAsn}`)
              message.success(`Virement de ${money(r.amount)} reçu`)
              f.patch({ STBalance: money(r.balance), STAmount: money(0) })
            } else {
              if (!v.SaveToAsn) return void message.warning('Choisissez la carte qui reçoit le virement')
              const r = await post<Row>('/station/balance/transfer', { asn: v.STAsn, toAsn: v.SaveToAsn, amount: amt(v.STAmount) })
              message.success('Virement effectué')
              f.patch({ STBalance: money(r.balance), STAmount: '' })
            }
          }),
        }}
        grids={{
          RepayView: {
            rows: bills.rows, rowKey: 'id', onDoubleClick: (r) => { setRepay(r); setRepayAmount(String(Number(r.amount ?? 0) - Number(r.repayAmount ?? 0))) },
            pagination: { current: billPage, total: bills.total, pageSize: 10, onChange: setBillPage, size: 'small' },
            value: {
              ID: (r) => r.id, GroupName: (r) => r.groupName, GroupID: (r) => r.groupId, GroupAsn: (r) => formatAsn(r.asn as string), OilType: (r) => r.billDate,
              LoginName: (r) => money(r.amount), RepayAmount: (r) => money(r.repayAmount), Statue: (r) => BILL_STATUS[Number(r.statue)] ?? r.statue,
            },
          },
        }} />
      {repay && (
        <Win layout={LR} title="Remboursement" onClose={() => setRepay(null)}>
          <WinForm layout={LR} values={{ RepayBillID: repay.id, GroupName: repay.groupName, BillDate: repay.billDate, Amount: money(repay.amount), RepayAmount: repayAmount }}
            onChange={(n, val) => n === 'RepayAmount' && setRepayAmount(String(val))}
            readOnly={['RepayBillID', 'GroupName', 'BillDate', 'Amount']}
            buttons={{
              skinButton1: () => setRepay(null),
              ReapyBtn: () => Modal.confirm({
                title: `Rembourser ${repayAmount} ?`, okText: 'Rembourser', cancelText: 'Annuler',
                onOk: async () => {
                  await post(`/station/balance/bills/${repay.id}/repay`, { amount: amt(repayAmount) })
                  message.success('Remboursement enregistré')
                  setRepay(null)
                  loadBills()
                },
              }),
            }} />
        </Win>
      )}
    </Win>
  )
}
