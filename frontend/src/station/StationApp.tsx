import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Spin } from 'antd'
import { useStation } from './auth'
import Login from './pages/Login'
import MainWindow from './MainWindow'

/** Logiciel Station : fenêtre principale « Station SoftWare » après connexion de l'employé. */
export default function StationApp() {
  const { me, loading } = useStation()
  if (loading) {
    return <div style={{ height: '100vh', display: 'grid', placeItems: 'center' }}><Spin size="large" /></div>
  }
  return (
    <BrowserRouter basename="/station">
      <Routes>
        <Route path="/login" element={me ? <Navigate to="/" replace /> : <Login />} />
        <Route path="/" element={me ? <MainWindow /> : <Navigate to="/login" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
