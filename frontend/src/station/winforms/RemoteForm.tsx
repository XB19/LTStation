import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Button, Checkbox, Modal, Space } from 'antd'
import dayjs from 'dayjs'
import { http } from '../../api/http'
import Win from './Win'
import WinForm, { getLayout, tr, type GridProps, type Option } from './WinForm'

/**
 * Fenêtre du logiciel Station dont la logique s'exécute sur le serveur (/api/sw/form) : la mise en
 * page d'origine est affichée par WinForm, chaque événement déclaré dans le .cs d'origine (Click,
 * SelectedIndexChanged, CellClick, Tick…) est envoyé au serveur avec la saisie, puis l'état
 * modifié, les messages et les questions (Oui/Non, sous-fenêtre) sont appliqués.
 */

type Props = Record<string, unknown>
type Row = Record<string, unknown>

interface GridState {
  rows: Row[]
  current: number
  visible?: Record<string, boolean>
  headers?: Record<string, string>
  options?: Record<string, { value: string; label: string }[]>
  /** Colonnes ajoutées par le code (Columns.Add) */
  added?: { name: string; header: string; width: number; check: boolean }[]
}

interface Suspend {
  type: 'confirm' | 'dialog'
  key: string
  text?: string
  form?: string
  args?: Props
}

interface Message {
  text?: string
  caption?: string
  error?: boolean
  open?: string
  args?: Props
  download?: string
  contentType?: string
  data?: string
  print?: string[]
  /** Sous-fenêtre / question dont la suite est exécutée sur le serveur ($continue) */
  dialog?: string
  confirm?: string
  key?: string
  /** ExecuteJavascript d'une fenêtre web */
  js?: string
  /** OpenFileDialog : filtre d'origine ("Excel(*.xls)|*.xls") */
  openFile?: string
  /** SaveFileDialog : filtre d'origine ("PDF(*.pdf)|*.pdf|Excel(*.xls)|*.xls") ; le poste choisit le type */
  saveFile?: string
  /** Fenêtre enfant intégrée dans un conteneur (TopLevel = false) */
  embed?: string
  id?: string
  into?: string
}

interface FormState {
  id?: string
  controls: Record<string, Props>
  grids: Record<string, GridState>
  messages: Message[]
  timers?: Record<string, number>
  title?: string
  suspend?: Suspend
  /** Événements branchés dans le code d'origine (en plus de ceux du concepteur) */
  events?: Record<string, Record<string, string>>
  /** Fenêtre web (Formium) : page d'origine et fonctions de l'objet « my » */
  web?: string
  my?: Record<string, string>
  close?: string
  result?: Props
  /** Modifications de la fenêtre propriétaire faites par cette fenêtre */
  owner?: FormState & { id: string }
}

/** Fenêtres affichées, par identifiant serveur : saisie courante et application d une réponse (fenêtres propriétaires). */
const live = new Map<string, { values: () => Record<string, Props>; apply: (s: FormState) => void }>()

export interface RemoteFormProps {
  form: string
  args?: Props
  /** Fermeture : résultat (OK / Cancel…) et valeurs renvoyées à la fenêtre parente */
  onClose: (result: string, data: Props) => void
  /** Ouverture d'une autre fenêtre sans attendre (Form.Show()) ; parent = fenêtre propriétaire (Owner) */
  onOpen?: (form: string, args: Props, parent?: string, attachId?: string) => void
  /** Fenêtre propriétaire (Owner) côté serveur */
  parent?: string
  /** Fenêtre déjà créée sur le serveur (fenêtre intégrée) : rattachement sans ouverture */
  attachId?: string
  /** Affichage dans une fenêtre modale (par défaut) ou directement dans la page */
  framed?: boolean
  extra?: Record<string, ReactNode>
}

const TEXT_TYPES = /TextBox|NumericUpDown|Label|Button|GroupBox|CaptionPanel|TabPage|RadioButton|CheckBox/
const isCombo = (t: string) => t.endsWith('ComboBox') || t === 'ListBox'
const isDate = (t: string) => t.endsWith('DateTimePicker')
const isCheck = (t: string) => t.endsWith('CheckBox') || t.endsWith('RadioButton')

