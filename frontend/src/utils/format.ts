import dayjs from 'dayjs'

export const fmtDateTime = (v?: unknown) => (v ? dayjs(v as string).format('YYYY-MM-DD HH:mm:ss') : '')
export const fmtDate = (v?: unknown) => (v ? dayjs(v as string).format('YYYY-MM-DD') : '')
export const fmtNum = (v?: unknown, digits = 2) =>
  v === null || v === undefined || v === '' ? '' : Number(v).toLocaleString('fr-FR', { minimumFractionDigits: digits, maximumFractionDigits: digits })
