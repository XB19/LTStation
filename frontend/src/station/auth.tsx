import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { get, post } from '../api/http'

export interface Duty {
  id: number
  shift: string
  shiftName?: string
  openTime: string
  status: number
}

export interface StationMe {
  employeeId: number
  employeeName: string
  station: { stationId: number; code: string; name: string; address?: string; phone?: string }
  duty: Duty | null
  /** Droits du profil (1 à 24) ; null = tous les droits */
  rights: number[] | null
}

interface State {
  me: StationMe | null
  loading: boolean
  login: (login: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

const Ctx = createContext<State | null>(null)

/** Session de l'employé connecté au logiciel Station. */
export function StationAuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<StationMe | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      setMe(await get<StationMe>('/station/auth/me'))
    } catch {
      setMe(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (window.location.pathname.startsWith('/station/login')) setLoading(false)
    else refresh()
  }, [refresh])

  const login = async (l: string, password: string) => {
    setMe(await post<StationMe>('/station/auth/login', { login: l, password }))
  }
  const logout = async () => {
    await post('/station/auth/logout')
    setMe(null)
  }

  return <Ctx.Provider value={{ me, loading, login, logout, refresh }}>{children}</Ctx.Provider>
}

export function useStation() {
  const c = useContext(Ctx)
  if (!c) throw new Error('StationAuthProvider manquant')
  return c
}
