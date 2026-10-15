import { useEffect, useState } from 'react'
import { message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { get, put } from '../../api/http'
import WinForm, { getLayout, useFields, type Option } from '../winforms/WinForm'
import Win from '../winforms/Win'
import { CAR_COLORS, CAR_TYPES, CERTIFICATES, GROUP_TYPES, LIMIT_TYPES, num, specialPrice } from './common'

const L = getLayout('CardUpDate')
type Row = Record<string, unknown>

interface Lists {
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
}

/** Prix spéciaux d'origine « ,CCCCHHHH… » → champs énergie / prix. */
function decodeSpecial(sp: unknown, gradePrefix: string, pricePrefix: string) {
  const out: Record<string, unknown> = {}
  String(sp ?? '').split(',').filter((x) => x.length >= 8).slice(0, 7).forEach((x, i) => {
    out[`${gradePrefix}${i + 1}`] = x.slice(0, x.length - 4)
    out[`${pricePrefix}${i + 1}`] = String(parseInt(x.slice(-4), 16))
  })
  return out
}

const s = (v: unknown) => (v === null || v === undefined ? '' : String(v))
const d = (v: unknown) => (v ? dayjs(v as string) : undefined)
const confine = (v: unknown) => String(v ?? '').split(',').filter(Boolean)[0]

/** Mise à jour de carte (CardUpDate d'origine) : saisir le n° de carte, « Lire la carte », modifier puis « Mettre à jour ». */
export default function CardUpDate({ onClose }: { onClose: () => void }) {
  const f = useFields()
  const v = f.values
  const [lists, setLists] = useState<Lists | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  useEffect(() => {
    get<Lists>('/station/cardmake/prepare').then(setLists)
  }, [])

  const read = async (asnField: string, expect: string[]) => {
    const asn = String(v[asnField] ?? '').replace(/\s/g, '')
    if (asn.length < 16) return message.warning('Saisissez le numéro de la carte')
    const r = await get<Row>(`/station/cardmake/details/${asn}`)
    if (!expect.includes(r.kind as string)) return message.error('Ce numéro ne correspond pas à ce type de carte')
    switch (r.kind) {
      case 'PERSON':
        f.patch({
          PersonGroupSelect: r.groupId, PersonAsn: r.asn, PersonValidTime: s(r.validDatetime).slice(0, 10), PersonAccount: r.accountNo, PersonPassword: '',
          PersonName: r.name, PersonID: r.code, PersonCertificate: r.certificateTypeId, PersonCertificateNo: r.certificateNo, PersonPhone: r.phone,
          PersonCardNo: r.vehicleNo, PersonPostCode: r.postCode, CardNoColor: r.vehicleColorId, PersonEmail: r.email, CardNoType: r.vehicleTypeId,
          PersonBirthDay: d(r.birthday), PersonAddress: r.address, PersonLimitCardNo: r.isLimitVehicleNo, UserPosEnable: r.memo1 === 'POS',
          PersonDiscount: Number(r.discount), PersonLimitOilType: r.limitOil ?? '', PersonLimitVolume: s(r.limitVolume), PersonLimitDaliyTime: s(r.limitTime),
          PersonLimitAmountDaliy: s(r.limitAmount), PersonLlimitVolumeMonth: s(r.limitVolumeMonthly), PersonLimitAmountMonth: s(r.limitAmountMonthly),
          PersonLimitMileage: s(r.limitMileage), PersonMileageTolerance: s(r.mileageTolerance), LimitType: r.limitTypeId ?? 0, PersonStation: confine(r.useConfine),
          ...decodeSpecial(r.specialPrice, 'Grade', 'GPrice'),
        })
        break
      case 'CREDIT':
        f.patch({
          CreditGroup: r.groupId, CreditLine: s(r.creditLines), CreditBalance: s(r.balance), BillDate: s(r.dateAccount), RepayDate: s(r.dateRepayment),
          CreditAsn: r.asn, CreditPwd: '', CreditAccount: r.accountNo, CreditName: r.name, CreditID: r.code, CreditCertificate: r.certificateTypeId,
          CreditCertificateNo: r.certificateNo, CreditPhone: r.phone, CreditCardNo: r.vehicleNo, CreditPostCode: r.postCode, CreditAddress: r.address,
          CreditBirthDay: d(r.birthday), CreditEmail: r.email, CreditLimitCardNo: r.isLimitVehicleNo, CreditPosEnable: r.memo1 === 'POS',
          CreditDisCount: Number(r.discount), CreditLimitOilType: r.limitOil ?? '', CreditLimitVolume: s(r.limitVolume), CreditLimitTime: s(r.limitTime),
          CreditLimitAmount: s(r.limitAmount), CreditLimitVolumeMonth: s(r.limitVolumeMonthly), CreditLimitAmountMonth: s(r.limitAmountMonthly),
          CreditLimitMileage: s(r.limitMileage), CreditMileageTolerance: s(r.mileageTolerance), CreditLimitType: r.limitTypeId ?? 0, CreditStation: confine(r.useConfine),
        })
        break
      case 'CHILD':
        f.patch({
          MasterGroup: r.groupId, ChildAsn: r.asn, ChildValidTime: s(r.validDatetime).slice(0, 10), ChildAccount: r.accountNo, ChildPassword: '', ChildName: r.name,
          ChildId: r.code, ChildCerTypeID: r.certificateTypeId, ChildCerTypeNo: r.certificateNo, ChildPhone: r.phone, ChildCarNo: r.vehicleNo,
          ChildPostCode: r.postCode, ChildAddress: r.address, ChildBirthDay: d(r.birthday), ChildEmail: r.email, ChilrenPosEnable: r.memo1 === 'POS',
          ChildIsLimitCarNo: r.isLimitVehicleNo, ChildDisCount: Number(r.discount), ChildLimitOil: r.limitOil ?? '', ChildLimitVolume: s(r.limitVolume),
          ChildLimitTime: s(r.limitTime), ChildLimitAmount: s(r.limitAmount), ChildLimitVolumeMonth: s(r.limitVolumeMonthly),
          ChildLimitAmountMonth: s(r.limitAmountMonthly), ChildLimitMileager: s(r.limitMileage), ChildMileageTolerance: s(r.mileageTolerance),
          ChildLimitType: r.limitTypeId ?? 0, ChildStation: confine(r.useConfine),
        })
        break
      case 'EMPLOYEE':
        f.patch({
          StationSelect: r.stationId, EmployeeSelect: r.employeeId, EmpAsn: r.asn, EmpValidTime: s(r.validDatetime).slice(0, 10), EmpAccount: s(r.account),
          EmpPassword: '', EmpPasswordConfirm: '', EmpPosEnable: r.memo1 === 'POS', ...decodeSpecial(r.specialPrice, 'EGrade', 'EPrice'),
        })
        break
      case 'GROUP':
        f.patch({
          GroupTypeSelect: s(r.asn).startsWith('010012') ? 1 : s(r.asn).startsWith('010014') ? 2 : 0, GroupDisCount: Number(r.discount), GroupAsn: r.asn,
          GroupValidTime: s(r.validDatetime).slice(0, 10), GroupAccount: r.accountNo, GroupPassword: '', GroupName: r.companyName, GroupID: r.groupCode,
          GroupCertificateNo: r.certificateNo, GroupManage: r.manager, GroupPhone: r.phone, GroupFax: r.fax, GroupPostCode: r.postCode, GroupAddress: r.address,
          GroupEamil: r.email, LimitMinBalance: s(r.minBalance), GroupLimitOil: r.limitOil ?? '', GroupLimitVolume: s(r.limitVolume), GroupLimitTime: s(r.limitTime),
          GroupLimitAmountDay: s(r.limitAmount), GroupLimitVolumeMonth: s(r.limitVolumeMonthly), GroupLimitAmountMonth: s(r.limitAmountMonthly),
          GroupCreditLine: s(r.creditLines), GroupBalance: s(r.balance), GroupCreateDay: s(r.dateAccount), GroupRepayDay: s(r.dateRepayment),
        })
        break
    }
    message.success('Carte lue')
  }

  const save = async (btn: string, url: string, body: Row, pin?: unknown, pin2?: unknown) => {
    if (pin && !/^\d{4,8}$/.test(String(pin))) return message.error('Nouveau mot de passe : 4 à 8 chiffres (laisser vide pour ne pas le changer)')
    if (pin2 !== undefined && pin !== pin2) return message.error('Les mots de passe ne correspondent pas')
    setBusy(btn)
    try {
      await put(url, body)
      message.success('Carte mise à jour')
    } finally {
      setBusy(null)
    }
  }

  const bday = (x: unknown) => (x as Dayjs | undefined)?.format('YYYY-MM-DDT00:00:00')
  const limitOpts = (t: unknown) => (!lists ? [] : Number(t) === 1 ? lists.regions : Number(t) === 2 ? lists.cities : Number(t) === 3 ? lists.limitStations : [])
  const grades = lists?.grades ?? []
  const options: Record<string, Option[]> = {
    PersonGroupSelect: lists?.groups ?? [], PersonCertificate: CERTIFICATES, CardNoColor: CAR_COLORS, CardNoType: CAR_TYPES, PersonDiscount: lists?.discounts ?? [],
    PersonLimitOilType: grades, LimitType: LIMIT_TYPES, PersonStation: limitOpts(v.LimitType),
    CreditGroup: lists?.creditGroups ?? [], CreditCertificate: CERTIFICATES, CreditDisCount: lists?.discounts ?? [], CreditLimitOilType: grades,
    CreditLimitType: LIMIT_TYPES, CreditStation: limitOpts(v.CreditLimitType),
    MasterGroup: lists?.masterGroups ?? [], ChildCerTypeID: CERTIFICATES, ChildDisCount: lists?.discounts ?? [], ChildLimitOil: grades, ChildLimitType: LIMIT_TYPES,
    ChildStation: limitOpts(v.ChildLimitType),
    StationSelect: lists?.stations ?? [], EmployeeSelect: lists?.employees ?? [],
    GroupTypeSelect: GROUP_TYPES, GroupDisCount: lists?.discounts ?? [], GroupLimitOil: grades,
  }
  for (let i = 1; i <= 7; i++) {
    options[`Grade${i}`] = grades
    options[`EGrade${i}`] = grades
  }
  const gt = Number(v.GroupTypeSelect ?? 0)
  const hidden = ['groupBox13']
  if (gt !== 1) hidden.push('CreditBox')
  if (gt !== 2) hidden.push('LimitMinLabel', 'LimitMinBalance')

  return (
    <Win layout={L} onClose={onClose} title="Mise à jour de carte">
      <WinForm layout={L} values={v} onChange={f.set} options={options} hidden={hidden} busy={busy}
        readOnly={['PersonValidTime', 'PersonAccount', 'CreditAccount', 'CreditBalance', 'ChildValidTime', 'ChildAccount', 'EmpValidTime', 'EmpAccount',
          'GroupValidTime', 'GroupAccount', 'GroupID', 'GroupUseAmount', 'GroupBalance']}
        disabled={['GroupTypeSelect', 'StationSelect', 'EmployeeSelect']}
        passwords={['PersonPassword', 'CreditPwd', 'ChildPassword', 'EmpPassword', 'EmpPasswordConfirm', 'GroupPassword']}
        texts={{ label11: 'Saisissez le n° de carte puis « Lire la carte »' }}
        buttons={{
          PersonUpdatetButton: () => read('PersonAsn', ['PERSON']),
          CreditIniButton: () => read('CreditAsn', ['CREDIT']),
          ChildIniButton: () => read('ChildAsn', ['CHILD']),
          EmpReadButton: () => read('EmpAsn', ['EMPLOYEE']),
          GroupIniButton: () => read('GroupAsn', ['GROUP']),
          PersonButton: () => save('PersonButton', `/station/cardmake/holder/${v.PersonAsn}`, {
            password: v.PersonPassword, name: v.PersonName, code: v.PersonID, certificateTypeId: v.PersonCertificate, certificateNo: v.PersonCertificateNo,
            phone: v.PersonPhone, vehicleNo: v.PersonCardNo, postCode: v.PersonPostCode, address: v.PersonAddress, email: v.PersonEmail,
            birthday: bday(v.PersonBirthDay), groupId: v.PersonGroupSelect, vehicleColorId: v.CardNoColor, vehicleTypeId: v.CardNoType, discount: v.PersonDiscount,
            limitVehicleNo: v.PersonLimitCardNo, posUse: v.UserPosEnable, limitOil: v.PersonLimitOilType, limitVolume: num(v.PersonLimitVolume),
            limitTime: num(v.PersonLimitDaliyTime), limitAmount: num(v.PersonLimitAmountDaliy), limitVolumeMonth: num(v.PersonLlimitVolumeMonth),
            limitAmountMonth: num(v.PersonLimitAmountMonth), limitMileage: num(v.PersonLimitMileage), mileageTolerance: num(v.PersonMileageTolerance),
            limitTypeId: v.LimitType, useConfine: v.PersonStation ? `${v.PersonStation},` : '', specialPrice: specialPrice(v, 'Grade', 'GPrice'),
          }, v.PersonPassword),
          CreditButton: () => save('CreditButton', `/station/cardmake/holder/${v.CreditAsn}`, {
            password: v.CreditPwd, name: v.CreditName, code: v.CreditID, certificateTypeId: v.CreditCertificate, certificateNo: v.CreditCertificateNo,
            phone: v.CreditPhone, vehicleNo: v.CreditCardNo, postCode: v.CreditPostCode, address: v.CreditAddress, email: v.CreditEmail,
            birthday: bday(v.CreditBirthDay), groupId: v.CreditGroup, discount: v.CreditDisCount, limitVehicleNo: v.CreditLimitCardNo, posUse: v.CreditPosEnable,
            limitOil: v.CreditLimitOilType, limitVolume: num(v.CreditLimitVolume), limitTime: num(v.CreditLimitTime), limitAmount: num(v.CreditLimitAmount),
            limitVolumeMonth: num(v.CreditLimitVolumeMonth), limitAmountMonth: num(v.CreditLimitAmountMonth), limitMileage: num(v.CreditLimitMileage),
            mileageTolerance: num(v.CreditMileageTolerance), limitTypeId: v.CreditLimitType, useConfine: v.CreditStation ? `${v.CreditStation},` : '',
            creditLine: num(v.CreditLine), billDate: num(v.BillDate), repayDay: num(v.RepayDate),
          }, v.CreditPwd),
          ChildUpdateButton: () => save('ChildUpdateButton', `/station/cardmake/holder/${v.ChildAsn}`, {
            password: v.ChildPassword, name: v.ChildName, code: v.ChildId, certificateTypeId: v.ChildCerTypeID, certificateNo: v.ChildCerTypeNo,
            phone: v.ChildPhone, vehicleNo: v.ChildCarNo, postCode: v.ChildPostCode, address: v.ChildAddress, email: v.ChildEmail,
            birthday: bday(v.ChildBirthDay), groupId: v.MasterGroup, discount: v.ChildDisCount, limitVehicleNo: v.ChildIsLimitCarNo, posUse: v.ChilrenPosEnable,
            limitOil: v.ChildLimitOil, limitVolume: num(v.ChildLimitVolume), limitTime: num(v.ChildLimitTime), limitAmount: num(v.ChildLimitAmount),
            limitVolumeMonth: num(v.ChildLimitVolumeMonth), limitAmountMonth: num(v.ChildLimitAmountMonth), limitMileage: num(v.ChildLimitMileager),
            mileageTolerance: num(v.ChildMileageTolerance), limitTypeId: v.ChildLimitType, useConfine: v.ChildStation ? `${v.ChildStation},` : '',
          }, v.ChildPassword),
          EmployeeUpdateButton: () => save('EmployeeUpdateButton', `/station/cardmake/employee/${v.EmpAsn}`, {
            employeeId: v.EmployeeSelect, password: v.EmpPassword, posUse: v.EmpPosEnable, specialPrice: specialPrice(v, 'EGrade', 'EPrice'),
          }, v.EmpPassword, v.EmpPasswordConfirm),
          GroupUpdateButton: () => save('GroupUpdateButton', `/station/cardmake/group/${v.GroupAsn}`, {
            groupType: gt, password: v.GroupPassword, companyName: v.GroupName, certificateNo: v.GroupCertificateNo, manager: v.GroupManage, phone: v.GroupPhone,
            fax: v.GroupFax, postCode: v.GroupPostCode, address: v.GroupAddress, email: v.GroupEamil, discount: v.GroupDisCount, limitOil: v.GroupLimitOil,
            limitVolume: num(v.GroupLimitVolume), limitTime: num(v.GroupLimitTime), limitAmountDay: num(v.GroupLimitAmountDay),
            limitVolumeMonth: num(v.GroupLimitVolumeMonth), limitAmountMonth: num(v.GroupLimitAmountMonth), creditLine: num(v.GroupCreditLine),
            billDate: num(v.GroupCreateDay), repayDay: num(v.GroupRepayDay), limitMinBalance: num(v.LimitMinBalance),
          }, v.GroupPassword),
        }} />
    </Win>
  )
}
