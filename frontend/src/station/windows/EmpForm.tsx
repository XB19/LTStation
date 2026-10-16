import { useCallback, useEffect, useState } from 'react'
import { Button, Modal, message } from 'antd'
import { del, get, post, put } from '../../api/http'
import WinForm, { getLayout, useFields } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { useStation } from '../auth'

type Row = Record<string, unknown>
const L = getLayout('EmpForm')
const LC = getLayout('CreateEmployee')
const LU = getLayout('EmpUpdate')

/** Gestion des employés (EmpForm d'origine) : double-cliquez sur une ligne pour la modifier. */
export default function EmpForm({ onClose }: { onClose: () => void }) {
  const [rows, setRows] = useState<Row[]>([])
  const [auths, setAuths] = useState<Row[]>([])
  const [edit, setEdit] = useState<Row | 'new' | null>(null)
  const load = useCallback(async () => {
    setRows(await get<Row[]>('/station/employees'))
    setAuths(await get<Row[]>('/station/authorities'))
  }, [])
  useEffect(() => {
    load()
  }, [load])
  return (
    <Win layout={L} onClose={onClose} title="Gestion des employés">
      <WinForm layout={L} values={{}} onChange={() => undefined}
        buttons={{ AddEmp: () => setEdit('new'), Confirm: onClose }}
        grids={{
          EmpView: {
            rows, rowKey: 'employeeId', onDoubleClick: setEdit,
            value: { ID: (r) => r.employeeNo, OilType: (r) => r.name, LoginName: (r) => r.loginName, Type: (r) => r.authorityName ?? 'Tous les droits' },
          },
        }} />
      {edit && <EmployeeEdit row={edit === 'new' ? null : edit} auths={auths} onClose={(changed) => { setEdit(null); if (changed) load() }} />}
    </Win>
  )
}

function EmployeeEdit({ row, auths, onClose }: { row: Row | null; auths: Row[]; onClose: (changed: boolean) => void }) {
  const { me } = useStation()
  const layout = row ? LU : LC
  const f = useFields(row ? {
    EmpID: row.employeeNo, StationSelect: me?.station.stationId, PersonName: row.name, LoginName: row.loginName, Password: '',
    Authority: row.authId ? Number(row.authId) : 0, CenterLogin: !!row.centerLogin,
  } : { StationSelect: me?.station.stationId, BaseAuthority: 0 })
  const v = f.values
  const authOptions = [{ value: 0, label: 'Tous les droits' }, ...auths.map((a) => ({ value: a.id as number, label: String(a.name) }))]
  return (
    <Win layout={layout} title={row ? "Modifier l'employé" : 'Nouvel employé'} onClose={() => onClose(false)}>
      <WinForm layout={layout} values={v} onChange={f.set}
        options={{ StationSelect: [{ value: me?.station.stationId ?? 0, label: me?.station.name ?? '' }], BaseAuthority: authOptions, Authority: authOptions }}
        readOnly={['EmpID']} disabled={['StationSelect']} passwords={['Password']}
        texts={row ? { label4: 'Mot de passe (vide = inchangé)' } : undefined}
        buttons={{
          SaveButton: async () => {
            const auth = Number(row ? v.Authority : v.BaseAuthority) || null
            const body = { name: v.PersonName, loginName: v.LoginName, password: v.Password, authorityId: auth, centerLogin: v.CenterLogin }
            if (row) await put(`/station/employees/${row.employeeId}`, body)
            else await post('/station/employees', body)
            message.success('Employé enregistré')
            onClose(true)
          },
        }} />
      {row && (
        <div style={{ textAlign: 'right', marginTop: 8 }}>
          <Button type="link" danger onClick={() => Modal.confirm({
            title: 'Supprimer cet employé ?', okText: 'Supprimer', okType: 'danger', cancelText: 'Annuler',
            onOk: async () => { await del(`/station/employees/${row.employeeId}`); onClose(true) },
          })}>Supprimer l'employé</Button>
        </div>
      )}
    </Win>
  )
}
