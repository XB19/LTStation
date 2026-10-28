import { useEffect, useState } from 'react'
import { Button, Modal, Result, Spin } from 'antd'
import { get, post } from '../api/http'
import RemoteForm from './winforms/RemoteForm'
import { tr } from './winforms/WinForm'

/**
 * Logiciel Station (StationSoftware.exe) : démarrage comme Program.cs.
 *  - fin de validité de la station : message puis arrêt ;
 *  - licence absente : assistant StationCreate (mode IC) ou NormalRegister (mode Normal) ;
 *  - Auto = 0 : fenêtre Login, puis Station (mode IC) ou NormalMainForm (mode Normal) ;
 *  - Auto ≠ 0 : premier employé connecté automatiquement, puis NormalMainForm.
 * Les fenêtres ouvertes par Form.Show() s'affichent par-dessus la fenêtre principale.
 */

interface Boot {
  outOfTime: boolean
  registered: boolean
  systemType: number
  auto: number
  texts: Record<string, string>
}

type Stage = { kind: 'boot' } | { kind: 'form'; form: string; next: string | null } | { kind: 'main'; form: string } | { kind: 'exit'; text?: string }

interface Opened {
  key: number
  form: string
  args: Record<string, unknown>
  parent?: string
  /** Fenêtre déjà créée sur le serveur (Form.Show() d une instance) */
  attachId?: string
}

export default function SwApp() {
  const [stage, setStage] = useState<Stage>({ kind: 'boot' })
  const [boot, setBoot] = useState<Boot | null>(null)
  const [opened, setOpened] = useState<Opened[]>([])

  const mainForm = (b: Boot) => (b.systemType === 1 ? 'NormalForm.NormalMainForm' : 'Station')

  const start = async () => {
    setOpened([])
    setStage({ kind: 'boot' })
    try {
      const b = await get<Boot>('/sw/boot')
      setBoot(b)
      if (b.outOfTime) {
        Modal.error({ title: tr(b.texts?.OutOfTime ?? 'OutOfTime'), okText: 'OK' })
        setStage({ kind: 'exit', text: b.texts?.OutOfTime })
        return
      }
      if (!b.registered) {
        setStage({ kind: 'form', form: b.systemType === 1 ? 'NormalForm.NormalRegister' : 'StationCreate', next: mainForm(b) })
        return
      }
      if (b.auto === 0) {
        setStage({ kind: 'form', form: 'Login', next: mainForm(b) })
        return
      }
      await post('/sw/autologin')
      setStage({ kind: 'main', form: 'NormalForm.NormalMainForm' })
    } catch {
      setStage({ kind: 'exit', text: 'Le logiciel Station ne répond pas.' })
    }
  }

  useEffect(() => {
    start()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const open = (form: string, args: Record<string, unknown>, parent?: string, attachId?: string) =>
    setOpened((o) => [...o, { key: Date.now() + Math.random(), form, args, parent, attachId }])

  if (stage.kind === 'boot') {
    return <div style={{ height: '100vh', display: 'grid', placeItems: 'center' }}><Spin size="large" /></div>
  }
  if (stage.kind === 'exit') {
    return (
      <Result status="info" title="Station SoftWare" subTitle={stage.text ? tr(stage.text) : 'Le logiciel a été fermé.'}
        extra={<Button type="primary" onClick={start}>Relancer</Button>} />
    )
  }
  if (stage.kind === 'form') {
    return (
      <div className="sw-desktop">
        {/* key : une fenêtre neuve à chaque étape (sinon l'état de la page de connexion passerait à la fenêtre principale) */}
        <RemoteForm key={`form:${stage.form}`} form={stage.form} onOpen={open}
          onClose={(result) => (result === 'OK' && stage.next ? setStage({ kind: 'main', form: stage.next }) : setStage({ kind: 'exit' }))} />
        {opened.map((o) => (
          <RemoteForm key={o.key} form={o.form} args={o.args} parent={o.parent} attachId={o.attachId} onOpen={open}
            onClose={() => setOpened((x) => x.filter((y) => y.key !== o.key))} />
        ))}
      </div>
    )
  }
  return (
    <div className="sw-main">
      <RemoteForm key={`main:${stage.form}`} form={stage.form} framed={false} onOpen={open} onClose={() => setStage({ kind: 'exit' })} />
      {opened.map((o) => (
        <RemoteForm key={o.key} form={o.form} args={o.args} parent={o.parent} attachId={o.attachId} onOpen={open}
          onClose={() => setOpened((x) => x.filter((y) => y.key !== o.key))} />
      ))}
      {boot && null}
    </div>
  )
}
