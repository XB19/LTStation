import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App as AntApp, ConfigProvider } from 'antd'
import frFR from 'antd/locale/fr_FR'
import dayjs from 'dayjs'
import 'dayjs/locale/fr'
import SwApp from './SwApp'
import './station.css'

dayjs.locale('fr')

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider
      locale={frFR}
      theme={{
        token: { colorPrimary: '#0f766e', borderRadius: 10, fontFamily: "'Inter', 'Segoe UI', system-ui, sans-serif" },
        components: { Button: { controlHeightLG: 48 }, Card: { borderRadiusLG: 14 } },
      }}
    >
      <AntApp>
        <SwApp />
      </AntApp>
    </ConfigProvider>
  </StrictMode>,
)
