import type { ComponentType } from 'react'
import AccountManager from './pages/main/AccountManager'
import StationSetting from './pages/main/StationSetting'
import ActiveStation from './pages/main/ActiveStation'
import PriceConfigure from './pages/main/PriceConfigure'
import UnknownDevice from './pages/main/UnknownDevice'
import ActiveDevice from './pages/main/ActiveDevice'
import RenewOrder from './pages/main/RenewOrder'
import AirSetting from './pages/main/AirSetting'
import Employees from './pages/main/Employees'
import DiscountScore from './pages/main/DiscountScore'
import SpecialPrice from './pages/main/SpecialPrice'
import Transactions from './pages/main/Transactions'
import ChargeList from './pages/main/ChargeList'
import LogSearch from './pages/main/LogSearch'
import CustomerList from './pages/main/CustomerList'
import TankMessage, { StationTankMessage } from './pages/main/TankMessage'
import TankAlarms, { DeviceSafe } from './pages/main/TankAlarms'
import StationReport from './pages/main/StationReport'
import CardSummary from './pages/main/CardSummary'
import DepositOnline from './pages/main/DepositOnline'
import MCardTranslate from './pages/main/MCardTranslate'
import MCardUpdate from './pages/main/MCardUpdate'
import DeviceSearch from './pages/main/DeviceSearch'
import OrderSubmit from './pages/main/OrderSubmit'
import BillOrder from './pages/main/BillOrder'
import { MonitorDeviceList, MonitorDeviceWall } from './pages/main/MonitorDevices'
import CardUpdateByServer from './pages/main/CardUpdateByServer'
import Advert from './pages/main/Advert'
import { FleetList, FleetDrivers, FleetRechargeRecords, FleetDistributeRecords, FleetCardRecords } from './pages/main/Fleets'
import { PurchaseOilPriceList, PurchaseOrders } from './pages/main/Purchase'
import { ScanPayRecords, ScanPayBarcode } from './pages/main/ScanPay'
import { GprsCustomers, GprsIccidLen, GprsStations, GprsNozzles, GprsTanks, GprsNozzleSearch, GprsTransactions, GprsReports } from './pages/gprs/Gprs'
import { TcpPlatform, BoxConfigure, TcpDeviceBind, TcpDeviceShow, NormalTransaction } from './pages/main/Wireless'

/**
 * Pages déjà portées, indexées par chemin. Les autres entrées du menu
 * affichent l'écran "en cours de portage".
 */
export const PAGES: Record<string, ComponentType> = {
  '/main/account-manager': AccountManager,
  '/main/station-setting': StationSetting,
  '/main/active-station': ActiveStation,
  '/main/price-configure': PriceConfigure,
  '/main/unknown-device': UnknownDevice,
  '/main/active-device': ActiveDevice,
  '/main/renew-order': RenewOrder,
  '/main/air-setting': AirSetting,
  '/main/employees': Employees,
  '/main/discount-score': DiscountScore,
  '/main/special-price': SpecialPrice,
  '/main/transactions': Transactions,
  '/main/charge-list': ChargeList,
  '/main/log-search': LogSearch,
  '/main/customers': CustomerList,
  '/main/tank-message': TankMessage,
  '/main/station-tank-message': StationTankMessage,
  '/main/tank-alarms': TankAlarms,
  '/main/station-report': StationReport,
  '/main/card-summary': CardSummary,
  '/main/deposit-online': DepositOnline,
  '/main/mcard-translate': MCardTranslate,
  '/main/mcard-update': MCardUpdate,
  '/main/device-search': DeviceSearch,
  '/main/device-safe': DeviceSafe,
  '/main/order-submit': OrderSubmit,
  '/main/bill-order': BillOrder,
  '/main/monitor-device-list': MonitorDeviceList,
  '/main/monitor-device': MonitorDeviceWall,
  '/main/card-update-server': CardUpdateByServer,
  '/main/advert': Advert,
  '/main/fleet-list': FleetList,
  '/main/fleet-driver': FleetDrivers,
  '/main/fleet-recharge': FleetRechargeRecords,
  '/main/fleet-distribute': FleetDistributeRecords,
  '/main/fleet-card-record': FleetCardRecords,
  '/main/purchase-oil-price': PurchaseOilPriceList,
  '/main/purchase-order': PurchaseOrders,
  '/main/scan-pay-records': ScanPayRecords,
  '/main/scan-pay-barcode': ScanPayBarcode,
  '/main/tcp-platform': TcpPlatform,
  '/main/box-configure': BoxConfigure,
  '/main/tcp-device-bind': TcpDeviceBind,
  '/main/tcp-device-show': TcpDeviceShow,
  '/main/normal-transaction': NormalTransaction,
  '/gprs/iccid-len': GprsIccidLen,
  '/gprs/customers': GprsCustomers,
  '/gprs/stations': GprsStations,
  '/gprs/nozzles': GprsNozzles,
  '/gprs/tanks': GprsTanks,
  '/gprs/nozzle-search': GprsNozzleSearch,
  '/gprs/transactions': GprsTransactions,
  '/gprs/reports': GprsReports,
}
