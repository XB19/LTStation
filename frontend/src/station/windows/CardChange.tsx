import { useState } from 'react'
import { Modal, message } from 'antd'
import { get, post } from '../../api/http'
import WinForm, { getLayout, useFields } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { formatAsn, money } from '../labels'
import { CARD_TYPES, CERT_TYPES } from './CardLost'

type Row = Record<string, unknown>
const L = getLayout('CardChange')

/** Remplacement de carte (CardChange d'origine) : la carte doit d'abord être déclarée perdue ; le nouveau numéro est attribué automatiquement. */
export default function CardChange({ onClose }: { onClose: () => void }) {
  const f = useFields({ TypeSelect: 0, CardTypeIndex: 0 })
  const [asns, setAsns] = useState<string[]>([])
  const [busy, setBusy] = useState<string | null>(null)

  const select = async (asn: string) => {
    const r = await get<Row>(`/station/cards/${asn}/info`)
    f.patch({ ChangeCardNo: asn, OldAsn: formatAsn(asn), UserName: r.holder, UserID: r.userId, Balance: money(r.balance), LogBalance: money(r.logBalance),
      UserCardName: r.holder, CardAsn: '' })
  }

  return (
    <Win layout={L} onClose={onClose} title="Remplacement de carte">
      <WinForm layout={L} values={f.values} busy={busy}
        onChange={(n, v) => (n === 'ChangeCardNo' ? select(v as string) : f.set(n, v))}
        hidden={['label10', 'CardTypeSelect', 'IniButton']}
        options={{ CardTypeIndex: CARD_TYPES, TypeSelect: CERT_TYPES, ChangeCardNo: asns.map((a) => ({ value: a, label: formatAsn(a) })) }}
        readOnly={['UserName', 'UserID', 'OldAsn', 'Balance', 'LogBalance', 'UserCardName', 'CardAsn']}
        buttons={{
          SearchButton: async () => {
            const v = f.values
            const r = await get<string[]>('/station/cards/lookup', {
              cardType: v.CardTypeIndex, certType: Number(v.TypeSelect) + 1, certNo: v.TypeNo ?? '', name: v.UserNameSearch ?? '', phone: v.PhoneSearch ?? '', lost: true,
            })
            setAsns(r)
            if (r.length) select(r[0])
            else message.info('Aucune carte perdue ne correspond : déclarez d’abord la carte perdue')
          },
          ChangeButton: () => {
            const asn = f.values.ChangeCardNo as string
            if (!asn) return message.warning('Recherchez et choisissez la carte à remplacer')
            Modal.confirm({
              title: 'Remplacer cette carte ?', content: `${formatAsn(asn)} — le solde est transféré sur la nouvelle carte.`, okText: 'Remplacer', cancelText: 'Annuler',
              onOk: async () => {
                setBusy('ChangeButton')
                try {
                  const r = await post<Row>(`/station/cards/${asn}/replace`)
                  f.set('CardAsn', formatAsn(r.asn as string))
                  setAsns((a) => a.filter((x) => x !== asn))
                  Modal.success({ title: 'Carte remplacée', content: <div>Nouveau n° de carte : <b>{formatAsn(r.asn as string)}</b></div> })
                } finally {
                  setBusy(null)
                }
              },
            })
          },
        }} />
    </Win>
  )
}
