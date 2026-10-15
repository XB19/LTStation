import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Dropdown, Modal, Table, Tooltip } from 'antd'
import {
  BarChartOutlined, CalendarOutlined, ClockCircleOutlined, CreditCardOutlined, DollarOutlined, EditOutlined, FileSearchOutlined,
  FundOutlined, GiftOutlined, KeyOutlined, PrinterOutlined, ProfileOutlined, RollbackOutlined, SafetyOutlined, StopOutlined, SwapOutlined,
  TagOutlined, TeamOutlined, WalletOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { get } from '../api/http'
import { useStation } from './auth'
import { formatAsn, money } from './labels'
import { WINDOWS, type WindowKey } from './windows'
import Sale from './windows/Sale'
import './winforms/winforms.css'

type Row = Record<string, unknown>

interface MainData {
  shift: { shift: string; name: string } | null
  employee: string
  station: string
  transactions: Row[]
  employees: Row[]
  nozzles: Row[]
}

/** Barre d'outils (dans l'ordre du logiciel d'origine ; « Secret Key » et « PSAM » concernent le matériel et sont retirés). */
const TOOLS: { key: WindowKey; label: string; icon: ReactNode; color: string; right?: number }[] = [
  { key: 'OilTypeAndPrice', label: 'Prix', icon: <DollarOutlined />, color: '#1e88e5', right: 1 },
  { key: 'StationPrintForm', label: 'Impression', icon: <PrinterOutlined />, color: '#fb8c00' },
  { key: 'CardMake', label: 'Création de carte', icon: <CreditCardOutlined />, color: '#1565c0', right: 4 },
  { key: 'CardUpDate', label: 'Mise à jour carte', icon: <EditOutlined />, color: '#5c6bc0', right: 7 },
  { key: 'CardLost', label: 'Carte perdue', icon: <StopOutlined />, color: '#ef6c00', right: 10 },
  { key: 'CardChange', label: 'Remplacement', icon: <SwapOutlined />, color: '#0288d1', right: 13 },
  { key: 'Deposit', label: 'Solde', icon: <WalletOutlined />, color: '#e53935', right: 16 },
  { key: 'OilTransacation', label: 'Transactions', icon: <FileSearchOutlined />, color: '#1976d2', right: 2 },
  { key: 'EmpForm', label: 'Employés', icon: <TeamOutlined />, color: '#26a69a', right: 5 },
  { key: 'ShiftForm', label: 'Rapport de poste', icon: <ClockCircleOutlined />, color: '#7e57c2', right: 8 },
  { key: 'RDaily0', label: 'Rapport journalier', icon: <BarChartOutlined />, color: '#d81b60', right: 11 },
  { key: 'RDaily1', label: 'Rapport mensuel', icon: <CalendarOutlined />, color: '#1e88e5', right: 14 },
  { key: 'CardLog', label: 'Journal des cartes', icon: <ProfileOutlined />, color: '#3949ab', right: 17 },
  { key: 'ScoreDeal', label: 'Points', icon: <GiftOutlined />, color: '#e91e63', right: 3 },
  { key: 'CenterDisCount', label: 'Remises', icon: <TagOutlined />, color: '#f4511e', right: 6 },
  { key: 'BusinessReportMess', label: 'Activité', icon: <FundOutlined />, color: '#00897b', right: 15 },
  { key: 'PasswordLost', label: 'Code PIN oublié', icon: <KeyOutlined />, color: '#8d6e63', right: 18 },
  { key: 'Authority', label: 'Droits', icon: <SafetyOutlined />, color: '#546e7a', right: 24 },
  { key: 'CancleDuty', label: 'Annuler la clôture', icon: <RollbackOutlined />, color: '#757575', right: 8 },
]

/** Fenêtre principale du logiciel Station (Station.cs d'origine), sans les fonctions liées au matériel. */
export default function MainWindow() {
  const { me, logout } = useStation()
  const [data, setData] = useState<MainData | null>(null)
  const [tab, setTab] = useState<'noz' | 'table'>('noz')
  const [open, setOpen] = useState<WindowKey | null>(null)
  const [active, setActive] = useState<WindowKey | null>(null)
  const [sale, setSale] = useState<Row | null>(null)

  const [refreshSec, setRefreshSec] = useState(15)
  const can = (right?: number) => !right || !me?.rights || me.rights.includes(right)
  const load = useCallback(() => get<MainData>('/station/main').then(setData).catch(() => undefined), [])
  useEffect(() => {
    get<Record<string, string>>('/station/settings').then((s) => s['refresh.seconds'] && setRefreshSec(Number(s['refresh.seconds']))).catch(() => undefined)
  }, [open])
  useEffect(() => {
    load()
    const t = setInterval(load, refreshSec * 1000)
    return () => clearInterval(t)
  }, [load, refreshSec])

  const openWin = (k: WindowKey) => {
    setActive(k)
    setOpen(k)
  }
  const Current = open ? WINDOWS[open] : null

  const menus: { label: string; right?: number; items: ({ key: string; label: string; onClick: () => void } | { type: 'divider' })[] }[] = [
    {
      label: 'Paramètres système', right: 19, items: [
        { key: 'base', label: 'Informations de la station', onClick: () => openWin('BaseMessage') },
        { type: 'divider' },
        { key: 'noz', label: 'Configuration des pistolets', onClick: () => openWin('StationNozzleSet') },
        { type: 'divider' },
        { key: 'shift', label: 'Gestion des postes', onClick: () => openWin('ShiftItemForm') },
        { key: 'login', label: 'Changer d’employé', onClick: () => logout() },
        { key: 'exit', label: 'Quitter', onClick: () => Modal.confirm({ title: 'Quitter le logiciel ?', okText: 'Quitter', cancelText: 'Annuler', onOk: () => logout() }) },
      ],
    },
    {
      label: 'Gestion de la station', right: 20, items: [
        { key: 'sr', label: 'Rapport de poste', onClick: () => openWin('ShiftForm') },
        { key: 'dr', label: 'Rapport journalier', onClick: () => openWin('RDaily0') },
        { key: 'mr', label: 'Rapport mensuel', onClick: () => openWin('RDaily1') },
        { type: 'divider' },
        { key: 'tq', label: 'Recherche des transactions', onClick: () => openWin('OilTransacation') },
        { type: 'divider' },
        { key: 'stock', label: 'Stock', onClick: () => openWin('StockManager') },
        { type: 'divider' },
        { key: 'um', label: 'Informations client', onClick: () => openWin('UserCardMessage') },
      ],
    },
    {
      label: 'Extensions', right: 22, items: [
        { key: 'vs', label: 'Points (utilisateurs)', onClick: () => openWin('VirtualScore') },
        { type: 'divider' },
        { key: 'other', label: 'Autres réglages', onClick: () => openWin('OtherSetForm') },
      ],
    },
    {
      label: 'Utilisateurs', right: 23, items: [
        { key: 'ul', label: 'Liste des cartes', onClick: () => openWin('CustomerUserList') },
      ],
    },
    {
      label: 'À propos', items: [
        { key: 'about', label: 'À propos du logiciel', onClick: () => Modal.info({ title: 'LTStation — Station', content: 'Logiciel de station sans équipement : ventes et cartes saisies à la caisse.' }) },
      ],
    },
  ]

  return (
    <div className="sw-app">
      <div className="sw-menubar">
        {menus.filter((m) => can(m.right)).map((m) => (
          <Dropdown key={m.label} trigger={['click']} menu={{ items: m.items.map((it, i) => ('type' in it ? { type: 'divider' as const, key: `d${i}` } : { key: it.key, label: it.label, onClick: it.onClick })) }}>
            <span className="sw-menu">{m.label}</span>
          </Dropdown>
        ))}
        <span className="sw-title">Station SoftWare — {data?.station ?? me?.station.name}</span>
      </div>

      <div className="sw-toolbar">
        {TOOLS.filter((t) => can(t.right)).map((t) => (
          <button key={t.key} className={`sw-tool ${active === t.key ? 'active' : ''}`} onClick={() => openWin(t.key)}>
            <span className="sw-tool-icon" style={{ background: t.color }}>{t.icon}</span>
            {t.label}
          </button>
        ))}
      </div>

      <div className="sw-body">
        <div className="sw-left">
          <div className="sw-tx">
            <Table<Row> size="small" bordered pagination={false} rowKey="id" dataSource={data?.transactions ?? []} scroll={{ y: 'calc(100vh - 360px)' }}
              onRow={(r) => ({ onDoubleClick: () => openWin('OilTransacation'), title: r.asn && !String(r.asn).startsWith('0000') ? `Carte ${formatAsn(r.asn as string)}` : undefined })}
              columns={[
                { title: 'Pist.', dataIndex: 'noz', width: 46 },
                { title: 'Volume', dataIndex: 'volume', width: 72, render: (v) => money(v) },
                { title: 'Montant', dataIndex: 'amount', width: 96, render: (v) => money(v) },
                { title: 'Date', dataIndex: 'time', ellipsis: true, render: (v) => dayjs(v).format('YYYY-MM-DD HH:mm:ss') },
              ]} />
          </div>
          <div className="sw-emp">
            <Table<Row> size="small" bordered pagination={false} rowKey="emp" dataSource={data?.employees ?? []} scroll={{ y: 150 }}
              columns={[
                { title: 'Employé', dataIndex: 'emp' },
                { title: 'Volume total', dataIndex: 'totalVolume', render: (v) => money(v) },
                { title: 'Montant total', dataIndex: 'totalAmount', render: (v) => money(v) },
              ]} />
          </div>
        </div>

        <div className="sw-right">
          <div className="sw-tabs">
            <button className={tab === 'noz' ? 'active' : ''} onClick={() => setTab('noz')}>Pistolets</button>
            <button className={tab === 'table' ? 'active' : ''} onClick={() => setTab('table')}>Tableau des pistolets</button>
          </div>
          <div className="sw-pane">
            {tab === 'noz' ? (
              <div className="sw-noz-grid">
                {(data?.nozzles ?? []).map((n) => (
                  <Tooltip key={String(n.nozId)} title="Cliquez pour saisir une vente sur ce pistolet">
                    <div className="sw-noz" onClick={() => setSale(n)}>
                      <div className="sw-noz-head">
                        <div className="sw-noz-no">N°{String(n.nozId)}</div>
                        <div>
                          <div className="sw-noz-emp">{String(n.lastEmployee ?? 'Aucun employé')}</div>
                          <div className="sw-noz-card">{n.lastAsn && !String(n.lastAsn).startsWith('0000') ? formatAsn(n.lastAsn as string) : 'Sans carte'}</div>
                        </div>
                      </div>
                      <div className="sw-noz-state ready">Prêt — saisie manuelle</div>
                      <div className="sw-noz-main">
                        <div className="sw-noz-icon">⛽</div>
                        <div className="sw-noz-val">Montant : {money(n.lastAmount)}</div>
                        <div className="sw-noz-val">Volume : {money(n.lastVolume)}</div>
                        <div className="sw-noz-fuel">Énergie : {String(n.gradeName ?? '')}</div>
                        <div className="sw-noz-val">Prix : {money(n.price, 3)}</div>
                      </div>
                      <div className="sw-noz-total">Index : {money(n.volumeTotal ?? 0)}</div>
                    </div>
                  </Tooltip>
                ))}
                {data && data.nozzles.length === 0 && (
                  <div style={{ color: '#5b7590' }}>Aucun pistolet : ajoutez-les dans Paramètres système &gt; Configuration des pistolets.</div>
                )}
              </div>
            ) : (
              <Table<Row> size="small" bordered pagination={false} rowKey="nozId" dataSource={data?.nozzles ?? []}
                onRow={(r) => ({ onDoubleClick: () => setSale(r) })}
                columns={[
                  { title: 'Pistolet', dataIndex: 'nozId' },
                  { title: 'Dernière vente', dataIndex: 'lastTime', render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '') },
                  { title: 'Index', dataIndex: 'volumeTotal', render: (v) => money(v ?? 0) },
                  { title: 'Dernier montant', dataIndex: 'lastAmount', render: (v) => money(v) },
                  { title: 'Dernier volume', dataIndex: 'lastVolume', render: (v) => money(v) },
                  { title: 'Énergie', dataIndex: 'gradeName' },
                  { title: 'Prix', dataIndex: 'price', render: (v) => money(v, 3) },
                  { title: 'État', key: 'st', render: () => 'Saisie manuelle' },
                ]} />
            )}
          </div>
        </div>
      </div>

      <div className="sw-status">
        <span><b>LTStation</b></span>
        <span className="sw-grow">Poste en cours : {data?.shift ? `${data.shift.shift} (${data.shift.name})` : <span className="sw-err">aucun poste configuré</span>}</span>
        <span>Employé connecté : {me?.employeeName}</span>
        <span>État : en ligne</span>
      </div>

      {Current && <Current onClose={() => { setOpen(null); load() }} />}
      {sale && <Sale nozzle={sale} onClose={() => { setSale(null); load() }} />}
    </div>
  )
}