function download(m: Message) {
  const bin = atob(m.data ?? '')
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  const url = URL.createObjectURL(new Blob([bytes], { type: m.contentType }))
  const a = document.createElement('a')
  a.href = url
  a.download = m.download ?? 'export'
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function printLines(lines: string[]) {
  const w = window.open('', '_blank', 'width=420,height=640')
  if (!w) return
  const esc = (s: string) => s.replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]!)
  w.document.write(`<!doctype html><html><head><meta charset="utf-8"><style>body{font-family:monospace;font-size:12px;white-space:pre;margin:8px}</style></head>
<body>${lines.map(esc).join('\n')}<script>window.onload=()=>{window.print();setTimeout(()=>window.close(),300)}</script></body></html>`)
  w.document.close()
}

const ask = (text: string) => new Promise<boolean>((resolve) => Modal.confirm({
  title: tr(text), okText: 'Oui', cancelText: 'Non', onOk: () => resolve(true), onCancel: () => resolve(false),
}))

/** SaveFileDialog : choix du type de fichier parmi le filtre (null = annulé) ; le fichier est ensuite téléchargé. */
const chooseSave = (filter: string) => new Promise<string | null>((resolve) => {
  const parts = filter.split('|')
  const types: { label: string; ext: string }[] = []
  for (let i = 0; i + 1 < parts.length; i += 2) types.push({ label: parts[i], ext: parts[i + 1].split(';')[0].replace(/^\*/, '') })
  if (types.length <= 1) {
    resolve(types[0]?.ext ?? '')
    return
  }
  const m = Modal.confirm({
    title: 'Enregistrer sous',
    content: (
      <Space wrap>
        {types.map((t) => <Button key={t.ext} onClick={() => { m.destroy(); resolve(t.ext) }}>{t.label}</Button>)}
      </Space>
    ),
    okButtonProps: { style: { display: 'none' } },
    cancelText: 'Annuler',
    onCancel: () => resolve(null),
  })
})

const show = (m: Message) => new Promise<void>((resolve) => (m.error ? Modal.error : Modal.info)({
  title: m.caption ? tr(m.caption) : undefined, content: tr(m.text ?? ''), okText: 'OK', onOk: () => resolve(), onCancel: () => resolve(),
}))

