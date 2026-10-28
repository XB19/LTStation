import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import dayjs from 'dayjs'
import 'dayjs/locale/fr'
import { ThemeProvider } from '../theme/ThemeProvider'
import SwApp from './SwApp'
import './station.css'

dayjs.locale('fr')

// Thème partagé avec le Center (variables --lt-* des fenêtres) ; les fenêtres principales gardent les couleurs d'origine
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <SwApp />
    </ThemeProvider>
  </StrictMode>,
)
