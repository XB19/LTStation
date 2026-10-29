import { useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Button, Checkbox, DatePicker, Dropdown, Input, Progress, Select, Table, TimePicker } from 'antd'
import type { MenuProps } from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import fr from './fr.json'
import './winforms.css'

/**
 * Rendu d'une fenêtre du logiciel Station d'origine à partir de sa description (layouts/*.json, générés depuis
 * les fichiers .resx/.cs d'origine) : mêmes onglets, cadres, champs, boutons et colonnes, dans le même ordre.
 * Les champs sont alignés en colonnes comme dans l'original, avec des contrôles modernes.
 */

export interface Ctrl {
  name: string
  type: string
  parent: string
  loc: [number, number]
  size: [number, number]
  text: string
  dock: 'top' | 'bottom' | 'left' | 'right' | 'fill' | null
  visible: boolean
  order: number
  /** Ordre d'empilement d'origine (ZOrder) : l'ancrage (Dock) se fait du plus grand au plus petit */
  z?: number
  /** Apparence d'origine : couleurs, police, alignement du texte, bordure */
  style?: CtlStyle
}

export interface CtlStyle {
  back?: string
  fore?: string
  font?: { family: string; size: number; bold: boolean; italic: boolean; underline: boolean }
  align?: string
  border?: 'single' | 'fixed3d'
  padding?: number[]
}

export interface Column {
  name: string
  header: string
  visible: boolean
  width: number | null
  /** Propriété liée (DataPropertyName) */
  prop?: string | null
  readOnly?: boolean
}

export interface Layout {
  name: string
  title: string
  size: [number, number]
  controls: Ctrl[]
  columns: Record<string, Column[]>
  /** Gestionnaires d'événements du .cs d'origine : contrôle ('$this' = fenêtre) → événement → méthode */
  events?: Record<string, Record<string, string>>
  /** Menus : barres (MenuStrip, ToolStrip, StatusStrip) et menus contextuels (ContextMenuStrip) */
  menus?: Record<string, { type: string; owners: string[]; items: MenuItem[] }>
  style?: CtlStyle
}

export interface MenuItem {
  name: string
  type: string
  text: string
  visible: boolean
  items: MenuItem[]
  /** Boutons des barres d'outils : image (chemin sous station-web), taille fixe, position du texte */
  image?: string
  size?: [number, number]
  align?: string
  tir?: string
}

const layouts = import.meta.glob<Layout>('./layouts/*.json', { eager: true, import: 'default' })

/** PreviewControl de FastReport : rapport préparé (PDF en base64) affiché dans la fenêtre. */
function PdfPreview({ data }: { data: string }) {
  const url = useMemo(() => {
    if (!data) return ''
    const bin = atob(data)
    const bytes = new Uint8Array(bin.length)
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
    return URL.createObjectURL(new Blob([bytes], { type: 'application/pdf' }))
  }, [data])
  useEffect(() => () => { if (url) URL.revokeObjectURL(url) }, [url])
  return url
    ? <iframe title="preview" src={url} style={{ width: '100%', height: '100%', minHeight: 480, border: 0 }} />
    : <div style={{ width: '100%', minHeight: 480, background: '#f5f5f5' }} />
}

export function getLayout(name: string): Layout {
  const l = layouts[`./layouts/${name}.json`]
  if (!l) throw new Error(`Fenêtre inconnue : ${name}`)
  return l
}

const DICT = fr as Record<string, string>
export const tr = (t: string) => (t in DICT ? DICT[t] : t)

type Row = Record<string, unknown>

export interface GridProps {
  rows: Row[]
  rowKey?: string | ((r: Row) => string)
  /** Rendu particulier d'une colonne (nom de colonne d'origine) */
  render?: Record<string, (v: unknown, r: Row) => ReactNode>
  /** Valeur d'une colonne (nom de colonne d'origine) ; par défaut r[nomDeColonne] */
  value?: Record<string, (r: Row) => unknown>
  onDoubleClick?: (r: Row) => void
  onClick?: (r: Row) => void
  selectedKey?: unknown
  pagination?: false | TablePaginationConfig
  loading?: boolean
  /** Colonnes à masquer en plus de celles masquées dans l'original */
  hideColumns?: string[]
  /** En-têtes remplacés (nom de colonne d'origine → texte) */
  headers?: Record<string, string>
  /** Colonnes ajoutées par le code (Columns.Add) et en-têtes dessinés (case « tout cocher ») */
  extraColumns?: Column[]
  headerNodes?: Record<string, ReactNode>
  summary?: () => ReactNode
  /** Cellules modifiables (CellEndEdit d'origine) : valeur saisie pour une ligne et une colonne */
  editable?: boolean
  onCellEdit?: (r: Row, col: string, value: string) => void
  /** Colonnes à liste déroulante (DataGridViewComboBoxCell) */
  options?: Record<string, { value: string; label: string }[]>
}

/** Cellule modifiable : double-clic pour saisir, Entrée ou sortie du champ pour valider (CellEndEdit). */
function EditableCell({ value, onCommit }: { value: unknown; onCommit: (v: string) => void }) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState('')
  if (!editing) {
    return (
      <span className="wf-cell-edit" onDoubleClick={(e) => { e.stopPropagation(); setText(value === null || value === undefined ? '' : String(value)); setEditing(true) }}>
        {value === null || value === undefined || value === '' ? ' ' : String(value)}
      </span>
    )
  }
  const commit = () => { setEditing(false); onCommit(text) }
  return <Input size="small" autoFocus value={text} onChange={(e) => setText(e.target.value)} onPressEnter={commit} onBlur={commit}
    onClick={(e) => e.stopPropagation()} />
}

export interface Option {
  value: string | number
  label: string
}

