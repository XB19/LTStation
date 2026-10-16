import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import dayjs from 'dayjs'
import 'dayjs/locale/fr'
import { ThemeProvider } from '../theme/ThemeProvider'
import { StationAuthProvider } from './auth'
import StationApp from './StationApp'
import './station.css'

dayjs.locale('fr')

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <StationAuthProvider>
        <StationApp />
      </StationAuthProvider>
    </ThemeProvider>
  </StrictMode>,
)