export default function RemoteForm({ form, args, onClose, onOpen, parent, attachId, framed = true, extra }: RemoteFormProps) {
  const layout = useMemo(() => getLayout(form), [form])
  const types = useMemo(() => Object.fromEntries(layout.controls.map((c) => [c.name, c.type])), [layout])
  const [extraEvents, setExtraEvents] = useState<Record<string, Record<string, string>>>({})
  const events = useMemo(() => {
    const m: Record<string, Record<string, string>> = { ...(layout.events ?? {}) }
    for (const [k, v] of Object.entries(extraEvents)) m[k] = { ...m[k], ...v }
    return m
  }, [layout, extraEvents])
  const [id, setId] = useState<string>()
  const [ctl, setCtl] = useState<Record<string, Props>>({})
  const [grids, setGrids] = useState<Record<string, GridState>>({})
  const [title, setTitle] = useState<string>()
  const [busy, setBusy] = useState<string | null>(null)
  const [dialog, setDialog] = useState<{ form: string; args: Props; resolve: (r: { result: string; data: Props }) => void } | null>(null)
  const ctlRef = useRef(ctl)
  ctlRef.current = ctl
  const gridRef = useRef(grids)
  gridRef.current = grids
  const idRef = useRef<string>()
  const queue = useRef(Promise.resolve())
  const timers = useRef<Record<string, number>>({})
  const lastCol = useRef<Record<string, string>>({})
  const closed = useRef(false)
  const [web, setWeb] = useState<{ url: string; my: Record<string, string> } | null>(null)
  const [embeds, setEmbeds] = useState<Record<string, { form: string; id: string }>>({})
  const frame = useRef<HTMLIFrameElement>(null)
  const frameLoaded = useRef(false)
  const jsQueue = useRef<string[]>([])

  /** ExecuteJavascript : exécuté dans la page d'origine (en attente tant qu'elle n'est pas chargée). */
  const runJs = (js: string) => {
    const w = frame.current?.contentWindow as (Window & { eval: (s: string) => unknown }) | null
    if (!frameLoaded.current || !w) {
      jsQueue.current.push(js)
      return
    }
    try {
      w.eval(js)
    } catch (err) {
      console.error('ExecuteJavascript', js, err)
    }
  }

  const merge = (patch: Record<string, Props>) => setCtl((c) => {
    const n = { ...c }
    for (const [k, v] of Object.entries(patch)) n[k] = { ...n[k], ...v }
    ctlRef.current = n
    return n
  })

  /** Saisie de l'opérateur envoyée avec chaque événement. */
  const clientValues = () => {
    const out: Record<string, Props> = {}
    for (const [k, p] of Object.entries(ctlRef.current)) {
      const t = types[k] ?? ''
      const v: Props = {}
      if ('Text' in p && !isCombo(t)) v.Text = p.Text
      if ('Checked' in p) v.Checked = p.Checked
      if ('SelectedIndex' in p) v.SelectedIndex = p.SelectedIndex
      if ('Value' in p) v.Value = p.Value
      if (Object.keys(v).length) out[k] = v
    }
    return out
  }

  const clientGrids = () => Object.fromEntries(Object.entries(gridRef.current).map(([k, g]) => [k, g.current]))

  const sub = (f: string, a: Props) => new Promise<{ result: string; data: Props }>((resolve) => setDialog({ form: f, args: a, resolve }))

  /** OpenFileDialog : sélection d'un fichier envoyé au serveur (contenu en base64). */
  const pickFile = (filter: string, key: string) => {
    const input = document.createElement('input')
    input.type = 'file'
    const patterns = (filter.split('|')[1] ?? '').split(';').map((x) => x.trim().replace(/^\*/, '')).filter((x) => x && x !== '.*')
    if (patterns.length) input.accept = patterns.join(',')
    input.onchange = () => {
      const file = input.files?.[0]
      if (!file) return
      const reader = new FileReader()
      reader.onload = () => {
        const content = String(reader.result).split(',')[1] ?? ''
        fire('$continue', '', { key, result: 'OK', data: { name: file.name, content } })
      }
      reader.readAsDataURL(file)
    }
    input.click()
  }

  /** Applique une réponse du serveur ; renvoie la question en attente éventuelle. */
  const apply = async (s: FormState) => {
    merge(s.controls ?? {})
    if (s.grids && Object.keys(s.grids).length) {
      setGrids((g) => {
        const n = { ...g, ...s.grids }
        gridRef.current = n
        return n
      })
    }
    if (s.title) setTitle(s.title)
    if (s.events) setExtraEvents(s.events)
    if (s.web) setWeb({ url: s.web, my: s.my ?? {} })
    for (const [h, ms] of Object.entries(s.timers ?? {})) {
      window.clearInterval(timers.current[h])
      delete timers.current[h]
      if (ms > 0) timers.current[h] = window.setInterval(() => fire(h, '', {}, true), ms)
    }
    for (const m of s.messages ?? []) {
      if (m.dialog) {
        const r = await sub(m.dialog, m.args ?? {})
        fire('$continue', '', { key: m.key, result: r.result, data: r.data })
      } else if (m.confirm !== undefined) {
        const yes = await ask(m.confirm)
        fire('$continue', '', { key: m.key, result: yes ? 'OK' : 'Cancel', data: {} })
      } else if (m.open) onOpen?.(m.open, m.args ?? {}, idRef.current, m.id)
      else if (m.openFile !== undefined) pickFile(m.openFile, m.key!)
      else if (m.saveFile !== undefined) {
        const ext = await chooseSave(m.saveFile)
        fire('$continue', '', { key: m.key, result: ext === null ? 'Cancel' : 'OK', data: { ext: ext ?? '' } })
      }
      else if (m.js !== undefined) runJs(m.js)
      else if (m.embed && m.id && m.into) setEmbeds((x) => ({ ...x, [m.into!]: { form: m.embed!, id: m.id! } }))
      else if (m.download) download(m)
      else if (m.print) printLines(m.print)
      else await show(m)
    }
    if (s.close && !closed.current) {
      closed.current = true
      onClose(s.close, s.result ?? {})
    }
    return s.suspend
  }

  /** Envoie un événement ; les questions sont posées puis l'événement est rejoué avec les réponses. */
  const fire = (handler: string, sender: string, e: Props, quiet = false): Promise<void> => {
    const run = async () => {
      if (!idRef.current || closed.current) return
      const answers: Props = {}
      if (!quiet) setBusy(sender || handler)
      try {
        for (;;) {
          const { data } = await http.post<FormState>(`/sw/form/${idRef.current}/event`,
            { handler, sender, e, values: clientValues(), grids: clientGrids(), answers,
              ownerValues: parent ? live.get(parent)?.values() : undefined })
          if (data.owner) live.get(data.owner.id)?.apply(data.owner)
          const q = await apply(data)
          if (!q) break
          answers[q.key] = q.type === 'confirm' ? await ask(q.text ?? '') : await sub(q.form!, q.args ?? {})
        }
      } finally {
        if (!quiet) setBusy(null)
      }
    }
    queue.current = queue.current.then(run, run)
    return queue.current
  }

  const liveRef = useRef({ values: clientValues, apply: (st: FormState) => { void apply(st) } })
  liveRef.current = { values: clientValues, apply: (st: FormState) => { void apply(st) } }
  useEffect(() => {
    if (!id) return
    live.set(id, { values: () => liveRef.current.values(), apply: (st) => liveRef.current.apply(st) })
    return () => { live.delete(id) }
  }, [id])

  useEffect(() => {
    closed.current = false
    let alive = true
    ;(async () => {
      const answers: Props = {}
      if (attachId) {
        idRef.current = attachId
        setId(attachId)
        const { data } = await http.post<FormState>(`/sw/form/${attachId}/state`, {})
        if (alive) await apply(data)
        return
      }
      for (;;) {
        const { data } = await http.post<FormState>('/sw/form/open', { form, args: args ?? {}, answers, parent })
        if (!alive) {
          if (data.id) http.post(`/sw/form/${data.id}/close`, {}).catch(() => undefined)
          return
        }
        idRef.current = data.id
        setId(data.id)
        const q = await apply(data)
        if (!q) break
        answers[q.key] = q.type === 'confirm' ? await ask(q.text ?? '') : await sub(q.form!, q.args ?? {})
      }
    })().catch(() => { if (alive) onClose('Cancel', {}) })
    return () => {
      alive = false
      Object.values(timers.current).forEach((t) => window.clearInterval(t))
      timers.current = {}
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form])

  /** Fermeture par la croix (FormClosing peut l'annuler). */
  const requestClose = () => {
    const run = async () => {
      if (!idRef.current) { onClose('Cancel', {}); return }
      const { data } = await http.post<FormState>(`/sw/form/${idRef.current}/close`, { values: clientValues(), grids: clientGrids() })
      await apply(data)
    }
    queue.current = queue.current.then(run, run)
  }

  const on = (name: string, ev: string) => events[name]?.[ev]

  // ------------------------------------------------------------ valeurs pour WinForm
  const values: Record<string, unknown> = {}
  const options: Record<string, Option[]> = {}
  const hidden: string[] = []
  const disabled: string[] = []
  const readOnly: string[] = []
  const texts: Record<string, string> = {}
  const styles: Record<string, CSSProperties> = {}
  const images: Record<string, string> = {}
  const backgrounds: Record<string, string> = {}
  for (const [n, pr] of Object.entries(ctl)) {
    const st: CSSProperties = {}
    if (pr.BackColor) st.backgroundColor = String(pr.BackColor).toLowerCase()
    if (pr.ForeColor) st.color = String(pr.ForeColor).toLowerCase()
    if (Object.keys(st).length) styles[n] = st
    if (pr.Image) images[n] = String(pr.Image)
    if (pr.BackgroundImage) backgrounds[n] = String(pr.BackgroundImage)
  }
  for (const c of layout.controls) {
    const p = ctl[c.name]
    if (!p) continue
    const t = c.type
    if (isCombo(t)) {
      options[c.name] = ((p.Items as unknown[]) ?? []).map((x, i) => ({ value: i, label: String(x ?? '') }))
      const i = Number(p.SelectedIndex ?? -1)
      // DropDown : texte libre (Text) quand aucun élément n'est choisi
      values[c.name] = i >= 0 ? i : (p.Text ? String(p.Text) : undefined)
    } else if (isDate(t)) values[c.name] = p.Value ? dayjs(String(p.Value)) : undefined
    else if (t.endsWith('ProgressBar')) {
      // ProgressBar : pourcentage de Value entre Minimum et Maximum (100 par défaut)
      const max = Number(p.Maximum ?? 100), min = Number(p.Minimum ?? 0)
      values[c.name] = max > min ? Math.round((100 * (Number(p.Value ?? 0) - min)) / (max - min)) : 0
    } else if (t === 'PreviewControl') values[c.name] = p.Pdf ?? ''
    else if (isCheck(t)) values[c.name] = !!p.Checked
    else values[c.name] = p.Text ?? ''
    if ('Text' in p && TEXT_TYPES.test(t) && !/TextBox|NumericUpDown/.test(t)) texts[c.name] = tr(String(p.Text ?? ''))
    if (p.Visible === false) hidden.push(c.name)
    if (p.Enabled === false) disabled.push(c.name)
    if (p.ReadOnly === true) readOnly.push(c.name)
  }
  // Éléments de menu (hors liste des contrôles) : Visible / Enabled / Text
  for (const [n, p] of Object.entries(ctl)) {
    if (types[n]) continue
    if (p.Visible === false) hidden.push(n)
    if (p.Enabled === false) disabled.push(n)
    if ('Text' in p) texts[n] = tr(String(p.Text ?? ''))
  }
  // Champs encore jamais envoyés par le serveur : texte d'origine
  for (const c of layout.controls) {
    if (ctl[c.name] || values[c.name] !== undefined) continue
    if (isCheck(c.type)) values[c.name] = false
    else if (/TextBox|NumericUpDown/.test(c.type)) values[c.name] = c.text ?? ''
  }

  // Onglets : SelectedIndex du TabControl (serveur) ↔ onglet affiché
  const pagesOf = (tabControl: string) => layout.controls.filter((x) => x.parent === tabControl && x.type.endsWith('TabPage'))
    .sort((a, b) => a.order - b.order).map((x) => x.name)
  const tabs: Record<string, string> = {}
  for (const c of layout.controls) {
    if (!/TabControl$/.test(c.type)) continue
    const i = Number(ctl[c.name]?.SelectedIndex ?? -1)
    const pages = pagesOf(c.name)
    if (i >= 0 && i < pages.length) tabs[c.name] = pages[i]
  }
  const onTab = (tabControl: string, page: string) => {
    const i = pagesOf(tabControl).indexOf(page)
    merge({ [tabControl]: { SelectedIndex: i } })
    const h = on(tabControl, 'SelectedIndexChanged')
    if (h) fire(h, tabControl, {}, true)
  }

  const onChange = (name: string, v: unknown) => {
    const t = types[name] ?? ''
    let patch: Props
    let ev: string | undefined
    if (isCombo(t)) { patch = { SelectedIndex: v as number, Text: undefined }; ev = on(name, 'SelectedIndexChanged') ?? on(name, 'SelectedValueChanged') }
    else if (isDate(t)) { patch = { Value: dayjs(v as dayjs.Dayjs).format('YYYY-MM-DD HH:mm:ss') }; ev = on(name, 'ValueChanged') }
    else if (isCheck(t)) { patch = { Checked: !!v }; ev = on(name, 'CheckedChanged') ?? on(name, 'CheckStateChanged') }
    else { patch = { Text: String(v ?? '') }; ev = on(name, 'TextChanged') }
    if (isCombo(t)) {
      setCtl((c) => {
        const n = { ...c, [name]: { ...c[name], SelectedIndex: v as number } }
        delete (n[name] as Props).Text
        ctlRef.current = n
        return n
      })
    } else merge({ [name]: patch })
    // Bouton radio : les autres boutons du même conteneur sont décochés, leurs événements partent d'abord
    if (t.endsWith('RadioButton') && v) {
      const me = layout.controls.find((c) => c.name === name)
      for (const o of layout.controls) {
        if (o.name === name || !o.type.endsWith('RadioButton') || o.parent !== me?.parent || !ctlRef.current[o.name]?.Checked) continue
        merge({ [o.name]: { Checked: false } })
        const h = on(o.name, 'CheckedChanged')
        if (h) fire(h, o.name, {}, true)
      }
    }
    if (ev) fire(ev, name, {}, true)
    // ComboBox : choix de l'utilisateur (après SelectedIndexChanged)
    if (isCombo(t)) {
      const sc = on(name, 'SelectionChangeCommitted')
      if (sc) fire(sc, name, {}, true)
    }
  }

  const buttons: Record<string, () => unknown> = {}
  for (const c of layout.controls) {
    const h = on(c.name, 'Click')
    if (h && (c.type.includes('Button') || c.type.endsWith('Label') || c.type === 'PictureBox')) buttons[c.name] = () => fire(h, c.name, {})
  }

  const gridProps: Record<string, GridProps> = {}
  for (const [name, layoutCols] of Object.entries(layout.columns)) {
    const g = grids[name]
    const extra = (g?.added ?? []).map((a) => ({ name: a.name, header: a.header, visible: true, width: a.width, readOnly: true, check: a.check }))
    const cols = [...layoutCols, ...extra]
    const colIndex = (col?: string) => (col ? cols.findIndex((x) => x.name === col) : -1)
    const select = (r: Row) => {
      const i = Number(r.__i)
      setGrids((gs) => {
        const n = { ...gs, [name]: { ...(gs[name] ?? { rows: [] }), current: i } }
        gridRef.current = n
        return n
      })
      return i
    }
    const cellArgs = (r: Row) => {
      const col = lastCol.current[name]
      return { RowIndex: Number(r.__i), ColumnIndex: colIndex(col), ColumnName: col ?? '' }
    }
    gridProps[name] = {
      rows: g?.rows ?? [],
      rowKey: '__i',
      selectedKey: g && g.current >= 0 ? g.current : undefined,
      hideColumns: Object.entries(g?.visible ?? {}).filter(([, v]) => !v).map(([k]) => k),
      headers: g?.headers,
      options: g?.options,
      pagination: (g?.rows.length ?? 0) > 200 ? { pageSize: 100, showSizeChanger: false } : false,
      render: Object.fromEntries(cols.map((x) => [x.name, (v: unknown) => (
        <span onClick={() => { lastCol.current[name] = x.name }}>
          {typeof v === 'boolean' || ('check' in x && x.check) ? <Checkbox checked={!!v} /> : v === null || v === undefined ? '' : String(v)}
        </span>
      )])),
      extraColumns: extra,
      // En-tête d une colonne à cocher (CellPainting + CellMouseUp sur la ligne -1 dans l original)
      headerNodes: Object.fromEntries(extra.filter((x) => x.check).map((x) => [x.name, (
        <Checkbox checked={(g?.rows.length ?? 0) > 0 && (g?.rows ?? []).every((r) => r[x.name] === true)}
          onChange={() => { const h = on(name, 'CellMouseUp'); if (h) fire(h, name, { RowIndex: -1, ColumnIndex: colIndex(x.name) }) }} />
      )])),
      onClick: (r) => {
        const prev = gridRef.current[name]?.current
        select(r)
        const e = cellArgs(r)
        if (prev !== Number(r.__i)) {
          const sc = on(name, 'SelectionChanged') ?? on(name, 'CurrentCellChanged')
          if (sc) fire(sc, name, e, true)
        }
        for (const ev of ['CellClick', 'CellContentClick', 'Click']) {
          const h = on(name, ev)
          if (h) fire(h, name, e)
        }
      },
      editable: !!on(name, 'CellEndEdit') && ctl[name]?.ReadOnly !== true,
      onCellEdit: (r, col, value) => {
        const i = Number(r.__i)
        setGrids((gs) => {
          const cur = gs[name] ?? { rows: [], current: -1 }
          const rows = cur.rows.map((x) => (Number(x.__i) === i ? { ...x, [col]: value } : x))
          const n = { ...gs, [name]: { ...cur, rows, current: i } }
          gridRef.current = n
          return n
        })
        const h = on(name, 'CellEndEdit')
        if (h) fire(h, name, { RowIndex: i, ColumnIndex: colIndex(col), ColumnName: col, EditValue: value })
      },
      onDoubleClick: (r) => {
        select(r)
        for (const ev of ['CellDoubleClick', 'CellContentDoubleClick', 'DoubleClick']) {
          const h = on(name, ev)
          if (h) fire(h, name, cellArgs(r))
        }
      },
    }
  }

  const body = (
    <>
      <WinForm layout={layout} values={values} onChange={onChange} buttons={buttons} options={options} grids={gridProps}
        hidden={hidden} disabled={disabled} readOnly={readOnly} texts={texts} busy={busy}
        passwords={layout.controls.filter((c) => ctl[c.name]?.PasswordChar).map((c) => c.name)}
        autoComplete={Object.fromEntries(Object.entries(ctl).filter(([, pr]) => Array.isArray(pr.AutoCompleteCustomSource))
          .map(([n, pr]) => [n, (pr.AutoCompleteCustomSource as unknown[]).map(String)]))}
        timeOnly={layout.controls.filter((c) => ctl[c.name]?.Format === 'Time').map((c) => c.name)}
        extra={{ ...extra, ...Object.fromEntries(Object.entries(embeds).map(([into, e]) => [into,
          <RemoteForm key={e.id} form={e.form} attachId={e.id} framed={false} onOpen={onOpen} onClose={() => setEmbeds((x) => { const n = { ...x }; delete n[into]; return n })} />])) }} onMenu={(item) => { const h = on(item, 'Click'); if (h) fire(h, item, {}) }}
        styles={styles} images={images} backgrounds={backgrounds} keepPager
        onCtlEvent={(name, ev) => { const h = on(name, ev); if (h) fire(h, name, {}) }}
        tab={tabs} onTab={onTab}
        onDropDown={(name, open) => { const h = on(name, open ? 'DropDown' : 'DropDownClosed'); if (h) fire(h, name, {}) }} />
      {dialog && (
        <RemoteForm form={dialog.form} args={dialog.args} onOpen={onOpen} parent={idRef.current}
          onClose={(result, data) => { const d = dialog; setDialog(null); d.resolve({ result, data }) }} />
      )}
    </>
  )
  /** Page chargée : objet « my » injecté, scripts en attente exécutés, puis LoadHandler.OnLoadEnd. */
  const onFrameLoad = () => {
    const w = frame.current?.contentWindow as (Window & { my?: Record<string, (...a: unknown[]) => void> }) | null
    if (!w || !web) return
    w.my = {}
    for (const [name, handler] of Object.entries(web.my)) {
      w.my[name] = (...a: unknown[]) => {
        const arg = a.find((x) => typeof x === 'string') ?? (a[0] === undefined ? '' : String(a[0]))
        fire(handler, '', { arg })
      }
    }
    frameLoaded.current = true
    const q = jsQueue.current.splice(0)
    q.forEach(runJs)
    fire('LoadHandler_OnLoadEnd', '', {}, true)
  }

  const webBody = web && (
    <>
      <iframe ref={frame} title={layout.title} src={`/station-web/${web.url}`} onLoad={onFrameLoad}
        style={{ width: '100%', height: Math.max(layout.size[1], 320), border: 0, display: 'block' }} />
      {dialog && (
        <RemoteForm form={dialog.form} args={dialog.args} onOpen={onOpen} parent={idRef.current}
          onClose={(result, data) => { const d = dialog; setDialog(null); d.resolve({ result, data }) }} />
      )}
    </>
  )
  if (webBody) {
    if (!framed) return webBody
    return <Win layout={layout} title={title ? tr(title) : undefined} width={layout.size[0] + 48} onClose={requestClose}>{webBody}</Win>
  }
  if (!framed) return id ? body : null
  return (
    <Win layout={layout} title={title ? tr(title) : undefined} onClose={requestClose}>
      {body}
    </Win>
  )
}
