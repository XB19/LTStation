import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Input, InputNumber, Modal, Radio, Segmented, message } from 'antd'
import { post } from '../../api/http'
import { useStation } from '../auth'
import { formatAsn, money } from '../labels'
import { printSale } from './printing'

type R = Record<string, unknown>

interface Preview {
  price: number
  volume: number
  amount: number
  toPay: number
  card?: R
}

/** Montants et volumes les plus courants, pour une saisie en un clic. */
const PRESETS = { amount: [1000, 2000, 5000, 10000, 20000], volume: [5, 10, 20, 30, 50] }

const PAYMENT_OPTIONS = [
  { value: 1, label: 'Espèces' },
  { value: 7, label: 'Carte bancaire' },
  { value: 8, label: 'Carte client' },
  { value: 10, label: 'Virement' },
]

/**
 * Saisie d'une vente sur un pistolet (remplace la remontée automatique de la pompe) :
 * montant ou litres, moyen de paiement, carte client (n° + mot de passe), immatriculation.
 */
export default function Sale({ nozzle, onClose }: { nozzle: R; onClose: () => void }) {
  const { me } = useStation()
  const [mode, setMode] = useState<'amount' | 'volume'>('amount')
  const [value, setValue] = useState<number | null>(null)
  const [payment, setPayment] = useState(1)
  const [asn, setAsn] = useState('')
  const [pin, setPin] = useState('')
  const [car, setCar] = useState('')
  const [preview, setPreview] = useState<Preview | null>(null)
  const [saving, setSaving] = useState(false)
  const valueRef = useRef<HTMLInputElement>(null)

  const body = useMemo(() => ({
    nozId: nozzle.nozId,
    amount: mode === 'amount' ? value : undefined,
    volume: mode === 'volume' ? value : undefined,
    paymentId: payment,
    asn: payment === 8 ? asn.replace(/\s/g, '') : undefined,
    carNumber: car || undefined,
  }), [nozzle.nozId, mode, value, payment, asn, car])

  useEffect(() => {
    if (!value || value <= 0 || (payment === 8 && asn.replace(/\s/g, '').length < 16)) {
      setPreview(null)
      return
    }
    const t = setTimeout(() => post<Preview>('/station/sales/preview', body).then(setPreview).catch(() => setPreview(null)), 300)
    return () => clearTimeout(t)
  }, [body, value, payment, asn])

  const submit = async () => {
    if (!preview || saving) return
    if (payment === 8 && !/^\d{4,8}$/.test(pin)) return message.error('Le porteur doit saisir le mot de passe de sa carte')
    setSaving(true)
    try {
      const sale = await post<R>('/station/sales', { ...body, pin: payment === 8 ? pin : undefined })
      Modal.confirm({
        title: 'Vente enregistrée',
        content: `${money(sale.volume)} L · ${money(sale.truelyPay)}${payment === 8 ? ` · nouveau solde ${money(sale.balance)}` : ''}`,
        okText: 'Imprimer le ticket', cancelText: 'Fermer',
        onOk: () => printSale(sale, me?.station.name),
      })
      onClose()
    } finally {
      setSaving(false)
    }
  }

  const card = preview?.card
  const unit = mode === 'amount' ? '' : ' L'
  return (
    <Modal className="wf-window sale-window" open title={`Vente — pistolet N°${String(nozzle.nozId)} · ${String(nozzle.gradeName ?? '')}`} onCancel={onClose}
      footer={null} width={580} centered maskClosable={false}
      afterOpenChange={(o) => o && valueRef.current?.focus()}>
      <div className="sale-strip">
        <span className="sale-fuel">{String(nozzle.gradeName ?? '')}</span>
        <span>Prix : <b>{money(nozzle.price, 3)}</b></span>
      </div>

      <div className="sale-section">
        <Segmented block value={mode} onChange={(x) => { setMode(x as 'amount' | 'volume'); setValue(null); valueRef.current?.focus() }}
          options={[{ label: 'Montant', value: 'amount' }, { label: 'Litres', value: 'volume' }]} />
        <InputNumber ref={valueRef} size="large" min={0.01} className="sale-value" value={value} onChange={(x) => setValue(x)}
          onPressEnter={submit} placeholder={mode === 'amount' ? 'Montant servi' : 'Litres servis'} addonAfter={mode === 'amount' ? undefined : 'L'} />
        <div className="sale-presets">
          {PRESETS[mode].map((p) => (
            <Button key={p} onClick={() => { setValue(p); valueRef.current?.focus() }} type={value === p ? 'primary' : 'default'}>{p.toLocaleString('fr-FR')}{unit}</Button>
          ))}
        </div>
      </div>

      <div className="sale-section">
        <div className="sale-label">Paiement</div>
        <Radio.Group className="sale-payments" optionType="button" buttonStyle="solid" value={payment}
          onChange={(e) => setPayment(e.target.value)} options={PAYMENT_OPTIONS} />
        {payment === 8 && (
          <div className="sale-card">
            <Input placeholder="N° de carte" value={asn} onChange={(e) => setAsn(e.target.value)} maxLength={24} allowClear />
            <Input.Password placeholder="Mot de passe de la carte" value={pin} maxLength={8} inputMode="numeric"
              onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))} onPressEnter={submit} />
          </div>
        )}
        {card && (
          <Alert type={Number(card.status) === 0 ? 'info' : 'error'} showIcon style={{ marginTop: 8 }}
            message={`${String(card.holder)} · ${String(card.typeLabel)} · solde ${money(card.balance)}${Number(card.status) !== 0 ? ` · ${String(card.statusLabel)}` : ''}`}
            description={<>
              {Number(card.discount) > 0 && Number(card.discount) < 100 && <div>Remise de la carte : {100 - Number(card.discount)} %</div>}
              <div className="sale-muted">Carte {formatAsn(String(card.asn))}</div>
            </>} />
        )}
      </div>

      <div className="sale-section">
        <Input placeholder="Immatriculation (facultatif)" value={car} maxLength={16} onChange={(e) => setCar(e.target.value.toUpperCase())} onPressEnter={submit} />
      </div>

      <div className="sale-total">
        <div>
          <div className="sale-label">À payer</div>
          <div className="sale-topay">{preview ? money(preview.toPay) : '—'}</div>
        </div>
        <dl>
          <dt>Volume</dt><dd>{preview ? `${money(preview.volume)} L` : '—'}</dd>
          <dt>Montant</dt><dd>{preview ? money(preview.amount) : '—'}</dd>
        </dl>
      </div>

      <div className="sale-actions">
        <span className="sale-muted">Entrée pour valider · Échap pour annuler</span>
        <Button onClick={onClose}>Annuler</Button>
        <Button type="primary" size="large" loading={saving} disabled={!preview} onClick={submit}>Valider la vente</Button>
      </div>
    </Modal>
  )
}
