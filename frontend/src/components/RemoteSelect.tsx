import { useEffect, useState } from 'react'
import { Select, type SelectProps } from 'antd'
import { get, type SelectItem } from '../api/http'

/** Liste déroulante alimentée par /api/select/{type} (Home/GetICSelect d'origine). */
export function useSelectOptions(type: string, params?: Record<string, unknown>, enabled = true) {
  const [options, setOptions] = useState<SelectItem[]>([])
  const key = JSON.stringify(params ?? {})
  useEffect(() => {
    if (!enabled) {
      setOptions([])
      return
    }
    let alive = true
    get<SelectItem[]>(`/select/${type}`, params)
      .then((r) => alive && setOptions(r))
      .catch(() => alive && setOptions([]))
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type, key, enabled])
  return options
}

type Props = Omit<SelectProps, 'options'> & {
  type: string
  params?: Record<string, unknown>
  enabled?: boolean
  /** Utiliser le libellé comme valeur (listes de noms) */
  textAsValue?: boolean
}

export default function RemoteSelect({ type, params, enabled = true, textAsValue, ...rest }: Props) {
  const options = useSelectOptions(type, params, enabled)
  return (
    <Select
      allowClear
      showSearch
      optionFilterProp="label"
      placeholder="Veuillez sélectionner"
      style={{ minWidth: 160 }}
      options={options.map((o) => ({ value: textAsValue ? o.text : o.id, label: o.text }))}
      {...rest}
    />
  )
}
