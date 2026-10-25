import { useMemo, useState, type CSSProperties, type ReactNode } from 'react'
import { Button, Checkbox, DatePicker, Input, Select, Table, TimePicker } from 'antd'
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
  /** Propriété liée (DataPropertyName) */
  prop?: string | null
  header: string
  visible: boolean
  width: number | null
}

export interface Layout {
  name: string
  title: string
  size: [number, number]
  controls: Ctrl[]
  columns: Record<string, Column[]>
  /** Gestionnaires d'événements du .cs d'origine : contrôle ('$this' = fenêtre) → événement → méthode */
  events?: Record<string, Record<string, string>>
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
}

/** Pagination d'origine remplacée par celle des tableaux */
const PAGER = new Set(['CurEnd', 'Cur', 'CurPage', 'Next', 'NextEnd', 'TotalPageSecond', 'TotalPage', 'TotalCount', 'MessageLabel', 'MLabel'])
const isPagerLabel = (c: Ctrl) => c.type.endsWith('Label') && (c.text === '/' || /^\d+$/.test(c.text))

const CONTAINERS = new Set(['SkinTabControl', 'TabControl', 'GroupBox', 'SkinCaptionPanel', 'Panel', 'SkinPanel', 'SkinGroupBox'])
const isLabel = (c: Ctrl) => c.type === 'Label' || c.type === 'SkinLabel'
const isGrid = (c: Ctrl) => c.type.endsWith('DataGridView')

export default function WinForm(p: WinFormProps) {
  const { layout } = p
  const [tabs, setTabs] = useState<Record<string, string>>({})
  const children = useMemo(() => {
    const m: Record<string, Ctrl[]> = {}
    const hidden = new Set(p.hidden ?? [])
    for (const c of layout.controls) {
      if (!c.visible || hidden.has(c.name) || PAGER.has(c.name) || isPagerLabel(c)) continue
      if (isLabel(c) && !c.text && !p.texts?.[c.name]) continue
      ;(m[c.parent] ??= []).push(c)
    }
    return m
  }, [layout, p.hidden, p.texts])

  const inFlow = useMemo(() => new Set(layout.controls.filter((c) => !c.dock && !isLabel(c) && !c.type.includes('Button') && !c.type.includes('CheckBox')
    && !CONTAINERS.has(c.type) && !isGrid(c)).map((c) => c.name)), [layout])

  const text = (c: Ctrl) => (p.texts?.[c.name] !== undefined ? p.texts[c.name] : tr(c.text))

  const renderControl = (c: Ctrl): ReactNode => {
    const ro = p.readOnly?.includes(c.name)
    const dis = p.disabled?.includes(c.name)
    const v = p.values[c.name]
    const w: number | string = inFlow.has(c.name) ? '100%' : Math.max(c.size[0], 60)
    switch (c.type) {
      case 'Label':
      case 'SkinLabel':
        return <span className="wf-label">{text(c)}</span>
      case 'TextBox':
      case 'SkinTextBox':
      case 'RichTextBox':
      case 'NumericUpDown': {
        const Comp = p.passwords?.includes(c.name) ? Input.Password : Input
        return <Comp size="small" style={{ width: w }} value={(v as string) ?? ''} readOnly={ro} disabled={dis}
          className={ro ? 'wf-ro' : undefined} onChange={(e) => p.onChange(c.name, e.target.value)} />
      }
      case 'SkinComboBox':
      case 'ComboBox':
        return <Select size="small" style={{ width: w }} value={v as string | number | undefined} disabled={dis || ro}
          options={p.options?.[c.name] ?? []} onChange={(x) => p.onChange(c.name, x)} showSearch optionFilterProp="label"
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
        if (isGrid(c)) return renderGrid(c)
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
          onRow={(r) => ({ onDoubleClick: () => g?.onDoubleClick?.(r), onClick: () => g?.onClick?.(r) })} />
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
    const docked = kids.filter((c) => c.dock && (CONTAINERS.has(c.type) || isGrid(c)))
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
    return <div className="wf-panel">{renderBody(c.name)}</div>
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
