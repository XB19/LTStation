import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from './auth/AuthContext'
import MainLayout from './layout/MainLayout'
import { MENU } from './layout/menu'
import Login from './pages/Login'
import Activation from './pages/Activation'
import Dashboard from './pages/Dashboard'
import Pending from './pages/Pending'
import { PAGES } from './routes'

function Protected() {
  const { me, loading } = useAuth()
  if (loading) return <Spin fullscreen />
  if (!me) return <Navigate to="/login" replace />
  return <MainLayout />
}

const allPaths = Array.from(new Set(MENU.flatMap((s) => s.groups.flatMap((g) => g.items.map((i) => i.path)))))

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/activation" element={<Activation />} />
        <Route element={<Protected />}>
          <Route path="/" element={<Dashboard />} />
          {Object.entries(PAGES).map(([path, Page]) => (
            <Route key={path} path={path} element={<Page />} />
          ))}
          {allPaths
            .filter((p) => p !== '/' && !PAGES[p])
            .map((p) => (
              <Route key={p} path={p} element={<Pending />} />
            ))}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
