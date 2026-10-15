/** Moyens de paiement (def_payments) utilisés par le logiciel Station. */
export const PAYMENTS: Record<number, string> = {
  1: 'Espèces',
  2: 'R.C',
  3: 'Bon principal',
  4: 'Bon secondaire',
  5: 'Bon de crédit',
  6: 'Bon d’espèces',
  7: 'Carte bancaire',
  8: 'Carte client',
  9: 'Carte de crédit',
  10: 'Virement',
}

export const paymentLabel = (id: unknown) => PAYMENTS[Number(id)] ?? `Paiement ${String(id)}`

/** Types d'opérations du journal des comptes (transaction_types). */
export const CARD_OPS: Record<number, string> = {
  1: 'Ouverture',
  2: 'Recharge',
  3: 'Perte déclarée',
  4: 'Nouveau code PIN',
  5: 'Déblocage',
  6: 'Modification',
  7: 'Changement de carte',
  8: 'Remplacement',
  9: 'Clôture',
  10: 'Virement',
  11: 'Paiement',
  12: 'Annulation',
  13: 'Achat boutique',
  14: 'Remboursement de crédit',
  16: 'Virement de groupe',
}

export const cardOpLabel = (id: unknown) => CARD_OPS[Number(id)] ?? `Opération ${String(id)}`

export const STATUS_COLORS: Record<number, string> = { 0: 'green', 1: 'red', 2: 'red', 3: 'default', 5: 'default', 8: 'orange', 9: 'orange', 10: 'orange' }

export const money = (v: unknown, digits = 2) =>
  v === null || v === undefined || v === '' ? '—' : Number(v).toLocaleString('fr-FR', { minimumFractionDigits: digits, maximumFractionDigits: digits })

export const formatAsn = (asn?: string) => (asn ? asn.replace(/(.{4})/g, '$1 ').trim() : '')
