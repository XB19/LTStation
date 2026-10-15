import { useCallback, useEffect, useState } from 'react'
import { Modal, message } from 'antd'
import dayjs from 'dayjs'
import { get, post } from '../../api/http'
import WinForm, { getLayout, useFields, type Option } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { formatAsn } from '../labels'

type Row = Record<string, unknown>
const L = getLayout('CardLost')
const LE = getLayout('EmpLost')

/** Types de carte (CardTypeSelect) et de pièce (TypeSelect) du logiciel d'origine. */
export const CARD_TYPES: Option[] = ['Carte prépayée', 'Carte employé', 'Carte de crédit', 'Carte de groupe', 'Carte de crédit de groupe', 'Carte principale',
  'Carte secondaire'].map((label, value) => ({ value, label }))
export const CERT_TYPES: Option[] = ["N° d'identité", 'Permis de conduire', 'Carte officielle', 'Passeport', 'Carte de travail', 'Autre']
  .map((label, i) => ({ value: i, label }))

const fmt = (v: unknown) => (v ? dayjs(v as string).format('YYYY-MM-DD HH:mm:ss') : '')

/** Déclaration de perte (CardLost d'origine) : cartes clients et de groupe, et cartes employé. */
export default function CardLost({ onClose }: { onClose: () => void }) {
  const f = useFields({ TypeSelect: 0, CardTypeSelect: 0 })
  const [asns, setAsns] = useState<string[]>([])
  const [emp, setEmp] = useState<Row[]>([])
  const [empLost, setEmpLost] = useState<Row | null>(null)
  const loadEmp = useCallback(() => get<Row[]>('/station/cards/employee-cards').then(setEmp), [])
  useEffect(() => {
    loadEmp()
  }, [loadEmp])

  const select = async (asn: string) => {
    f.set('LostCardNoSelect', asn)
    const r = await get<Row>(`/station/cards/${asn}/info`)
    f.patch({ LostCardNoSelect: asn, UserCardName: r.holder, UserID: r.userId, CardTime: fmt(r.issueDatetime), CardOpen: r.stationName })
  }

  return (
    <Win layout={L} onClose={onClose} title="Carte perdue">
      <WinForm layout={L} values={f.values}
        onChange={(n, v) => (n === 'LostCardNoSelect' ? select(v as string) : f.set(n, v))}
        options={{ TypeSelect: CERT_TYPES, CardTypeSelect: CARD_TYPES, LostCardNoSelect: asns.map((a) => ({ value: a, label: formatAsn(a) })) }}
        readOnly={['UserCardName', 'CardTime', 'CardOpen']}
        buttons={{
          SearchButton: async () => {
            const v = f.values
            const r = await get<string[]>('/station/cards/lookup', {
              cardType: v.CardTypeSelect, certType: Number(v.TypeSelect) + 1, certNo: v.TypeNo ?? '', name: v.UserName ?? '', phone: v.SPhone ?? '',
            })
            setAsns(r)
            if (r.length) select(r[0])
            else {
              f.patch({ LostCardNoSelect: undefined, UserCardName: '', CardTime: '', CardOpen: '' })
              message.info('Aucune carte en service ne correspond')
            }
          },
          ConfirmButton: () => {
            const asn = f.values.LostCardNoSelect as string
            if (!asn) return message.warning('Choisissez la carte')
            Modal.confirm({
              title: 'Déclarer cette carte perdue ?', content: formatAsn(asn), okText: 'Valider', okType: 'danger', cancelText: 'Annuler',
              onOk: async () => {
                await post(`/station/cards/${asn}/lost`)
                message.success('Carte déclarée perdue')
                setAsns((a) => a.filter((x) => x !== asn))
                f.patch({ LostCardNoSelect: undefined, UserCardName: '', CardTime: '', CardOpen: '' })
              },
            })
          },
        }}
        grids={{
          EmpView: {
            rows: emp, rowKey: 'asn', onDoubleClick: setEmpLost,
            value: {
              dataGridViewTextBoxColumn1: (r) => formatAsn(r.asn as string), dataGridViewTextBoxColumn2: (r) => r.name, CardOperate: (r) => r.stationName,
              dataGridViewTextBoxColumn3: (r) => fmt(r.issueDatetime), dataGridViewTextBoxColumn4: () => 'Normale',
            },
          },
        }} />
      {empLost && (
        <Win layout={LE} title="Carte employé perdue" onClose={() => setEmpLost(null)}>
          <WinForm layout={LE} values={{ AsnNo: formatAsn(empLost.asn as string), StationSelect: 1, EmpID: String(empLost.employeeId), EmpName: empLost.name,
            IssueTime: fmt(empLost.issueDatetime) }} onChange={() => undefined}
            options={{ StationSelect: [{ value: 1, label: String(empLost.stationName ?? '') }] }}
            readOnly={['AsnNo', 'EmpID', 'EmpName', 'IssueTime']} disabled={['StationSelect']}
            buttons={{
              LostButton: async () => {
                await post(`/station/cards/${empLost.asn}/lost`)
                message.success('Carte employé déclarée perdue')
                setEmpLost(null)
                loadEmp()
              },
            }} />
        </Win>
      )}
    </Win>
  )
}
