import { Fragment, useCallback, useEffect, useState, type CSSProperties, type ReactNode } from 'react'
import { App, Dropdown, Empty, Modal, Table, Tooltip } from 'antd'
import {
  BarChartOutlined, CalendarOutlined, ClockCircleOutlined, CreditCardOutlined, DollarOutlined, EditOutlined, FileSearchOutlined,
  FundOutlined, GiftOutlined, KeyOutlined, PrinterOutlined, ProfileOutlined, RollbackOutlined, SafetyOutlined, StopOutlined, SwapOutlined,
  TagOutlined, TeamOutlined, WalletOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { get } from '../api/http'
import AppearanceButton from '../theme/AppearanceButton'
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

/** Familles d'outils : une couleur par famille, séparateur entre deux familles. */
type ToolGroup = 'base' | 'cards' | 'reports' | 'admin'
const GROUP_HUE: Record<ToolGroup, string> = { base: '#1e88e5', cards: '#5e35b1', reports: '#00897b', admin: '#ef6c00' }

/** Barre d'outils (dans l'ordre du logiciel d'origine ; « Secret Key » et « PSAM » concernent le matériel et sont retirés). */
const TOOLS: { key: WindowKey; label: string; icon: ReactNode; group: ToolGroup; right?: number }[] = [
  { key: 'OilTypeAndPrice', label: 'Prix', icon: <DollarOutlined />, group: 'base', right: 1 },
  { key: 'StationPrintForm', label: 'Impression', icon: <PrinterOutlined />, group: 'base' },
  { key: 'CardMake', label: 'Création de carte', icon: <CreditCardOutlined />, group: 'cards', right: 4 },
  { key: 'CardUpDate', label: 'Mise à jour carte', icon: <EditOutlined />, group: 'cards', right: 7 },
  { key: 'CardLost', label: 'Carte perdue', icon: <StopOutlined />, group: 'cards', right: 10 },
  { key: 'CardChange', label: 'Remplacement', icon: <SwapOutlined />, group: 'cards', right: 13 },
  { key: 'Deposit', label: 'Solde', icon: <WalletOutlined />, group: 'cards', right: 16 },
  { key: 'OilTransacation', label: 'Transactions', icon: <FileSearchOutlined />, group: 'reports', right: 2 },
  { key: 'EmpForm', label: 'Employés', icon: <TeamOutlined />, group: 'reports', right: 5 },
  { key: 'ShiftForm', label: 'Rapport de poste', icon: <ClockCircleOutlined />, group: 'reports', right: 8 },
  { key: 'RDaily0', label: 'Rapport journalier', icon: <BarChartOutlined />, group: 'reports', right: 11 },
  { key: 'RDaily1', label: 'Rapport mensuel', icon: <CalendarOutlined />, group: 'reports', right: 14 },
  { key: 'CardLog', label: 'Journal des cartes', icon: <ProfileOutlined />, group: 'reports', right: 17 },
  { key: 'ScoreDeal', label: 'Points', icon: <GiftOutlined />, group: 'admin', right: 3 },
  { key: 'CenterDisCount', label: 'Remises', icon: <TagOutlined />, group: 'admin', right: 6 },
  { key: 'BusinessReportMess', label: 'Activité', icon: <FundOutlined />, group: 'admin', right: 15 },
  { key: 'PasswordLost', label: 'Code PIN oublié', icon: <KeyOutlined />, group: 'admin', right: 18 },
  { key: 'Authority', label: 'Droits', icon: <SafetyOutlined />, group: 'admin', right: 24 },
  { key: 'CancleDuty', label: 'Annuler la clôture', icon: <RollbackOutlined />, group: 'admin', right: 8 },
]

const hasCard = (asn: unknown) => !!asn && !String(asn).startsWith('0000')

/** Heure affichée dans la barre d'état. */
function useClock() {
  const [now, setNow] = useState(() => dayjs())
  useEffect(() => {
    const t = setInterval(() => setNow(dayjs()), 10000)
    return () => clearInterval(t)
  }, [])
  return now
}

/** Fenêtre principale du logiciel Station (Station.cs d'origine), sans les fonctions liées au matériel. */
export default function MainWindow() {
  const { me, logout } = useStation()
  const { message } = App.useApp()
  const [data, setData] = useState<MainData | null>(null)
  const [sync, setSync] = useState<{ at: number | null; failed: boolean }>({ at: null, failed: false })
  const [tab, setTab] = useState<'noz' | 'table'>('noz')
  const [open, setOpen] = useState<WindowKey | null>(null)
  const [active, setActive] = useState<WindowKey | null>(null)
  const [sale, setSale] = useState<Row | null>(null)
  const [typed, setTyped] = useState('')
  const clock = useClock()

  const [refreshSec, setRefreshSec] = useState(15)
  const can = (right?: number) => !right || !me?.rights || me.rights.includes(right)
  const load = useCallback(() => get<MainData>('/station/main')
    .then((d) => {
      setData(d)
      setSync({ at: Date.now(), failed: false })
    })
    .catch(() => setSync((s) => ({ ...s, failed: true }))), [])
  useEffect(() => {
    get<Record<string, string>>('/station/settings').then((s) => s['refresh.seconds'] && setRefreshSec(Number(s['refresh.seconds']))).catch(() => undefined)
  }, [open])
  useEffect(() => {
    load()
    const t = setInterval(load, refreshSec * 1000)
    return () => clearInterval(t)
  }, [load, refreshSec])

  // Saisie rapide : taper le numéro d'un pistolet ouvre la vente sur ce pistolet
  useEffect(() => {
    if (open || sale) return
    const onKey = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.altKey || e.metaKey) return
      if (e.target instanceof Element && e.target.closest('input, textarea, select, [contenteditable="true"], .ant-select, .ant-modal, .ant-dropdown')) return
      if (/^[0-9]$/.test(e.key)) setTyped((s) => (s + e.key).slice(-3))
      else if (e.key === 'Escape') setTyped('')
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, sale])
  useEffect(() => {
    if (!typed) return
    const t = setTimeout(() => {
      const n = data?.nozzles.find((x) => String(x.nozId) === typed)
      if (n) setSale(n)
      else message.warning(`Aucun pistolet N°${typed}`)
      setTyped('')
    }, 700)
    return () => clearTimeout(t)
  }, [typed, data, message])

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

  const tools = TOOLS.filter((t) => can(t.right))
  const tx = data?.transactions ?? []
  const txTotal = tx.reduce((s, r) => s + Number(r.amount ?? 0), 0)

  return (
    <div className="sw-app">
      <div className="sw-menubar">
        {menus.filter((m) => can(m.right)).map((m) => (
          <Dropdown key={m.label} trigger={['click']} menu={{ items: m.items.map((it, i) => ('type' in it ? { type: 'divider' as const, key: `d${i}` } : { key: it.key, label: it.label, onClick: it.onClick })) }}>
            <button type="button" className="sw-menu">{m.label}</button>
          </Dropdown>
        ))}
        <span className="sw-title">Station SoftWare — {data?.station ?? me?.station.name}</span>
        <AppearanceButton size="small" />
      </div>

      <div className="sw-toolbar" role="toolbar" aria-label="Barre d'outils">
        {tools.map((t, i) => (
          <Fragment key={t.key}>
            {i > 0 && tools[i - 1].group !== t.group && <span className="sw-tool-sep" aria-hidden />}
            <button type="button" className={`sw-tool ${active === t.key ? 'active' : ''}`} onClick={() => openWin(t.key)}
              style={{ '--hue': GROUP_HUE[t.group] } as CSSProperties}>
              <span className="sw-tool-icon">{t.icon}</span>
              <span className="sw-tool-label">{t.label}</span>
            </button>
          </Fragment>
        ))}
      </div>

      <div className="sw-body">
        <div className="sw-left">
          <div className="sw-panel-head">
            <span>Transactions des postes non clôturés</span>
            <span className="sw-panel-meta">{tx.length} · {money(txTotal)}</span>
          </div>
          <div className="sw-tx">
            <Table<Row> size="small" pagination={false} rowKey="id" dataSource={tx} scroll={{ y: 'calc(100vh - 420px)' }}
              locale={{ emptyText: 'Aucune transaction sur les postes ouverts' }}
              onRow={(r) => ({ onDoubleClick: () => openWin('OilTransacation'), title: hasCard(r.asn) ? `Carte ${formatAsn(r.asn as string)}` : undefined })}
              columns={[
                { title: 'Pist.', dataIndex: 'noz', width: 50 },
                { title: 'Volume', dataIndex: 'volume', width: 78, align: 'right', render: (v) => money(v) },
                { title: 'Montant', dataIndex: 'amount', width: 100, align: 'right', render: (v) => money(v) },
                { title: 'Heure', dataIndex: 'time', ellipsis: true, render: (v) => dayjs(v).format('DD/MM HH:mm:ss') },
              ]} />
          </div>
          <div className="sw-panel-head">
            <span>Totaux par employé</span>
          </div>
          <div className="sw-emp">
            <Table<Row> size="small" pagination={false} rowKey="emp" dataSource={data?.employees ?? []} scroll={{ y: 150 }}
              locale={{ emptyText: 'Aucune vente' }}
              columns={[
                { title: 'Employé', dataIndex: 'emp', ellipsis: true },
                { title: 'Volume total', dataIndex: 'totalVolume', align: 'right', render: (v) => money(v) },
                { title: 'Montant total', dataIndex: 'totalAmount', align: 'right', render: (v) => money(v) },
              ]} />
          </div>
        </div>

        <div className="sw-right">
          <div className="sw-tabs" role="tablist">
            <button type="button" role="tab" aria-selected={tab === 'noz'} className={tab === 'noz' ? 'active' : ''} onClick={() => setTab('noz')}>Pistolets</button>
            <button type="button" role="tab" aria-selected={tab === 'table'} className={tab === 'table' ? 'active' : ''} onClick={() => setTab('table')}>Tableau des pistolets</button>
            <span className="sw-hint">
              {typed
                ? <>Pistolet <kbd>{typed}</kbd>…</>
                : <>Astuce : tapez le numéro d’un pistolet pour saisir une vente</>}
            </span>
          </div>
          <div className="sw-pane">
            {tab === 'noz' ? (
              <div className="sw-noz-grid">
                {(data?.nozzles ?? []).map((n) => {
                  const id = String(n.nozId)
                  return (
                    <Tooltip key={id} title="Cliquez pour saisir une vente sur ce pistolet">
                      <button type="button" className={`sw-noz${typed && id.startsWith(typed) ? ' match' : ''}`} onClick={() => setSale(n)}>
                        <div className="sw-noz-head">
                          <div className="sw-noz-no">N°{id}</div>
                          <div>
                            <div className="sw-noz-emp">{String(n.lastEmployee ?? 'Aucun employé')}</div>
                            <div className="sw-noz-card">{hasCard(n.lastAsn) ? formatAsn(n.lastAsn as string) : 'Sans carte'}</div>
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
                      </button>
                    </Tooltip>
                  )
                })}
                {data && data.nozzles.length === 0 && (
                  <Empty className="sw-empty" description="Aucun pistolet : ajoutez-les dans Paramètres système › Configuration des pistolets." />
                )}
              </div>
            ) : (
              <Table<Row> size="small" pagination={false} rowKey="nozId" dataSource={data?.nozzles ?? []}
                onRow={(r) => ({ onDoubleClick: () => setSale(r) })}
                columns={[
                  { title: 'Pistolet', dataIndex: 'nozId' },
                  { title: 'Dernière vente', dataIndex: 'lastTime', render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '') },
                  { title: 'Index', dataIndex: 'volumeTotal', align: 'right', render: (v) => money(v ?? 0) },
                  { title: 'Dernier montant', dataIndex: 'lastAmount', align: 'right', render: (v) => money(v) },
                  { title: 'Dernier volume', dataIndex: 'lastVolume', align: 'right', render: (v) => money(v) },
                  { title: 'Énergie', dataIndex: 'gradeName' },
                  { title: 'Prix', dataIndex: 'price', align: 'right', render: (v) => money(v, 3) },
                  { title: 'État', key: 'st', render: () => 'Saisie manuelle' },
                ]} />
            )}
          </div>
        </div>
      </div>

      <div className="sw-status">
        <span className="sw-status-brand">LTStation</span>
        <span className="sw-grow">Poste en cours : {data?.shift ? `${data.shift.shift} (${data.shift.name})` : <span className="sw-err">aucun poste configuré</span>}</span>
        <span>Employé connecté : {me?.employeeName}</span>
        <span className={`sw-conn ${sync.failed ? 'off' : 'on'}`}>
          {sync.failed
            ? 'Hors ligne — nouvel essai automatique'
            : sync.at ? `En ligne · actualisé à ${dayjs(sync.at).format('HH:mm:ss')}` : 'Connexion…'}
        </span>
        <span className="sw-clock">{clock.format('ddd D MMM · HH:mm')}</span>
      </div>

      {Current && <Current onClose={() => { setOpen(null); load() }} />}
      {sale && <Sale nozzle={sale} onClose={() => { setSale(null); load() }} />}
    </div>
  )
}