export interface WinFormProps {
  layout: Layout
  values: Record<string, unknown>
  onChange: (name: string, value: unknown) => void
  buttons?: Record<string, () => unknown>
  options?: Record<string, Option[]>
  grids?: Record<string, GridProps>
  /** Contrôles masqués (fonctions liées au matériel, pagination d'origine…) */
  hidden?: string[]
  /** Contrôles en lecture seule */
  readOnly?: string[]
  disabled?: string[]
  /** Textes remplacés (contrôle → texte) */
  texts?: Record<string, string>
  /** Saisie masquée (mots de passe, codes PIN) */
  passwords?: string[]
  /** Champs date seulement, heure seulement */
  timeOnly?: string[]
  busy?: string | null
  tab?: Record<string, string>
  onTab?: (tabControl: string, page: string) => void
  /** Contenu ajouté à la fin d'un conteneur */
  extra?: Record<string, ReactNode>
  /** Clic sur un élément de menu (nom d'origine) */
  onMenu?: (item: string) => void
  /** Événements d'un champ : Click, MouseLeave, Validated (sortie du champ après modification) */
  onCtlEvent?: (name: string, event: string) => void
  /** Ouverture / fermeture de la liste d'une ComboBox (DropDown / DropDownClosed) */
  onDropDown?: (name: string, open: boolean) => void
  /** Couleurs d'origine (BackColor / ForeColor) et images (Image / BackgroundImage, chemins sous station-web) */
  styles?: Record<string, CSSProperties>
  /** Conserver la pagination d'origine (boutons Cur / Next, libellés de page) */
  keepPager?: boolean
  images?: Record<string, string>
  backgrounds?: Record<string, string>
  /** Suggestions de saisie (AutoCompleteCustomSource, mode SuggestAppend) */
  autoComplete?: Record<string, string[]>
  /**
   * Rendu fidèle de WinForms (fenêtres principales et fenêtres intégrées) : positions et ancrages d'origine, couleurs,
   * polices ; « fill » occupe toute la place disponible, « fixed » garde la taille d'origine de la fenêtre.
   */
  classic?: 'fill' | 'fixed'
}

const PT = 96 / 72

/** Apparence d'origine → CSS. */
export function cssOf(st?: CtlStyle): CSSProperties {
  if (!st) return {}
  const out: CSSProperties = {}
  if (st.back) out.background = st.back
  if (st.fore) out.color = st.fore
  if (st.font) {
    out.fontFamily = `"${st.font.family}", "Microsoft YaHei", "Segoe UI", sans-serif`
    out.fontSize = Math.round(st.font.size * PT * 10) / 10
    if (st.font.bold) out.fontWeight = 700
    if (st.font.italic) out.fontStyle = 'italic'
    if (st.font.underline) out.textDecoration = 'underline'
  }
  if (st.border === 'single') out.border = '1px solid #7a7a7a'
  if (st.border === 'fixed3d') out.border = '2px inset #d4d4d4'
  if (st.padding) out.padding = st.padding.map((x) => `${x}px`).join(' ')
  return out
}

/** ContentAlignment → alignement flex du texte. */
function alignOf(a?: string): CSSProperties {
  const [v, h] = (a ?? 'top-left').split('-')
  const map: Record<string, string> = { top: 'flex-start', middle: 'center', bottom: 'flex-end', left: 'flex-start', center: 'center', right: 'flex-end' }
  return { alignItems: map[v] ?? 'flex-start', justifyContent: map[h] ?? 'flex-start', textAlign: (h === 'center' ? 'center' : h === 'right' ? 'right' : 'left') }
}

/** Hauteur disponible d'un conteneur (tableaux qui remplissent leur zone). */
function Measured({ children }: { children: (h: number) => ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  const [h, setH] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const ro = new ResizeObserver(() => setH(el.clientHeight))
    ro.observe(el)
    setH(el.clientHeight)
    return () => ro.disconnect()
  }, [])
  return <div ref={ref} className="wfc-measure">{h > 0 && children(h)}</div>
}

const dockOf = (c: Ctrl): Ctrl['dock'] => c.dock ?? (c.type.endsWith('StatusStrip') ? 'bottom' : /(MenuStrip|ToolStrip)$/.test(c.type) ? 'top' : null)

/**
 * Placement WinForms des enfants d'un conteneur : contrôles ancrés traités du dernier au premier de l'ordre
 * d'empilement (haut, bas, gauche, droite, remplissage), les autres à leur position d'origine.
 * `grow` : hauteur ajoutée aux barres passées sur plusieurs lignes et à leurs conteneurs (le reste se décale d'autant).
 */
function placeChildren(kids: Ctrl[], offset: [number, number], grow: Record<string, number> = {}): Map<string, CSSProperties> {
  const useZ = kids.some((k) => (k.z ?? 0) !== 0)
  const key = (k: Ctrl) => (useZ ? k.z ?? 0 : k.order === 999 ? 0 : k.order)
  let l = 0, t = 0, r = 0, b = 0
  const pos = new Map<string, CSSProperties>()
  for (const k of kids.filter((x) => dockOf(x)).sort((a, c) => key(c) - key(a))) {
    const w = k.size[0]
    const h = k.size[1] + (grow[k.name] ?? 0)
    switch (dockOf(k)) {
      case 'top': pos.set(k.name, { left: l, right: r, top: t, height: h }); t += h; break
      case 'bottom': pos.set(k.name, { left: l, right: r, bottom: b, height: h }); b += h; break
      case 'left': pos.set(k.name, { left: l, top: t, bottom: b, width: w }); l += w; break
      case 'right': pos.set(k.name, { right: r, top: t, bottom: b, width: w }); r += w; break
      default: pos.set(k.name, { left: l, top: t, right: r, bottom: b })
    }
  }
  for (const k of kids.filter((x) => !dockOf(x))) {
    pos.set(k.name, { left: k.loc[0] - offset[0], top: k.loc[1] - offset[1], width: k.size[0], height: k.size[1] })
  }
  return pos
}

