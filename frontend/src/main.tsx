import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import dayjs from 'dayjs'
import 'dayjs/locale/fr'
import { ThemeProvider } from './theme/ThemeProvider'
import { AuthProvider } from './auth/AuthContext'
import App from './App'

dayjs.locale('fr')

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <AuthProvider>
        <App />
      </AuthProvider>
    </ThemeProvider>
  </StrictMode>,
)
