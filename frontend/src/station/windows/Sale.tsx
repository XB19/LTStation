import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Col, Descriptions, Input, InputNumber, Modal, Radio, Row, Segmented, message } from 'antd'
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
  return (
    <Modal className="wf-window" open title={`Vente — pistolet N°${String(nozzle.nozId)} · ${String(nozzle.gradeName ?? '')}`} onCancel={onClose} footer={null} width={560}
      centered maskClosable={false}>
      <Descriptions size="small" column={2} bordered style={{ marginBottom: 12 }}>
        <Descriptions.Item label="Énergie">{String(nozzle.gradeName ?? '')}</Descriptions.Item>
        <Descriptions.Item label="Prix">{money(nozzle.price, 3)}</Descriptions.Item>
      </Descriptions>
      <Row gutter={[12, 12]}>
        <Col span={24}>
          <Segmented block value={mode} onChange={(x) => setMode(x as 'amount' | 'volume')} options={[{ label: 'Montant', value: 'amount' }, { label: 'Litres', value: 'volume' }]} />
        </Col>
        <Col span={24}>
          <InputNumber autoFocus size="large" min={0.01} style={{ width: '100%', fontSize: 22 }} value={value} onChange={(x) => setValue(x)}
            placeholder={mode === 'amount' ? 'Montant servi' : 'Litres servis'} addonAfter={mode === 'amount' ? '' : 'L'} />
        </Col>
        <Col span={24}>
          <div style={{ marginBottom: 4 }}>Paiement</div>
          <Radio.Group optionType="button" buttonStyle="solid" value={payment} onChange={(e) => setPayment(e.target.value)}
            options={[{ value: 1, label: 'Espèces' }, { value: 7, label: 'Carte bancaire' }, { value: 8, label: 'Carte client' }, { value: 10, label: 'Virement' }]} />
        </Col>
        {payment === 8 && (
          <>
            <Col span={14}><Input placeholder="N° de carte" value={asn} onChange={(e) => setAsn(e.target.value)} maxLength={24} allowClear /></Col>
            <Col span={10}><Input.Password placeholder="Mot de passe de la carte" value={pin} maxLength={8} inputMode="numeric" onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))} /></Col>
          </>
        )}
        {card && (
          <Col span={24}>
            <Alert type={Number(card.status) === 0 ? 'info' : 'error'} showIcon
              message={`${String(card.holder)} · ${String(card.typeLabel)} · solde ${money(card.balance)}${Number(card.status) !== 0 ? ` · ${String(card.statusLabel)}` : ''}`}
              description={Number(card.discount) > 0 && Number(card.discount) < 100 ? `Remise de la carte : ${100 - Number(card.discount)} %` : undefined} />
          </Col>
        )}
        <Col span={24}><Input placeholder="Immatriculation (facultatif)" value={car} maxLength={16} onChange={(e) => setCar(e.target.value.toUpperCase())} /></Col>
        <Col span={24}>
          <Descriptions size="small" column={3} bordered>
            <Descriptions.Item label="Volume">{preview ? `${money(preview.volume)} L` : '—'}</Descriptions.Item>
            <Descriptions.Item label="Montant">{preview ? money(preview.amount) : '—'}</Descriptions.Item>
            <Descriptions.Item label="À payer"><b style={{ fontSize: 16 }}>{preview ? money(preview.toPay) : '—'}</b></Descriptions.Item>
          </Descriptions>
        </Col>
        <Col span={24} style={{ textAlign: 'right' }}>
          <Button onClick={onClose} style={{ marginRight: 8 }}>Annuler</Button>
          <Button type="primary" loading={saving} disabled={!preview} onClick={submit}>Valider la vente</Button>
        </Col>
        {card && <Col span={24} style={{ color: '#888', fontSize: 12 }}>Carte {formatAsn(String(card.asn))}</Col>}
      </Row>
    </Modal>
  )
}

