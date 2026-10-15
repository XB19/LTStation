import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App as AntApp, ConfigProvider } from 'antd'
import frFR from 'antd/locale/fr_FR'
import dayjs from 'dayjs'
import 'dayjs/locale/fr'
import { AuthProvider } from './auth/AuthContext'
import App from './App'

dayjs.locale('fr')

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider locale={frFR} theme={{ token: { colorPrimary: '#1e88e5' } }}>
      <AntApp>
        <AuthProvider>
          <App />
        </AuthProvider>
      </AntApp>
    </ConfigProvider>
  </StrictMode>,
)
