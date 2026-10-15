import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { get, post } from '../api/http'

export interface MenuEntry {
  label: string
  value: number
}

export interface Me {
  userId: number
  roleId: number
  loginName: string
  /** "1" employé/administrateur, "2" compte groupe, "99" client GPRS */
  loginType: string
  companyGroup: string
  stationId: number | null
  stationName: string | null
  employeeName: string | null
  auth: string[]
  gzhAuth: string
  isCenter: boolean
  gprs: string
  title: string
  pcMenus: MenuEntry[]
  phoneMenus: MenuEntry[]
}

interface AuthState {
  me: Me | null
  loading: boolean
  login: (user: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<void>
  can: (...ids: (string | number)[]) => boolean
}

const Ctx = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      setMe(await get<Me>('/auth/me'))
    } catch {
      setMe(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (window.location.pathname.startsWith('/login') || window.location.pathname.startsWith('/activation')) {
      setLoading(false)
    } else {
      refresh()
    }
  }, [refresh])

  const login = async (user: string, password: string) => {
    setMe(await post<Me>('/auth/login', { user, password }))
  }

  const logout = async () => {
    await post('/auth/logout')
    setMe(null)
  }

  const can = (...ids: (string | number)[]) => !!me && ids.some((id) => me.auth.includes(String(id)))

  return <Ctx.Provider value={{ me, loading, login, logout, refresh, can }}>{children}</Ctx.Provider>
}

export function useAuth(): AuthState {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAuth hors AuthProvider')
  return v
}
