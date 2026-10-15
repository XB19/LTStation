import axios, { AxiosError } from 'axios'
import { message } from 'antd'

/** Client HTTP unique : cookies de session, messages d'erreur en français. */
export const http = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.response.use(
  (r) => r,
  (err: AxiosError<{ message?: string }>) => {
    const status = err.response?.status
    const msg = err.response?.data?.message ?? err.message
    if (status === 401) {
      // Le logiciel Station a sa propre page de connexion
      const login = window.location.pathname.startsWith('/station') ? '/station/login' : '/login'
      if (window.location.pathname !== login) {
        window.location.href = login
      }
    } else {
      message.error(msg)
    }
    return Promise.reject(err)
  },
)

export async function get<T>(url: string, params?: object): Promise<T> {
  return (await http.get<T>(url, { params })).data
}

export async function post<T>(url: string, body?: unknown, params?: object): Promise<T> {
  return (await http.post<T>(url, body, { params })).data
}

export async function put<T>(url: string, body?: unknown): Promise<T> {
  return (await http.put<T>(url, body)).data
}

export async function del<T>(url: string, params?: object): Promise<T> {
  return (await http.delete<T>(url, { params })).data
}

export interface PageResult<T> {
  rows: T[]
  total: number
}

export interface SelectItem {
  id: number | string
  text: string
}
