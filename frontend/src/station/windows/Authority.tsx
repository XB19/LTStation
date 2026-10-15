import { useCallback, useEffect, useState } from 'react'
import { message } from 'antd'
import { get, post, put } from '../../api/http'
import WinForm, { getLayout, useFields } from '../winforms/WinForm'
import Win from '../winforms/Win'

type Row = Record<string, unknown>
const L = getLayout('Authority')
const LA = getLayout('AddAuthority')

/** Cases à cocher → n° de droit (1 à 24) du logiciel d'origine. */
export const RIGHT_BOXES: Record<string, number> = {
  PriceBox: 1, OilTransactionBox: 2, SocreBox: 3, MakeCardBox: 4, EmpBox: 5, DisCountBox: 6, CardUpdateBox: 7, ShiftBox: 8, KeyManagementBox: 9,
  CardLostBox: 10, DailyBox: 11, PSAMBox: 12, CardChangeBox: 13, MonthBox: 14, BusinessBox: 15, DepositBox: 16, CardSearchBox: 17, PasswodLostBox: 18,
  SystemSet: 19, StationManager: 20, Communication: 21, ExtendManager: 22, UserCheck: 23, AuthorityBox: 24, checkBox1: 24,
}
/** Droits liés au matériel, masqués : 9 clé secrète, 12 PSAM, 21 communication. */
const HARDWARE = ['KeyManagementBox', 'PSAMBox', 'Communication']

const toBoxes = (content: string) => {
  const set = new Set(content.split(',').map((x) => Number(x.trim())))
  return Object.fromEntries(Object.entries(RIGHT_BOXES).map(([k, n]) => [k, set.has(n)]))
}
const fromBoxes = (v: Record<string, unknown>) => [...new Set(Object.entries(RIGHT_BOXES).filter(([k]) => v[k]).map(([, n]) => n))]

/** Gestion des droits (Authority d'origine). */
export default function Authority({ onClose }: { onClose: () => void }) {
  const f = useFields()
  const [list, setList] = useState<Row[]>([])
  const [add, setAdd] = useState(false)
  const load = useCallback(async () => {
    const r = await get<Row[]>('/station/authorities')
    setList(r)
    return r
  }, [])
  useEffect(() => {
    load().then((r) => r[0] && f.patch({ AuthoritySelect: r[0].id, ...toBoxes(String(r[0].content)) }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load])
  return (
    <Win layout={L} onClose={onClose} title="Gestion des droits">
      <WinForm layout={L} values={f.values} hidden={HARDWARE}
        onChange={(n, v) => {
          if (n === 'AuthoritySelect') {
            const a = list.find((x) => x.id === v)
            f.patch({ AuthoritySelect: v, ...toBoxes(String(a?.content ?? '')) })
          } else f.set(n, v)
        }}
        options={{ AuthoritySelect: list.map((a) => ({ value: a.id as number, label: String(a.name) })) }}
        buttons={{
          AddAuthority: () => setAdd(true),
          ShiftClose: onClose,
          Confirm: async () => {
            if (!f.values.AuthoritySelect) return message.warning('Créez d’abord un profil')
            await put(`/station/authorities/${f.values.AuthoritySelect}`, { rights: fromBoxes(f.values) })
            message.success('Droits enregistrés')
            load()
          },
        }} />
      {add && <AddAuthority onClose={() => { setAdd(false); load() }} />}
    </Win>
  )
}

function AddAuthority({ onClose }: { onClose: () => void }) {
  const f = useFields()
  return (
    <Win layout={LA} onClose={onClose} title="Ajouter un profil de droits">
      <WinForm layout={LA} values={f.values} onChange={f.set} hidden={HARDWARE}
        buttons={{
          ShiftClose: onClose,
          Confirm: async () => {
            if (!f.values.AuthorityName) return message.warning('Saisissez le nom du profil')
            await post('/station/authorities', { name: f.values.AuthorityName, rights: fromBoxes(f.values) })
            message.success('Profil créé')
            onClose()
          },
        }} />
    </Win>
  )
}
