import dayjs from 'dayjs'
import { get } from '../../api/http'
import { formatAsn, money, paymentLabel } from '../labels'
import { esc, printHtml, rows } from '../print'
import { PRINT_LINES } from './StationPrintForm'

/** Lignes d'en-tête du ticket (fenêtre « Impression »). */
async function header() {
  try {
    const s = await get<Record<string, string>>('/station/settings')
    return PRINT_LINES.map((k) => s[`print.${k}`]).filter(Boolean).map((l) => `<div class="c">${esc(l)}</div>`).join('')
  } catch {
    return ''
  }
}

/** Ticket au format PrintClass d'origine : titre puis lignes « libellé & valeur ». */
export async function printTicket(title: string, lines: [string, unknown][], stationName?: string) {
  const h = await header()
  printHtml(title, `${h || `<h1>${esc(stationName ?? '')}</h1>`}<hr><h1>${esc(title)}</h1>${rows(lines)}<hr><div class="c">Merci de votre visite</div>`)
}

/** Ticket d'une vente de carburant. */
export async function printSale(sale: Record<string, unknown>, stationName?: string) {
  const card = Number(sale.paymentId) === 8
  await printTicket('Vente de carburant', [
    ['Ticket n°', sale.ticketNo],
    ['Date', dayjs(sale.trueTime as string).format('YYYY-MM-DD HH:mm:ss')],
    ['Pistolet', sale.gunId || '—'],
    ['Énergie', sale.gradeName],
    ['Prix', money(sale.price, 3)],
    ['Volume (L)', money(sale.volume)],
    ['Montant', money(sale.amount)],
    ...(Number(sale.discount) > 0 ? [['Remise', money(sale.discount)] as [string, unknown]] : []),
    ['Payé', money(sale.truelyPay)],
    ['Paiement', paymentLabel(sale.paymentId)],
    ...(card ? [['N° de carte', formatAsn(sale.asn as string)], ['Porteur', sale.cardHolder], ['Solde', money(sale.balance)]] as [string, unknown][] : []),
    ...(sale.carNumber ? [['Immatriculation', sale.carNumber] as [string, unknown]] : []),
    ['Opérateur', sale.employeeName],
  ], stationName)
}
