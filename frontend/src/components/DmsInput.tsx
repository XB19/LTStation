import { InputNumber, Space } from 'antd'

/** Saisie degrés / minutes / secondes convertie en degrés décimaux (comme l'original). */
export default function DmsInput({ value, onChange }: { value?: number; onChange?: (v: number | undefined) => void }) {
  const v = value ?? 0
  const deg = Math.trunc(v)
  const minF = (v - deg) * 60
  const min = Math.trunc(minF)
  const sec = Math.round((minF - min) * 60)

  const emit = (d: number, m: number, s: number) => onChange?.(Number((d + m / 60 + s / 3600).toFixed(6)))

  return (
    <Space.Compact>
      <InputNumber value={deg} min={-180} max={180} addonAfter="°" onChange={(d) => emit(d ?? 0, min, sec)} />
      <InputNumber value={min} min={0} max={59} addonAfter="′" onChange={(m) => emit(deg, m ?? 0, sec)} />
      <InputNumber value={sec} min={0} max={59} addonAfter="″" onChange={(s) => emit(deg, min, s ?? 0)} />
    </Space.Compact>
  )
}
