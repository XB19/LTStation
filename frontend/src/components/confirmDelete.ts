import { Modal, message } from 'antd'

/** Demande de confirmation avant suppression, puis exécute l'action. */
export function confirmDelete(action: () => Promise<unknown>, onDone?: () => void, content = 'Êtes-vous sûr de vouloir effectuer cette opération ?') {
  Modal.confirm({
    title: 'Confirmer la suppression',
    content,
    okText: 'Supprimer',
    okType: 'danger',
    cancelText: 'Annuler',
    onOk: async () => {
      await action()
      message.success('Opération réussie')
      onDone?.()
    },
  })
}
