import type { Option } from '../winforms/WinForm'

/** Listes fixes du logiciel d'origine (textes des fichiers .resx). */
export const CERTIFICATES: Option[] = ["Carte d'identité", 'Permis de conduire', 'Carte militaire', 'Passeport', 'Permis de travail', 'Autre']
  .map((label, i) => ({ value: i + 1, label }))
export const CAR_COLORS: Option[] = ['Bleu', 'Jaune', 'Jaune-vert', 'Vert', 'Noir', 'Inconnu', 'Compresseur'].map((label, i) => ({ value: i + 1, label }))
export const CAR_TYPES: Option[] = ['Taxi', 'Autocar', 'Minibus', 'Poids lourd', 'Voiture particulière', "Véhicule d'entreprise", 'Autre']
  .map((label, i) => ({ value: i + 1, label }))
export const LIMIT_TYPES: Option[] = ['Sans limite', 'Limite par région', 'Limite par ville', 'Limite par station'].map((label, i) => ({ value: i, label }))
export const GROUP_TYPES: Option[] = ['Carte de groupe personnelle', 'Carte de groupe de crédit', 'Carte principale de groupe'].map((label, i) => ({ value: i, label }))
export const EMP_CARD_TYPES: Option[] = [{ value: 0, label: 'Employé' }, { value: 1, label: 'Réparation' }]

export const num = (v: unknown) => (v === undefined || v === null || v === '' ? undefined : Number(v))

/** Prix spéciaux au format d'origine : « ,<code énergie><prix entier en hexadécimal sur 4> » pour chaque ligne renseignée. */
export function specialPrice(values: Record<string, unknown>, gradePrefix: string, pricePrefix: string) {
  let s = ''
  for (let i = 1; i <= 7; i++) {
    const code = values[`${gradePrefix}${i}`]
    const price = values[`${pricePrefix}${i}`]
    if (code && price !== undefined && price !== '' && !Number.isNaN(Number(price))) {
      s += ',' + String(code) + Math.trunc(Number(price)).toString(16).toUpperCase().padStart(4, '0')
    }
  }
  return s
}
