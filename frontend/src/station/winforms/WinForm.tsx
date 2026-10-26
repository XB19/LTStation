import { useMemo, useState, type CSSProperties, type ReactNode } from 'react'
import { Button, Checkbox, DatePicker, Dropdown, Input, Select, Table, TimePicker } from 'antd'
import type { MenuProps } from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import fr from './fr.json'

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
}

export interface MenuItem {
  name: string
  type: string
  text: string
  visible: boolean
  items: MenuItem[]
}

const layouts = import.meta.glob<Layout>('./layouts/*.json', { eager: true, import: 'default' })

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
}

/** Pagination d'origine remplacée par celle des tableaux */
const PAGER = new Set(['CurEnd', 'Cur', 'CurPage', 'Next', 'NextEnd', 'TotalPageSecond', 'TotalPage', 'TotalCount', 'MessageLabel', 'MLabel'])
const isPagerLabel = (c: Ctrl) => c.type.endsWith('Label') && (c.text === '/' || /^\d+$/.test(c.text))

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
    return <Dropdown trigger={['contextMenu']} menu={{ items: menuItems(m.items) }}><div>{node}</div></Dropdown>
  }

  const renderControl = (c: Ctrl): ReactNode => {
    const ro = p.readOnly?.includes(c.name)
    const dis = p.disabled?.includes(c.name)
    const v = p.values[c.name]
    const w: number | string = inFlow.has(c.name) ? '100%' : Math.max(c.size[0], 60)
    switch (c.type) {
      case 'Label':
      case 'SkinLabel':
        return <span className="wf-label" style={p.styles?.[c.name]}>{text(c)}</span>
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
          loading={p.busy === c.name} style={{ minWidth: Math.min(Math.max(c.size[0], 70), 140), height: c.size[1] >= 30 ? 34 : 26 }}
          onClick={() => p.buttons?.[c.name]?.()}>{text(c)}</Button>
      default:
        if (/(MenuStrip|ToolStrip|StatusStrip)$/.test(c.type)) return renderStrip(c)
        if (isGrid(c)) return withContext(c.name, renderGrid(c))
        if (CONTAINERS.has(c.type) || c.type.endsWith('TabPage')) return renderContainer(c)
        return null
    }
  }

  const renderGrid = (c: Ctrl) => {
    const g = p.grids?.[c.name]
    const hide = new Set(g?.hideColumns ?? [])
    const cols: ColumnsType<Row> = (layout.columns[c.name] ?? []).filter((x) => x.visible && !hide.has(x.name)).map((x) => ({
      key: x.name,
      title: g?.headers?.[x.name] ?? tr(x.header),
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
        return g?.render?.[x.name] ? g.render[x.name](val, r) : (val as ReactNode)
      },
    }))
    const scrollY = Math.max(c.size[1] - 60, 80)
    return (
      <div className="wf-grid" style={{ minHeight: Math.min(c.size[1], 520), minWidth: 0, overflow: 'hidden' }}>
        <Table<Row> size="small" bordered columns={cols} dataSource={g?.rows ?? []} loading={g?.loading}
          rowKey={g?.rowKey ?? ((r) => String(r.id ?? r.key ?? JSON.stringify(r)))}
          pagination={g?.pagination ?? false} scroll={{ x: 'max-content', y: scrollY }} summary={g?.summary}
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
    const slots = new Map<string, { row: number; col: number; span: number; label: boolean; items: Ctrl[] }>()
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
    const cells: ReactNode[] = [...slots.entries()].map(([key, s]) => (
      <div key={key} className={s.label ? 'wf-cell wf-cell-label' : 'wf-cell'}
        style={{ gridRow: s.row, gridColumn: s.span > 1 ? `${s.col} / span ${s.span}` : s.col, gap: 6 }}>
        {s.items.map((c) => <span key={c.name} style={{ display: 'contents' }}>{c.text === '*' && isLabel(c) ? <span className="wf-req">*</span> : renderControl(c)}</span>)}
      </div>
    ))
    // Colonne de champ : largeur d'origine, réductible si la fenêtre manque de place
    const widths = anchors.map((a) => Math.max(0, ...items.filter((c) => inFlow.has(c.name) && anchorOf(c.loc[0]) === anchors.indexOf(a)).map((c) => c.size[0])))
    const template = anchors.length
      ? widths.map((w) => (w > 0 ? `max-content minmax(${Math.min(w, 90)}px, ${w + 20}px)` : 'max-content max-content')).join(' ') + ' auto'
      : '1fr'
    return <div className="wf-flow" style={{ gridTemplateColumns: template }}>{cells}</div>
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
              ? (isGrid(c) ? { flex: '1 1 0', minWidth: 0 } : { flex: '1 1 auto', minWidth: 'min-content' })
              : { flex: '0 0 auto', minWidth: isGrid(c) ? c.size[0] : undefined }))}
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
      <div className="wf-panel" style={{ ...p.styles?.[c.name], ...(bg ? { backgroundImage: `url(/station-web/${bg})`, backgroundSize: 'cover' } : {}) }}>
        {renderBody(c.name)}
      </div>
    )
  }

  return <div className="wf-root">{renderBody('$this')}</div>
}

/** État simple des champs d'une fenêtre. */
export function useFields(init: Record<string, unknown> = {}) {
  const [values, setValues] = useState<Record<string, unknown>>(init)
  const set = (name: string, value: unknown) => setValues((v) => ({ ...v, [name]: value }))
  const patch = (o: Record<string, unknown>) => setValues((v) => ({ ...v, ...o }))
  return { values, set, patch, setValues }
}
