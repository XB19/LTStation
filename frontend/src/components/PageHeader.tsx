import { Breadcrumb, Typography } from 'antd'
import type { ReactNode } from 'react'

/** Titre de page + fil d'Ariane, comme les "page-titles" de l'original. */
export default function PageHeader({ title, section, extra }: { title: string; section?: string; extra?: ReactNode }) {
  return (
    <div className="lt-page-title">
      <div>
        <Typography.Title level={4} className="lt-page-title-text">
          {title}
        </Typography.Title>
        <Breadcrumb items={[{ title: section ?? 'Accueil' }, { title }]} />
      </div>
      {extra}
    </div>
  )
}
