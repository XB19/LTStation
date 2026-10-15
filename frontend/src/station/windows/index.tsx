import type { ComponentType } from 'react'
import OilTypeAndPrice from './OilTypeAndPrice'
import StationPrintForm from './StationPrintForm'
import CardMake from './CardMake'
import CardUpDate from './CardUpDate'
import CardLost from './CardLost'
import CardChange from './CardChange'
import Deposit from './Deposit'
import OilTransacation from './OilTransacation'
import EmpForm from './EmpForm'
import ShiftForm, { CancleDuty } from './ShiftForm'
import RDaily from './RDaily'
import CardLog, { BusinessReportMess } from './CardLog'
import Authority from './Authority'
import { BaseMessage, CenterDisCount, OtherSetForm, ScoreDeal, ShiftItemForm, StationNozzleSet } from './Settings'
import { CustomerUserList, PasswordLost, StockManager, UserCardMessage, VirtualScore } from './Customers'

type Win = ComponentType<{ onClose: () => void }>

/** Fenêtres du logiciel Station, par nom de fenêtre d'origine. */
export const WINDOWS = {
  OilTypeAndPrice,
  StationPrintForm,
  CardMake,
  CardUpDate,
  CardLost,
  CardChange,
  Deposit,
  OilTransacation,
  EmpForm,
  ShiftForm,
  RDaily0: ((p) => <RDaily {...p} month={false} />) as Win,
  RDaily1: ((p) => <RDaily {...p} month />) as Win,
  CardLog,
  ScoreDeal,
  CenterDisCount,
  BusinessReportMess,
  PasswordLost,
  Authority,
  CancleDuty,
  BaseMessage,
  StationNozzleSet,
  ShiftItemForm,
  StockManager,
  UserCardMessage,
  CustomerUserList,
  VirtualScore,
  OtherSetForm,
} satisfies Record<string, Win>

export type WindowKey = keyof typeof WINDOWS
