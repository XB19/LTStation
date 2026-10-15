import { useState } from 'react'
import { Input, Modal } from 'antd'

/** Saisie du mot de passe de la carte par le porteur (remplace la saisie sur le lecteur, PWDInput d'origine). */
export function askPin(title = 'Mot de passe de la carte'): Promise<string | null> {
  return new Promise((resolve) => {
    let value = ''
    const m = Modal.confirm({
      title,
      icon: null,
      content: <PinField onChange={(v) => { value = v }} onEnter={() => { m.destroy(); resolve(value) }} />,
      okText: 'Valider',
      cancelText: 'Annuler',
      onOk: () => resolve(value),
      onCancel: () => resolve(null),
    })
  })
}

function PinField({ onChange, onEnter }: { onChange: (v: string) => void; onEnter: () => void }) {
  const [v, setV] = useState('')
  return (
    <Input.Password autoFocus maxLength={8} inputMode="numeric" placeholder="Le porteur saisit son mot de passe" value={v}
      onChange={(e) => { const x = e.target.value.replace(/\D/g, ''); setV(x); onChange(x) }} onPressEnter={onEnter} />
  )
}
