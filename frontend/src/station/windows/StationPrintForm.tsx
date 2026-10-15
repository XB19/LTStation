import { useEffect } from 'react'
import { message } from 'antd'
import { get, put } from '../../api/http'
import WinForm, { getLayout, useFields } from '../winforms/WinForm'
import Win from '../winforms/Win'

const L = getLayout('StationPrintForm')
export const PRINT_LINES = ['First', 'Second', 'Third', 'Fourth', 'Fiveth', 'Sixth']

/** Lignes imprimées en tête du ticket (StationPrintForm). */
export default function StationPrintForm({ onClose }: { onClose: () => void }) {
  const f = useFields()
  useEffect(() => {
    get<Record<string, string>>('/station/settings').then((s) => f.patch(Object.fromEntries(PRINT_LINES.map((k) => [k, s[`print.${k}`] ?? '']))))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return (
    <Win layout={L} onClose={onClose} title="Impression du ticket">
      <WinForm layout={L} values={f.values} onChange={f.set}
        buttons={{
          SaveButton: async () => {
            await put('/station/settings', Object.fromEntries(PRINT_LINES.map((k) => [`print.${k}`, String(f.values[k] ?? '')])))
            message.success('Enregistré')
            onClose()
          },
        }} />
    </Win>
  )
}