/** Pagination d'origine remplacée par celle des tableaux */
const PAGER = new Set(['CurEnd', 'Cur', 'CurPage', 'Next', 'NextEnd', 'TotalPageSecond', 'TotalPage', 'TotalCount', 'MessageLabel', 'MLabel'])
const isPagerLabel = (c: Ctrl) => c.type.endsWith('Label') && (c.text === '/' || /^\d+$/.test(c.text))

/** Cellule de la grille des champs libres (plusieurs contrôles possibles, ex. libellé + « * »). */
interface Slot {
  row: number
  col: number
  span: number
  label: boolean
  items: Ctrl[]
}

const CONTAINERS = new Set(['SkinTabControl', 'TabControl', 'GroupBox', 'SkinCaptionPanel', 'Panel', 'SkinPanel', 'SkinGroupBox'])
const isLabel = (c: Ctrl) => c.type === 'Label' || c.type === 'SkinLabel'
const isGrid = (c: Ctrl) => c.type.endsWith('DataGridView')
const isStrip = (c: Ctrl) => /(MenuStrip|ToolStrip|StatusStrip)$/.test(c.type)

export default function WinForm(p: WinFormProps) {
  const { layout } = p
  const [tabs, setTabs] = useState<Record<string, string>>({})
  const children = useMemo(() => {
    const m: Record<string, Ctrl[]> = {}
    const hidden = new Set(p.hidden ?? [])
    for (const c of layout.controls) {
      if (!c.visible || hidden.has(c.name) || (!p.keepPager && (PAGER.has(c.name) || isPagerLabel(c)))) continue
      if (isLabel(c) && !c.text && !p.texts?.[c.name]) continue
      ;(m[c.parent] ??= []).push(c)
    }
    return m
  }, [layout, p.hidden, p.texts, p.keepPager])

  const inFlow = useMemo(() => new Set(layout.controls.filter((c) => !c.dock && !isLabel(c) && !c.type.includes('Button') && !c.type.includes('CheckBox')
    && !CONTAINERS.has(c.type) && !isGrid(c) && !isStrip(c)).map((c) => c.name)), [layout])

  const text = (c: Ctrl) => (p.texts?.[c.name] !== undefined ? p.texts[c.name] : tr(c.text))
  const itemText = (i: MenuItem) => (p.texts?.[i.name] !== undefined ? p.texts[i.name] : tr(i.text))
  const shown = (i: MenuItem) => i.visible && !(p.hidden ?? []).includes(i.name)

  /** Éléments d'un menu déroulant / contextuel au format antd. */
  const menuItems = (items: MenuItem[]): MenuProps['items'] => items.filter(shown).map((i) => (
    i.type === 'ToolStripSeparator'
      ? { type: 'divider' as const, key: i.name }
      : {
          key: i.name,
          label: itemText(i),
          disabled: (p.disabled ?? []).includes(i.name),
          children: i.items.length ? menuItems(i.items) : undefined,
          onClick: i.items.length ? undefined : () => p.onMenu?.(i.name),
        }
  ))

  /** Barre de menus, d'outils ou d'état. */
  const renderStrip = (c: Ctrl) => {
    const m = layout.menus?.[c.name]
    if (!m) return null
    const items = m.items.filter(shown)
    if (m.type === 'StatusStrip') {
      return (
        <div className="wf-status">
          {items.map((i) => (i.type === 'ToolStripProgressBar'
            ? <progress key={i.name} className="wf-status-pro" />
            : <span key={i.name} className="wf-status-item">{itemText(i)}</span>))}
        </div>
      )
    }
    return (
      <div className={m.type === 'MenuStrip' ? 'wf-menubar' : 'wf-toolbar'}>
        {items.map((i) => {
          if (i.type === 'ToolStripSeparator') return <span key={i.name} className="wf-sep" />
          const dis = (p.disabled ?? []).includes(i.name)
          if (i.items.length) {
            return (
              <Dropdown key={i.name} trigger={['click']} disabled={dis} menu={{ items: menuItems(i.items) }}>
                <button type="button" className="wf-menu-btn">{itemText(i)}</button>
              </Dropdown>
            )
          }
          return <button key={i.name} type="button" className="wf-menu-btn" disabled={dis} onClick={() => p.onMenu?.(i.name)}>{itemText(i)}</button>
        })}
      </div>
    )
  }

  /** Menu contextuel (clic droit) du contrôle, s'il en a un. */
  const contextMenu = (name: string) => Object.values(layout.menus ?? {}).find((m) => m.type === 'ContextMenuStrip' && m.owners.includes(name))
  const withContext = (name: string, node: ReactNode) => {
    const m = contextMenu(name)
    if (!m || !node) return node
    return <Dropdown trigger={['contextMenu']} menu={{ items: menuItems(m.items) }}><div style={{ height: '100%' }}>{node}</div></Dropdown>
  }

  const renderControl = (c: Ctrl): ReactNode => {
    const ro = p.readOnly?.includes(c.name)
    const dis = p.disabled?.includes(c.name)
    const v = p.values[c.name]
    const w: number | string = inFlow.has(c.name) ? '100%' : Math.max(c.size[0], 60)
    switch (c.type) {
      case 'Label':
      case 'SkinLabel':
        // Label.Image : image de fond derrière le texte
        return <span className="wf-label" style={{ ...p.styles?.[c.name], ...(p.images?.[c.name]
          ? { backgroundImage: `url(/station-web/${p.images[c.name]})`, backgroundSize: 'contain', backgroundRepeat: 'no-repeat', backgroundPosition: 'center', minWidth: c.size[0], minHeight: c.size[1], display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }
          : {}) }}>{text(c)}</span>
      case 'PictureBox':
      case 'SkinPictureBox':
        return p.images?.[c.name]
          ? <img src={`/station-web/${p.images[c.name]}`} alt="" style={{ width: c.size[0], height: c.size[1], objectFit: 'contain', ...p.styles?.[c.name] }} />
          : <span style={{ display: 'inline-block', width: c.size[0], height: c.size[1] }} />
      case 'TextBox':
      case 'SkinTextBox':
      case 'RichTextBox':
      case 'NumericUpDown': {
        const Comp = p.passwords?.includes(c.name) ? Input.Password : Input
        const ac = p.autoComplete?.[c.name]
        return <>{ac && <datalist id={`wf-ac-${c.name}`}>{ac.map((x) => <option key={x} value={x} />)}</datalist>}
        <Comp size="small" style={{ width: w }} value={(v as string) ?? ''} readOnly={ro} disabled={dis} list={ac ? `wf-ac-${c.name}` : undefined}
          className={ro ? 'wf-ro' : undefined} onChange={(e) => p.onChange(c.name, e.target.value)}
          onFocus={(e) => { (e.target as HTMLInputElement).dataset.orig = e.target.value }}
          onBlur={(e) => { if ((e.target as HTMLInputElement).dataset.orig !== e.target.value) p.onCtlEvent?.(c.name, 'Validated') }}
          onClick={() => p.onCtlEvent?.(c.name, 'Click')} onMouseLeave={() => p.onCtlEvent?.(c.name, 'MouseLeave')} /></>
      }
      case 'SkinComboBox':
      case 'ComboBox':
        return <Select size="small" style={{ width: w }} value={v as string | number | undefined} disabled={dis || ro}
          options={p.options?.[c.name] ?? []} onChange={(x) => p.onChange(c.name, x)} showSearch optionFilterProp="label"
          onDropdownVisibleChange={(open) => p.onDropDown?.(c.name, open)}
          popupMatchSelectWidth={false} />
      case 'DateTimePicker':
      case 'SkinDateTimePicker':
        return p.timeOnly?.includes(c.name)
          ? <TimePicker size="small" style={{ width: typeof w === 'number' ? Math.max(w, 100) : w, minWidth: 100 }} value={v as Dayjs | undefined} disabled={dis}
            onChange={(x) => p.onChange(c.name, x)} allowClear={false} />
          : <DatePicker size="small" style={{ width: typeof w === 'number' ? Math.max(w, 120) : w, minWidth: 120 }} value={v as Dayjs | undefined} disabled={dis}
            onChange={(x) => p.onChange(c.name, x ?? dayjs())} allowClear={false} />
      case 'CheckBox':
      case 'SkinCheckBox':
        return <Checkbox checked={!!v} disabled={dis || ro} onChange={(e) => p.onChange(c.name, e.target.checked)}>{text(c)}</Checkbox>
      case 'RadioButton':
      case 'SkinRadioButton':
        return <Checkbox checked={!!v} disabled={dis || ro} onChange={(e) => p.onChange(c.name, e.target.checked)}>{text(c)}</Checkbox>
      case 'SkinButton':
      case 'Button':
        return <Button size="small" type={c.size[1] >= 30 ? 'primary' : 'default'} disabled={dis || !p.buttons?.[c.name]}
          loading={p.busy === c.name} style={{ minWidth: Math.min(Math.max(c.size[0], 70), 140), height: c.size[1] >= 30 ? 34 : 26,
            ...(p.styles?.[c.name]?.backgroundColor ? { borderColor: p.styles[c.name].backgroundColor as string } : {}), ...p.styles?.[c.name] }}
          onClick={() => p.buttons?.[c.name]?.()}>{text(c)}</Button>
      case 'PreviewControl':
        return <PdfPreview data={String(v ?? '')} />
      case 'ProgressBar':
      case 'SkinProgressBar':
        return <Progress percent={Number(v ?? 0)} size="small" style={{ width: w, margin: 0 }} />
      default:
        if (/(MenuStrip|ToolStrip|StatusStrip)$/.test(c.type)) return renderStrip(c)
        if (isGrid(c)) return withContext(c.name, renderGrid(c))
        if (CONTAINERS.has(c.type) || c.type.endsWith('TabPage')) return renderContainer(c)
        return null
    }
  }

  const renderGrid = (c: Ctrl, fillH?: number) => {
    const g = p.grids?.[c.name]
    const hide = new Set(g?.hideColumns ?? [])
    const cols: ColumnsType<Row> = [...(layout.columns[c.name] ?? []), ...(g?.extraColumns ?? [])].filter((x) => x.visible && !hide.has(x.name)).map((x) => ({
      key: x.name,
      title: g?.headerNodes?.[x.name] ?? g?.headers?.[x.name] ?? tr(x.header),
      dataIndex: x.name,
      width: Math.max(x.width ?? 0, (g?.headers?.[x.name] ?? tr(x.header)).length * 8 + 28, 70),
      ellipsis: true,
      render: (_: unknown, r: Row) => {
        const val = g?.value?.[x.name] ? g.value[x.name](r) : r[x.name]
        const opts = g?.options?.[x.name]
        if (opts) {
          const cur = val === null || val === undefined ? undefined : String(val)
          if (!g?.editable || !g.onCellEdit) return opts.find((o) => o.value === cur)?.label ?? cur
          return <Select size="small" style={{ width: '100%' }} value={cur} options={opts} popupMatchSelectWidth={false}
            onClick={(e) => e.stopPropagation()} onChange={(v) => g.onCellEdit!(r, x.name, String(v))} />
        }
        if (g?.editable && !x.readOnly && g.onCellEdit) return <EditableCell value={val} onCommit={(v) => g.onCellEdit!(r, x.name, v)} />
        if (g?.render?.[x.name]) return g.render[x.name](val, r)
        // DataGridViewCheckBoxCell
        if (typeof val === 'boolean') return <Checkbox checked={val} />
        return val as ReactNode
      },
    }))
    const scrollY = fillH ? Math.max(fillH - 34, 30) : Math.max(c.size[1] - 60, 80)
    // Sans lignes, pas de largeur « max-content » : le message « Aucune donnée » élargissait la fenêtre sans fin
    return (
      <div className="wf-grid" style={fillH ? { height: fillH, minWidth: 0, overflow: 'hidden' } : { minHeight: Math.min(c.size[1], 520), minWidth: 0, overflow: 'hidden' }}>
        <Table<Row> size="small" bordered columns={cols} dataSource={g?.rows ?? []} loading={g?.loading}
          rowKey={g?.rowKey ?? ((r) => String(r.id ?? r.key ?? JSON.stringify(r)))}
          pagination={g?.pagination ?? false} scroll={{ x: g?.rows?.length ? 'max-content' : undefined, y: scrollY }} summary={g?.summary}
          rowClassName={(r) => (g?.selectedKey !== undefined && (typeof g.rowKey === 'string' ? r[g.rowKey] : undefined) === g.selectedKey ? 'wf-row-sel' : '')}
          onRow={(r) => ({ onDoubleClick: () => g?.onDoubleClick?.(r), onClick: () => g?.onClick?.(r), style: r.__fore || r.__back ? { color: r.__fore ? String(r.__fore).toLowerCase() : undefined, background: r.__back ? String(r.__back).toLowerCase() : undefined } : undefined })} />
      </div>
    )
  }

  /** Champs non ancrés d'un conteneur : lignes (même hauteur) et colonnes (mêmes abscisses), comme l'original. */
  const renderFlow = (items: Ctrl[]) => {
    if (!items.length) return null
    const cy = (c: Ctrl) => c.loc[1] + c.size[1] / 2
    const sorted = [...items].sort((a, b) => cy(a) - cy(b))
    const rows: Ctrl[][] = []
    for (const c of sorted) {
      const last = rows[rows.length - 1]
      if (last && Math.abs(cy(c) - cy(last[0])) <= 12) last.push(c)
      else rows.push([c])
    }
    // Abscisses des contrôles (hors libellés) → colonnes
    const xs = items.filter((c) => !isLabel(c)).map((c) => c.loc[0]).sort((a, b) => a - b)
    const anchors: number[] = []
    for (const x of xs) if (!anchors.length || x - anchors[anchors.length - 1] > 30) anchors.push(x)
    const anchorOf = (x: number) => {
      let best = 0
      anchors.forEach((a, i) => { if (Math.abs(a - x) < Math.abs(anchors[best] - x)) best = i })
      return best
    }
    // Cellules : plusieurs contrôles peuvent partager une cellule (ex. libellé + « * » obligatoire)
    const slots = new Map<string, Slot>()
    rows.forEach((row, ri) => {
      row.sort((a, b) => a.loc[0] - b.loc[0])
      row.forEach((c, i) => {
        let col: number
        let span = 1
        if (isLabel(c)) {
          const next = row.slice(i + 1).find((x) => !isLabel(x))
          if (next) col = anchorOf(next.loc[0]) * 2 + 1
          else if (anchors.length && c.loc[0] > anchors[anchors.length - 1]) {
            // Libellé seul à droite de tous les champs (ex. aide) : colonne supplémentaire en fin de ligne
            col = anchors.length * 2 + 1
          } else {
            col = anchors.length ? Math.min(anchorOf(c.loc[0]) * 2 + 1, anchors.length * 2) : 1
            span = Math.max(anchors.length * 2 - col + 1, 1)
          }
        } else col = anchorOf(c.loc[0]) * 2 + 2
        const key = `${ri}-${col}`
        const s = slots.get(key)
        if (s) s.items.push(c)
        else slots.set(key, { row: ri + 1, col, span, label: isLabel(c), items: [c] })
      })
    })
    const slotItems = (s: Slot) =>
      s.items.map((c) => <span key={c.name} style={{ display: 'contents' }}>{c.text === '*' && isLabel(c) ? <span className="wf-req">*</span> : renderControl(c)}</span>)
    const cells: ReactNode[] = [...slots.entries()].map(([key, s]) => (
      <div key={key} className={s.label ? 'wf-cell wf-cell-label' : 'wf-cell'}
        style={{ gridRow: s.row, gridColumn: s.span > 1 ? `${s.col} / span ${s.span}` : s.col, gap: 6 }}>
        {slotItems(s)}
      </div>
    ))
    // Colonne de champ : largeur d'origine, réductible si la fenêtre manque de place
    const widths = anchors.map((a) => Math.max(0, ...items.filter((c) => inFlow.has(c.name) && anchorOf(c.loc[0]) === anchors.indexOf(a)).map((c) => c.size[0])))
    const template = anchors.length
      ? widths.map((w) => (w > 0 ? `max-content minmax(${Math.min(w, 90)}px, ${w + 20}px)` : 'max-content max-content')).join(' ') + ' auto'
      : '1fr'
    const grid = <div className="wf-flow" style={{ gridTemplateColumns: template }}>{cells}</div>

    // Fenêtre trop étroite : chaque ligne d'origine devient une suite de paires « libellé (ou case) + champ »
    // qui passent à la ligne ensemble, dans le même ordre.
    const isField = (s: Slot) => !s.label && s.items.some((c) => inFlow.has(c.name))
    const isLead = (s: Slot) => s.label || s.items.every((c) => c.type.includes('CheckBox') || c.type.includes('RadioButton'))
    const lines = new Map<number, Slot[]>()
    for (const sl of [...slots.values()].sort((a, b) => a.col - b.col)) {
      if (!lines.has(sl.row)) lines.set(sl.row, [])
      lines.get(sl.row)!.push(sl)
    }
    const wrapped = (
      <div className="wf-flow-wrap">
        {[...lines.entries()].sort((a, b) => a[0] - b[0]).map(([row, line]) => {
          const groups: Slot[][] = []
          let cur: Slot[] = []
          const flush = () => { if (cur.length) groups.push(cur); cur = [] }
          for (const sl of line) {
            if (!isField(sl) && !isLead(sl)) { flush(); groups.push([sl]); continue }
            if (cur.some(isField)) flush()
            cur.push(sl)
          }
          flush()
          return (
            <div key={row} className="wf-wrap-row">
              {groups.map((g, gi) => (
                <div key={gi} className={g.every((x) => x.label) ? 'wf-pair wf-pair-text' : 'wf-pair'}>
                  {g.map((x) => (isField(x)
                    ? <div key={x.col} className="wf-pair-field" style={{ width: Math.max(...x.items.map((c) => (inFlow.has(c.name) ? c.size[0] : 0)), 90) + 20 }}>{slotItems(x)}</div>
                    : <div key={x.col} className={x.label ? 'wf-pair-label' : 'wf-pair-lead'}>{slotItems(x)}</div>))}
                </div>
              ))}
            </div>
          )
        })}
      </div>
    )
    return <FitFlow grid={grid} wrapped={wrapped} />
  }

  /** Conteneur : champs libres puis enfants ancrés (haut, gauche/remplissage/droite, bas). */
  const renderBody = (parent: string) => {
    const kids = (children[parent] ?? []).filter((c) => !c.type.endsWith('TabPage'))
    // Barres de menus / d'outils / d'état : ancrées en haut (en bas pour la barre d'état) même sans Dock explicite
    const docked = kids.filter((c) => (c.dock || isStrip(c)) && (CONTAINERS.has(c.type) || isGrid(c) || isStrip(c)))
      .map((c) => (isStrip(c) && !c.dock ? { ...c, dock: c.type.endsWith('StatusStrip') ? 'bottom' as const : 'top' as const } : c))
    const free = kids.filter((c) => !docked.includes(c))
    const top = docked.filter((c) => c.dock === 'top')
    const bottom = docked.filter((c) => c.dock === 'bottom')
    const middle = docked.filter((c) => c.dock === 'left' || c.dock === 'fill' || c.dock === 'right')
      .sort((a, b) => a.loc[0] - b.loc[0])
    const box = (c: Ctrl, style?: CSSProperties) => <div key={c.name} style={style}>{renderControl(c)}</div>
    return (
      <div className="wf-body">
        {renderFlow(free)}
        {top.sort((a, b) => a.loc[1] - b.loc[1]).map((c) => box(c))}
        {middle.length > 0 && (
          <div className="wf-middle">
            {middle.map((c) => box(c, c.dock === 'fill'
              // Largeur d'origine comme base : passe à la ligne si elle ne tient pas à côté des autres, puis rétrécit
              ? (isGrid(c) ? { flex: '1 1 0', minWidth: 0 } : { flex: `1 1 ${Math.min(c.size[0], 480)}px`, minWidth: 0 })
              : { flex: '0 1 auto', minWidth: isGrid(c) ? `min(${c.size[0]}px, 100%)` : 0 }))}
          </div>
        )}
        {bottom.sort((a, b) => a.loc[1] - b.loc[1]).map((c) => box(c))}
        {p.extra?.[parent]}
      </div>
    )
  }

  const renderContainer = (c: Ctrl): ReactNode => {
    if (c.type === 'SkinTabControl' || c.type === 'TabControl') {
      const pages = (layout.controls.filter((x) => x.parent === c.name && x.type.endsWith('TabPage') && x.visible && !(p.hidden ?? []).includes(x.name)))
        .sort((a, b) => a.order - b.order)
      const active = p.tab?.[c.name] ?? tabs[c.name] ?? pages[0]?.name
      return (
        <div className="wf-tabs">
          <div className="wf-tabbar">
            {pages.map((pg) => (
              <button key={pg.name} className={pg.name === active ? 'active' : ''}
                onClick={() => { setTabs((t) => ({ ...t, [c.name]: pg.name })); p.onTab?.(c.name, pg.name) }}>{text(pg)}</button>
            ))}
          </div>
          <div className="wf-tabpage">{active && renderBody(active)}</div>
        </div>
      )
    }
    if (c.type === 'GroupBox' || c.type === 'SkinGroupBox') {
      return (
        <fieldset className="wf-group">
          {c.text && <legend>{text(c)}</legend>}
          {renderBody(c.name)}
        </fieldset>
      )
    }
    if (c.type === 'SkinCaptionPanel') {
      return (
        <div className="wf-caption">
          {c.text && <div className="wf-caption-title">{text(c)}</div>}
          {renderBody(c.name)}
        </div>
      )
    }
    const bg = p.backgrounds?.[c.name]
    return (
      <div className="wf-panel" style={{ ...p.styles?.[c.name], ...(bg ? { backgroundImage: `url(/station-web/${bg})`, backgroundSize: 'cover' } : {}) }}
        onClick={(e) => { if (e.target === e.currentTarget) p.onCtlEvent?.(c.name, 'Click') }}>
        {renderBody(c.name)}
      </div>
    )
  }

  // ------------------------------------------------------------ rendu fidèle (fenêtres principales et intégrées)

  const classicKids = useMemo(() => {
    const m: Record<string, Ctrl[]> = {}
    const hidden = new Set(p.hidden ?? [])
    for (const c of layout.controls) {
      if (!c.visible || hidden.has(c.name)) continue
      ;(m[c.parent] ??= []).push(c)
    }
    return m
  }, [layout, p.hidden])

  /** Hauteur mesurée des barres d'outils et de menus passées sur plusieurs lignes (voir StripRow). */
  const [stripHeights, setStripHeights] = useState<Record<string, number>>({})
  const reportStrip = (name: string, h: number) =>
    setStripHeights((s) => (s[name] === h ? s : { ...s, [name]: h }))
  /** Hauteur ajoutée à chaque barre agrandie et à chacun de ses conteneurs (une barre peut être dans un Panel ancré). */
  const stripGrow = useMemo(() => {
    const byName = new Map(layout.controls.map((c) => [c.name, c]))
    const grow: Record<string, number> = {}
    for (const [name, h] of Object.entries(stripHeights)) {
      const extra = h - (byName.get(name)?.size[1] ?? h)
      if (extra <= 0) continue
      for (let n: string | undefined = name; n && n !== '$this'; n = byName.get(n)?.parent) {
        grow[n] = Math.max(grow[n] ?? 0, extra)
      }
    }
    return grow
  }, [stripHeights, layout])

  /** Décalage de la zone cliente (barre de titre des fenêtres Skin dessinée dans la fenêtre d'origine). */
  const rootOffset = useMemo((): [number, number] => {
    const top = (classicKids.$this ?? []).filter((c) => dockOf(c))
    if (!top.length) return [0, 0]
    return [Math.min(...top.map((c) => c.loc[0])), Math.min(...top.map((c) => c.loc[1]))]
  }, [classicKids])

  const classicStyle = (c: Ctrl): CSSProperties => {
    const st: CSSProperties = { ...cssOf(c.style), ...p.styles?.[c.name] }
    const bg = p.backgrounds?.[c.name]
    if (bg) Object.assign(st, { backgroundImage: `url(/station-web/${bg})`, backgroundSize: '100% 100%' })
    return st
  }

  const classicStrip = (c: Ctrl) => {
    const m = layout.menus?.[c.name]
    if (!m) return null
    const items = m.items.filter(shown)
    if (m.type === 'StatusStrip') {
      return (
        <div className="wfc-status" style={classicStyle(c)}>
          {items.map((i) => (i.type === 'ToolStripProgressBar'
            ? <progress key={i.name} className="wf-status-pro" />
            : <span key={i.name}>{itemText(i)}</span>))}
        </div>
      )
    }
    if (m.type === 'MenuStrip') {
      return (
        <div className="wfc-menubar" style={classicStyle(c)}>
          <StripRow onHeight={(h) => reportStrip(c.name, h)}>{renderStrip(c)}</StripRow>
        </div>
      )
    }
    // Tous les boutons restent visibles : ils se resserrent puis passent à la ligne, et la barre s'agrandit
    return (
      <div className="wfc-toolstrip" style={classicStyle(c)}>
        <StripRow className="wfc-toolstrip-row" onHeight={(h) => reportStrip(c.name, h)}>
          {items.map((i) => {
            if (i.type === 'ToolStripSeparator') return <span key={i.name} className="wfc-tool-sep" />
            const dis = (p.disabled ?? []).includes(i.name)
            const label = itemText(i)
            const btn = (
              <button key={i.name} type="button" className="wfc-tool" disabled={dis} title={label}
                style={i.size ? { maxWidth: i.size[0], height: i.size[1] } : undefined}
                onClick={i.items.length ? undefined : () => p.onMenu?.(i.name)}>
                {i.image && <span className="wfc-tool-img" style={{ backgroundImage: `url(/station-web/${i.image})` }} />}
                <span className="wfc-tool-text">{label}</span>
              </button>
            )
            return i.items.length
              ? <Dropdown key={i.name} trigger={['click']} disabled={dis} menu={{ items: menuItems(i.items) }}>{btn}</Dropdown>
              : btn
          })}
        </StripRow>
      </div>
    )
  }

  const classicTabs = (c: Ctrl) => {
    const pages = (classicKids[c.name] ?? []).filter((x) => x.type.endsWith('TabPage')).sort((a, b) => a.order - b.order)
    const active = p.tab?.[c.name] ?? tabs[c.name] ?? pages[0]?.name
    return (
      <div className="wfc-tabs" style={classicStyle(c)}>
        <div className="wfc-tabbar">
          {pages.map((pg) => (
            <button key={pg.name} type="button" className={pg.name === active ? 'active' : ''}
              onClick={() => { setTabs((t) => ({ ...t, [c.name]: pg.name })); p.onTab?.(c.name, pg.name) }}>{text(pg)}</button>
          ))}
        </div>
        {/* Toutes les pages restent montées (fenêtres intégrées conservées) ; seule la page active est visible */}
        {pages.map((pg) => (
          <div key={pg.name} className="wfc-tabpage" style={{ ...classicStyle(pg), display: pg.name === active ? 'block' : 'none' }}>
            {classicBody(pg.name, [0, 0])}
          </div>
        ))}
      </div>
    )
  }

  const click = (c: Ctrl) => () => (p.buttons?.[c.name] ? p.buttons[c.name]() : p.onCtlEvent?.(c.name, 'Click'))

  const classicCtl = (c: Ctrl): ReactNode => {
    if (c.type.endsWith('TabControl')) return classicTabs(c)
    if (isStrip(c)) return classicStrip(c)
    if (isGrid(c)) return withContext(c.name, <Measured>{(h) => renderGrid(c, h)}</Measured>)
    if (isLabel(c)) {
      const img = p.images?.[c.name]
      return (
        <div className="wfc-label" onClick={click(c)}
          style={{ ...alignOf(c.style?.align), ...classicStyle(c), ...(img ? { backgroundImage: `url(/station-web/${img})`, backgroundRepeat: 'no-repeat', backgroundPosition: 'center' } : {}) }}>
          {text(c)}
        </div>
      )
    }
    if (c.type.endsWith('PictureBox')) {
      const img = p.images?.[c.name]
      return <div className="wfc-picture" onClick={click(c)} style={{ ...classicStyle(c), ...(img ? { backgroundImage: `url(/station-web/${img})` } : {}) }} />
    }
    if (c.type === 'FlowLayoutPanel') {
      return (
        <div className="wfc-flow" style={classicStyle(c)} onClick={(e) => { if (e.target === e.currentTarget) p.onCtlEvent?.(c.name, 'Click') }}>
          {[...(classicKids[c.name] ?? [])].sort((a, b) => a.order - b.order).map((k) => (
            <div key={k.name} style={{ width: k.size[0], height: k.size[1], position: 'relative', flex: '0 0 auto' }}>{classicCtl(k)}</div>
          ))}
          {p.extra?.[c.name]}
        </div>
      )
    }
    if (CONTAINERS.has(c.type) || c.type.endsWith('Panel') || c.type.endsWith('GroupBox') || c.type.endsWith('TabPage')) {
      const group = c.type.endsWith('GroupBox')
      return (
        <div className={group ? 'wfc-panel wfc-group' : 'wfc-panel'} style={classicStyle(c)}
          onClick={(e) => { if (e.target === e.currentTarget) p.onCtlEvent?.(c.name, 'Click') }}>
          {group && c.text && <span className="wfc-group-title">{text(c)}</span>}
          {classicBody(c.name, [0, 0])}
        </div>
      )
    }
    // Champs et boutons : contrôle habituel, à la taille de sa zone
    return <div className="wfc-field" style={classicStyle(c)}>{renderControl(c)}</div>
  }

  function classicBody(parent: string, offset: [number, number]): ReactNode {
    const kids = (classicKids[parent] ?? []).filter((c) => !c.type.endsWith('TabPage'))
    const pos = placeChildren(kids, offset, stripGrow)
    // Le premier de l'ordre d'empilement est devant
    const useZ = kids.some((k) => (k.z ?? 0) !== 0)
    return (
      <>
        {kids.map((c) => (
          <div key={c.name} className="wfc-slot" style={{ ...pos.get(c.name), zIndex: 100 - (useZ ? c.z ?? 0 : 0) }}>{classicCtl(c)}</div>
        ))}
        {p.extra?.[parent] && <div className="wfc-extra">{p.extra[parent]}</div>}
      </>
    )
  }

  if (p.classic) {
    const bg = p.backgrounds?.['$this']
    return (
      <div className={p.classic === 'fill' ? 'wfc-root wfc-fill' : 'wfc-root'}
        style={{ ...cssOf(layout.style), ...p.styles?.$this, ...(p.classic === 'fixed' ? { width: layout.size[0], height: layout.size[1] } : {}),
          ...(bg ? { backgroundImage: `url(/station-web/${bg})`, backgroundSize: '100% 100%' } : {}) }}
        onClick={(e) => { if (e.target === e.currentTarget) p.onCtlEvent?.('$this', 'Click') }}>
        {classicBody('$this', rootOffset)}
      </div>
    )
  }


  const rootBg = p.backgrounds?.['$this']
  // Clic sur le fond (Panel / fenêtre) : seulement s il ne vient pas d un contrôle enfant, comme en WinForms
  return <div className="wf-root" style={rootBg ? { backgroundImage: `url(/station-web/${rootBg})`, backgroundSize: 'cover' } : undefined}
    onClick={(e) => { if (e.target === e.currentTarget) p.onCtlEvent?.('$this', 'Click') }}>{renderBody('$this')}</div>
}

