import { useCallback, useEffect, useState } from 'react'
import { Modal, message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { get, post } from '../../api/http'
import WinForm, { getLayout, useFields, type Option } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { formatAsn } from '../labels'
import { CAR_COLORS, CAR_TYPES, CERTIFICATES, EMP_CARD_TYPES, GROUP_TYPES, LIMIT_TYPES, num, specialPrice } from './common'

const L = getLayout('CardMake')

interface Prepare {
  groups: Option[]
  creditGroups: Option[]
  masterGroups: Option[]
  discounts: Option[]
  grades: Option[]
  stations: Option[]
  employees: Option[]
  regions: Option[]
  cities: Option[]
  limitStations: Option[]
  validTime: string
  person: { asn: string; account: string; id: string }
  credit: { asn: string; account: string; id: string }
  child: { asn: string; account: string; id: string }
  employee: { asn: string; account: string }
  repair: { asn: string; account: string }
  group: { asn: string; account: string; id: string }[]
}

/** Fenêtres masquées : lecteur de cartes et initialisation de puce (matériel). */
const HARDWARE = ['skinPanel1', 'PersonInitButton', 'EmpIniButton', 'GroupIniButton', 'CreditIniButton', 'ChilrenInit']

/** Création de carte (CardMake d'origine) : carte utilisateur, employé, de groupe, de crédit et carte secondaire de groupe. */
export default function CardMake({ onClose }: { onClose: () => void }) {
  const f = useFields()
  const [p, setP] = useState<Prepare | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const v = f.values

  const load = useCallback(async () => {
    const r = await get<Prepare>('/station/cardmake/prepare')
    setP(r)
    const vt = r.validTime
    f.patch({
      PersonGroupSelect: r.groups[0]?.value, PersonAsn: r.person.asn, PersonAccount: r.person.account, PersonID: r.person.id, PersonValidTime: vt,
      PersonCertificate: 1, CardNoColor: 1, CardNoType: 1, PersonDiscount: 100, PersonLimitOilType: '', LimitType: 0, PersonBirthDay: dayjs(),
      CreditAsn: r.credit.asn, CreditAccount: r.credit.account, CreditID: r.credit.id, CreditCertificate: 1, CreditDisCount: 100, CreditLimitOilType: '',
      CreditLimitType: 0, CreditBirthDay: dayjs(), CreditGroup: r.creditGroups[0]?.value,
      ChilrenCardAsn: r.child.asn, ChilrenCardAccount: r.child.account, ChilrenCardID: r.child.id, ChilrenCardCerTypeSelect: 1, ChilrenDisCount: 100,
      ChilrenLimitOilType: '', ChilrenLimitTypeSelect: 0, ChilrenBirthDay: dayjs(),
      EmpCardType: 0, StationSelect: r.stations[0]?.value, EmployeeSelect: r.employees[0]?.value, EmpAsn: r.employee.asn, EmpAccount: r.employee.account, EmpValidTime: vt,
      GroupTypeSelect: 0, GroupDisCount: 100, GroupLimitOil: '', GroupAsn: r.group[0].asn, GroupAccount: r.group[0].account, GroupID: r.group[0].id, GroupValidTime: vt,
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  useEffect(() => {
    load()
  }, [load])

  const onChange = async (name: string, value: unknown) => {
    f.set(name, value)
    if (!p) return
    if (name === 'EmpCardType') {
      const n = value === 1 ? p.repair : p.employee
      f.patch({ EmpAsn: n.asn, EmpAccount: n.account })
    }
    if (name === 'GroupTypeSelect') {
      const g = p.group[Number(value)]
      f.patch({ GroupAsn: g.asn, GroupAccount: g.account, GroupID: g.id })
    }
    if (name === 'MasterGroupSelect' || name === 'CreditGroup') {
      const g = await get<Record<string, unknown>>('/station/cardmake/group-info', { groupId: value })
      if (name === 'MasterGroupSelect') f.patch({ MasterCardID: g.code ?? g.groupId, MasterCardName: g.name })
      else f.patch({ CreditLine: g.creditLines ?? '', CreditBalance: g.balance ?? '', BillDate: g.dateAccount ?? '', RepayDate: g.dateRepayment ?? '' })
    }
    if (['LimitType', 'CreditLimitType', 'ChilrenLimitTypeSelect'].includes(name)) {
      const target = name === 'LimitType' ? 'PersonStation' : name === 'CreditLimitType' ? 'CreditLimitStations' : 'ChildLimitStation'
      f.set(target, undefined)
    }
  }

  const limitOptions = (type: unknown): Option[] => {
    if (!p) return []
    return Number(type) === 1 ? p.regions : Number(type) === 2 ? p.cities : Number(type) === 3 ? p.limitStations : []
  }

  const done = (r: Record<string, unknown>) => {
    Modal.success({
      title: 'Carte créée',
      content: <div>N° de carte : <b>{formatAsn(r.asn as string)}</b><br />Compte : {String(r.account)}<br />Remettez ce numéro au client : il paiera avec ce numéro et son mot de passe.</div>,
    })
    load()
  }

  const run = async (btn: string, url: string, body: Record<string, unknown>, pin: unknown, pin2?: unknown) => {
    if (pin2 !== undefined && pin !== pin2) return message.error('Les mots de passe ne correspondent pas')
    if (!/^\d{4,8}$/.test(String(pin ?? ''))) return message.error('Mot de passe de la carte : 4 à 8 chiffres')
    setBusy(btn)
    try {
      done(await post<Record<string, unknown>>(url, body))
    } finally {
      setBusy(null)
    }
  }

  const holder = (prefix: 'Person' | 'Credit' | 'Chilren') => {
    const k = (s: string) => v[s]
    if (prefix === 'Person') {
      return {
        asn: k('PersonAsn'), password: k('PersonPassword'), name: k('PersonName'), code: k('PersonID'), certificateTypeId: k('PersonCertificate'),
        certificateNo: k('PersonCertificateNo'), phone: k('PersonPhone'), vehicleNo: k('PersonCardNo'), postCode: k('PersonPostCode'), address: k('PersonAddress'),
        email: k('PersonEmail'), birthday: (k('PersonBirthDay') as Dayjs | undefined)?.format('YYYY-MM-DDT00:00:00'), groupId: k('PersonGroupSelect'),
        vehicleColorId: k('CardNoColor'), vehicleTypeId: k('CardNoType'), discount: k('PersonDiscount'), limitVehicleNo: k('PersonLimitCardNo'), posUse: k('POSEnable'),
        limitOil: k('PersonLimitOilType'), limitVolume: num(k('PersonLimitVolume')), limitTime: num(k('PersonLimitDaliyTime')), limitAmount: num(k('PersonLimitAmountDaliy')),
        limitVolumeMonth: num(k('PersonLlimitVolumeMonth')), limitAmountMonth: num(k('PersonLimitAmountMonth')), limitMileage: num(k('PersonLimitMileage')),
        mileageTolerance: num(k('PersonMileageTolerance')), limitTypeId: k('LimitType'), useConfine: k('PersonStation') ? `${k('PersonStation')},` : '',
        specialPrice: specialPrice(v, 'Grade', 'GPrice'),
      }
    }
    if (prefix === 'Credit') {
      return {
        asn: k('CreditAsn'), password: k('CreditPwd'), name: k('CreditName'), code: k('CreditID'), certificateTypeId: k('CreditCertificate'),
        certificateNo: k('CreditCertificateNo'), phone: k('CreditPhone'), vehicleNo: k('CreditCardNo'), postCode: k('CreditPostCode'), address: k('CreditAddress'),
        email: k('CreditEmail'), birthday: (k('CreditBirthDay') as Dayjs | undefined)?.format('YYYY-MM-DDT00:00:00'), groupId: k('CreditGroup') ?? 100000001,
        discount: k('CreditDisCount'), limitVehicleNo: k('CreditLimitCardNo'), posUse: k('CreditPosEnable'), limitOil: k('CreditLimitOilType'),
        limitVolume: num(k('CreditLimitVolume')), limitTime: num(k('CreditLimitTime')), limitAmount: num(k('CreditLimitAmount')),
        limitVolumeMonth: num(k('CreditLimitVolumeMonth')), limitAmountMonth: num(k('CreditLimitAmountMonth')), limitMileage: num(k('CreditLimitMileage')),
        mileageTolerance: num(k('CreditMileageTolerance')), limitTypeId: k('CreditLimitType'), useConfine: k('CreditLimitStations') ? `${k('CreditLimitStations')},` : '',
        creditLine: num(k('CreditLine')), billDate: num(k('BillDate')), repayDay: num(k('RepayDate')),
      }
    }
    return {
      asn: k('ChilrenCardAsn'), password: k('ChilrenCardPassword'), name: k('ChilrenCardName'), code: k('ChilrenCardID'),
      certificateTypeId: k('ChilrenCardCerTypeSelect'), certificateNo: k('ChilrenCardCertificateNo'), phone: k('ChilrenPhone'), vehicleNo: k('ChilrenCarNo'),
      postCode: k('ChilrenPostCode'), address: k('ChilrenAddress'), email: k('ChilrenEmail'),
      birthday: (k('ChilrenBirthDay') as Dayjs | undefined)?.format('YYYY-MM-DDT00:00:00'), groupId: k('MasterGroupSelect'), discount: k('ChilrenDisCount'),
      limitVehicleNo: k('ChilrenIsLimitCardNo'), posUse: k('ChilrenPosEnable'), limitOil: k('ChilrenLimitOilType'), limitVolume: num(k('ChilrenLimitVolume')),
      limitTime: num(k('ChilrenLimitTime')), limitAmount: num(k('ChilrenLimitAmount')), limitVolumeMonth: num(k('ChilrenLimitVolumeMonth')),
      limitAmountMonth: num(k('ChilrenLimitAmountMonth')), limitMileage: num(k('ChilrenLimitMileager')), mileageTolerance: num(k('ChilrenMileagerTolerance')),
      limitTypeId: k('ChilrenLimitTypeSelect'), useConfine: k('ChildLimitStation') ? `${k('ChildLimitStation')},` : '',
    }
  }

  const gradeOpts = p?.grades ?? []
  const options: Record<string, Option[]> = {
    PersonGroupSelect: p?.groups ?? [], PersonCertificate: CERTIFICATES, CardNoColor: CAR_COLORS, CardNoType: CAR_TYPES, PersonDiscount: p?.discounts ?? [],
    PersonLimitOilType: gradeOpts, LimitType: LIMIT_TYPES, PersonStation: limitOptions(v.LimitType),
    CreditGroup: p?.creditGroups ?? [], CreditCertificate: CERTIFICATES, CreditDisCount: p?.discounts ?? [], CreditLimitOilType: gradeOpts,
    CreditLimitType: LIMIT_TYPES, CreditLimitStations: limitOptions(v.CreditLimitType),
    MasterGroupSelect: p?.masterGroups ?? [], ChilrenCardCerTypeSelect: CERTIFICATES, ChilrenDisCount: p?.discounts ?? [], ChilrenLimitOilType: gradeOpts,
    ChilrenLimitTypeSelect: LIMIT_TYPES, ChildLimitStation: limitOptions(v.ChilrenLimitTypeSelect),
    EmpCardType: EMP_CARD_TYPES, StationSelect: p?.stations ?? [], EmployeeSelect: p?.employees ?? [],
    GroupTypeSelect: GROUP_TYPES, GroupDisCount: p?.discounts ?? [], GroupLimitOil: gradeOpts,
  }
  for (let i = 1; i <= 7; i++) {
    options[`Grade${i}`] = gradeOpts
    options[`EGrade${i}`] = gradeOpts
  }

  const gt = Number(v.GroupTypeSelect ?? 0)
  const hidden = [...HARDWARE, 'groupBox13']
  if (gt !== 1) hidden.push('CreditBox')
  if (gt !== 2) hidden.push('LimitMinLabel', 'LimitMinBalance', 'label110')

  return (
    <Win layout={L} onClose={onClose} title="Création de carte">
      <WinForm layout={L} values={v} onChange={onChange} options={options} hidden={hidden} busy={busy}
        readOnly={['PersonAsn', 'PersonValidTime', 'PersonAccount', 'CreditAsn', 'CreditAccount', 'CreditBalance', 'ChilrenCardAsn', 'ChilrenCardAccount',
          'MasterCardID', 'MasterCardName', 'EmpAsn', 'EmpValidTime', 'EmpAccount', 'GroupAsn', 'GroupAccount', 'GroupID', 'GroupValidTime', 'GroupUseAmount', 'GroupBalance']}
        passwords={['PersonPassword', 'CreditPwd', 'ChilrenCardPassword', 'EmpPassword', 'EmpPasswordConfirm', 'GroupPassword']}
        texts={{ label11: 'Choisissez d’abord le groupe', label110: 'Seuil de solde minimum de la carte principale', LimitMinLabel: 'Solde minimum' }}
        buttons={{
          PersonButton: () => run('PersonButton', '/station/cardmake/person', holder('Person'), v.PersonPassword),
          CreditButton: () => run('CreditButton', '/station/cardmake/credit', holder('Credit'), v.CreditPwd),
          ChilrenConfirm: () => run('ChilrenConfirm', '/station/cardmake/child', holder('Chilren'), v.ChilrenCardPassword),
          EmployeeConfirm: () => run('EmployeeConfirm', '/station/cardmake/employee', {
            employeeId: v.EmployeeSelect, repair: v.EmpCardType === 1, password: v.EmpPassword, posUse: v.EmpPosEnable,
            specialPrice: specialPrice(v, 'EGrade', 'EPrice'),
          }, v.EmpPassword, v.EmpPasswordConfirm),
          GroupButton: () => run('GroupButton', '/station/cardmake/group', {
            groupType: gt, password: v.GroupPassword, companyName: v.GroupName, certificateNo: v.GroupCertificateNo, manager: v.GroupManage,
            phone: v.GroupPhone, fax: v.GroupFax, postCode: v.GroupPostCode, address: v.GroupAddress, email: v.GroupEamil, discount: v.GroupDisCount,
            limitOil: v.GroupLimitOil, limitVolume: num(v.GroupLimitVolume), limitTime: num(v.GroupLimitTime), limitAmountDay: num(v.GroupLimitAmountDay),
            limitVolumeMonth: num(v.GroupLimitVolumeMonth), limitAmountMonth: num(v.GroupLimitAmountMonth), creditLine: num(v.GroupCreditLine),
            billDate: num(v.GroupCreateDay), repayDay: num(v.GroupRepayDay), limitMinBalance: num(v.LimitMinBalance),
          }, v.GroupPassword),
        }} />
    </Win>
  )
}
