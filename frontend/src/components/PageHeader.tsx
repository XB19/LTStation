import { Breadcrumb, Typography } from 'antd'
import type { ReactNode } from 'react'

/** Titre de page + fil d'Ariane, comme les "page-titles" de l'original. */
export default function PageHeader({ title, section, extra }: { title: string; section?: string; extra?: ReactNode }) {
  return (
    <div className="lt-page-title" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      <div>
        <Typography.Title level={4} style={{ margin: 0, color: '#1e88e5' }}>
          {title}
        </Typography.Title>
        <Breadcrumb items={[{ title: section ?? 'Accueil' }, { title }]} />
      </div>
      {extra}
    </div>
  )
}
