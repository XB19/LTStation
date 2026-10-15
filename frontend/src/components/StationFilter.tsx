import { Space } from 'antd'
import RemoteSelect from './RemoteSelect'

export interface StationFilterValue {
  regionId?: number
  cityId?: number
  stationId?: number
}

interface Props {
  value?: StationFilterValue
  onChange?: (v: StationFilterValue) => void
}

/**
 * Sélection en cascade Région → Ville → Station limitée aux droits de
 * l'utilisateur (listes RegionAuth / CityAuth / StationAuth d'origine).
 */
export default function StationFilter({ value = {}, onChange = () => undefined }: Props) {
  return (
    <Space wrap>
      <RemoteSelect
        type="RegionAuth"
        placeholder="Région"
        value={value.regionId}
        onChange={(regionId) => onChange({ regionId })}
      />
      <RemoteSelect
        type="CityAuth"
        placeholder="Ville"
        params={{ regionId: value.regionId }}
        enabled={value.regionId != null}
        value={value.cityId}
        onChange={(cityId) => onChange({ regionId: value.regionId, cityId })}
      />
      <RemoteSelect
        type="StationAuth"
        placeholder="Station"
        params={{ cityId: value.cityId }}
        enabled={value.cityId != null}
        value={value.stationId}
        onChange={(stationId) => onChange({ ...value, stationId })}
      />
    </Space>
  )
}
