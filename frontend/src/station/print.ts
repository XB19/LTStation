/** Impression d'un ticket ou d'un rapport dans une fenêtre dédiée (imprimante de caisse ou A4). */
export function printHtml(title: string, body: string, width: 'ticket' | 'a4' = 'ticket') {
  const w = window.open('', '_blank', 'width=420,height=640')
  if (!w) return
  const page = width === 'ticket' ? 'max-width: 300px;' : 'max-width: 780px;'
  w.document.write(`<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>${esc(title)}</title>
<style>
  body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 13px; color: #000; margin: 12px auto; ${page} }
  h1 { font-size: 16px; text-align: center; margin: 0 0 4px; }
  h2 { font-size: 14px; margin: 14px 0 6px; border-bottom: 1px solid #000; }
  .c { text-align: center; } .r { text-align: right; } .muted { color: #444; font-size: 12px; }
  table { width: 100%; border-collapse: collapse; } td, th { padding: 3px 2px; text-align: left; } th { border-bottom: 1px solid #000; }
  td.n, th.n { text-align: right; font-variant-numeric: tabular-nums; }
  .big { font-size: 18px; font-weight: 700; }
  hr { border: none; border-top: 1px dashed #000; margin: 8px 0; }
  @page { margin: 8mm; }
</style></head><body>${body}<script>window.onload = () => { window.print(); setTimeout(() => window.close(), 300) }</script></body></html>`)
  w.document.close()
}

export function esc(v: unknown) {
  return String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)
}

export function rows(items: [string, unknown][]) {
  return `<table>${items.map(([k, v]) => `<tr><td>${esc(k)}</td><td class="n">${esc(v)}</td></tr>`).join('')}</table>`
}
