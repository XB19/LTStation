import type { ReactNode } from 'react'
import { Modal } from 'antd'
import { tr, type Layout } from './WinForm'

/** Fenêtre modale à la taille de la fenêtre d'origine. */
export default function Win({ layout, title, open = true, onClose, children, width }: {
  layout?: Layout
  title?: string
  open?: boolean
  onClose: () => void
  children: ReactNode
  width?: number
}) {
  // Les libellés français sont plus longs que l'anglais d'origine : la fenêtre est un peu plus large
  const base = layout?.size[0] ?? 600
  const w = width ?? Math.min(Math.max(Math.round(base * (base < 800 ? 1.45 : 1.18)) + 40, 440), window.innerWidth - 40)
  return (
    <Modal className="wf-window" open={open} onCancel={onClose} footer={null} width={w} centered maskClosable={false} destroyOnClose
      title={title ?? (layout ? tr(layout.title) : '')}>
      {children}
    </Modal>
  )
}