/**
 * Contenu d'une barre (outils, menus) qui peut passer sur plusieurs lignes : sa hauteur naturelle est
 * remontée au placement des contrôles ancrés, pour que le reste de la fenêtre se décale au lieu d'être recouvert.
 */
function StripRow({ className, onHeight, children }: { className?: string; onHeight: (h: number) => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  const report = useRef(onHeight)
  report.current = onHeight
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const measure = () => {
      if (el.offsetHeight > 0) report.current(Math.ceil(el.offsetHeight))
    }
    measure()
    // Fenêtres modales : contenu attaché à la page après cet effet
    const t = window.setTimeout(measure, 0)
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    window.addEventListener('resize', measure)
    return () => {
      window.clearTimeout(t)
      ro.disconnect()
      window.removeEventListener('resize', measure)
    }
  }, [])
  return <div ref={ref} className={className}>{children}</div>
}

/**
 * Affiche la grille alignée comme l'original tant qu'elle tient dans la largeur disponible,
 * sinon la version en paires qui passent à la ligne ; revient à la grille si la place le permet.
 */
function FitFlow({ grid, wrapped }: { grid: ReactNode; wrapped: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  const needed = useRef(0)
  const [wrap, setWrap] = useState(false)
  useLayoutEffect(() => {
    const box = ref.current
    if (!box) return
    const check = () => {
      if (!wrap) {
        const g = box.firstElementChild as HTMLElement | null
        if (g && g.scrollWidth > g.clientWidth + 1) {
          needed.current = g.scrollWidth
          setWrap(true)
        }
      } else if (box.clientWidth >= needed.current) setWrap(false)
    }
    check()
    // Dans une fenêtre modale, le contenu n'est attaché à la page qu'après cet effet : nouvelle mesure au tick suivant
    const t = window.setTimeout(check, 0)
    // La largeur de la grille change aussi quand les champs finissent de s'afficher, sans que la boîte bouge
    const ro = new ResizeObserver(check)
    ro.observe(box)
    if (!wrap) Array.from(box.firstElementChild?.children ?? []).forEach((cell) => ro.observe(cell))
    window.addEventListener('resize', check)
    return () => {
      window.clearTimeout(t)
      ro.disconnect()
      window.removeEventListener('resize', check)
    }
  }, [wrap])
  return <div ref={ref} className="wf-fit">{wrap ? wrapped : grid}</div>
}

/** État simple des champs d'une fenêtre. */
export function useFields(init: Record<string, unknown> = {}) {
  const [values, setValues] = useState<Record<string, unknown>>(init)
  const set = (name: string, value: unknown) => setValues((v) => ({ ...v, [name]: value }))
  const patch = (o: Record<string, unknown>) => setValues((v) => ({ ...v, ...o }))
  return { values, set, patch, setValues }
}
