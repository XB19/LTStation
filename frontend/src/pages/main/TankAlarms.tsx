import { useCallback } from 'react'
import { Card, Tag } from 'antd'
import { get } from '../../api/http'
import CrudTable from '../../components/CrudTable'
import PageHeader from '../../components/PageHeader'
import { fmtDateTime } from '../../utils/format'

type Row = Record<string, unknown>

/** Informations d'alarme des cuves (Main/TankAlarmShow) ; variante « sécurité des stations » (Main/DeviceSafe). */
export default function TankAlarms({ safety = false }: { safety?: boolean }) {
  const load = useCallback(() => get<Row[]>('/tanks/alarms'), [])
  return (
    <>
      <PageHeader title={safety ? 'Surveillance de sécurité des stations' : "Informations d'alarme"}
        section={safety ? 'Sécurité des appareils' : 'Consultation des enregistrements'} />
      <Card>
        <CrudTable<Row>
          rowKey="id"
          load={load}
          pageSize={15}
          columns={[
            { title: 'Station', dataIndex: 'stationName' },
            { title: "Type d'énergie", dataIndex: 'oilName' },
            { title: 'Cuve', dataIndex: 'tankName' },
            {
              title: "Type d'alarme", dataIndex: 'alarmType',
              render: (v: string) => <Tag color={/normale|résorbée/.test(v) ? 'green' : 'red'}>{v}</Tag>,
            },
            { title: 'Heure du signalement', dataIndex: 'updateTime', render: fmtDateTime },
          ]}
        />
      </Card>
    </>
  )
}

export function DeviceSafe() {
  return <TankAlarms safety />
}
