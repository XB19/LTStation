import type { Me } from '../auth/AuthContext'

/**
 * Menu latéral : reprise fidèle de Views/Shared/_Layout.cshtml.
 * Chaque entrée porte le droit (menu_list.json "pc") qui la rend visible.
 */
export interface MenuLink {
  path: string
  label: string
  auth?: string
}

export interface MenuGroup {
  label: string
  icon: string
  /** Groupe affiché si l'utilisateur a au moins un de ces droits */
  anyOf?: string[]
  items: MenuLink[]
}

export interface MenuSection {
  label: string
  groups: MenuGroup[]
  visible: (me: Me, has: (id: string) => boolean) => boolean
}

const any = (has: (id: string) => boolean, ...ids: string[]) => ids.some(has)
const adminTypes = (me: Me) => ['1', '2', '99'].includes(me.loginType)

export const MENU: MenuSection[] = [
  {
    label: 'Configuration des appareils',
    visible: (me, has) => adminTypes(me) && any(has, '1', '2', '3', '4', '17'),
    groups: [
      {
        label: 'Paramètres de base',
        icon: 'home',
        items: [
          { path: '/main/account-manager', label: 'Gestion de la plateforme', auth: '17' },
          { path: '/main/station-setting', label: 'Configuration des stations', auth: '1' },
          { path: '/main/active-station', label: 'Activation de stations', auth: '35' },
          { path: '/main/price-configure', label: 'Configuration des prix', auth: '2' },
          { path: '/main/unknown-device', label: 'Nouveaux appareils', auth: '3' },
          { path: '/main/active-device', label: "Activation d'appareils", auth: '34' },
          { path: '/main/renew-order', label: "Recharges d'activation d'appareils", auth: '56' },
          { path: '/main/air-setting', label: 'Appareils cloud', auth: '4' },
        ],
      },
    ],
  },
  {
    label: 'Configuration système',
    visible: (me, has) => adminTypes(me) && any(has, '5', '6', '7', '8'),
    groups: [
      {
        label: 'Configuration système',
        icon: 'setting',
        items: [
          { path: '/main/employees', label: 'Configuration des employés', auth: '5' },
          { path: '/main/discount-score', label: 'Remises & points', auth: '7' },
          { path: '/main/special-price', label: 'Prix préférentiels', auth: '8' },
        ],
      },
    ],
  },
  {
    label: 'Informations',
    visible: (me, has) => adminTypes(me) && any(has, '9', '10', '11', '12', '13', '19', '26', '36', '14', '15', '16', '18'),
    groups: [
      {
        label: 'Consultation des enregistrements',
        icon: 'file-search',
        anyOf: ['9', '10', '11', '12', '13', '19', '26', '36'],
        items: [
          { path: '/main/transactions', label: 'Transactions des stations', auth: '9' },
          { path: '/main/charge-list', label: 'Recharges', auth: '10' },
          { path: '/main/log-search', label: 'Historique des utilisateurs', auth: '11' },
          { path: '/main/customers', label: 'Liste des utilisateurs', auth: '12' },
          { path: '/main/tank-message', label: 'Informations des cuves', auth: '13' },
          { path: '/main/station-tank-message', label: 'Cuves des stations', auth: '26' },
          { path: '/main/tank-alarms', label: "Informations d'alarme", auth: '19' },
        ],
      },
      {
        label: 'Recherche de rapports',
        icon: 'bar-chart',
        anyOf: ['14'],
        items: [{ path: '/main/station-report', label: 'Rapports des stations', auth: '14' }],
      },
      {
        label: 'Statistiques',
        icon: 'pie-chart',
        anyOf: ['15'],
        items: [{ path: '/main/card-summary', label: 'Statistiques des cartes', auth: '15' }],
      },
      {
        label: 'Opérations sur cartes',
        icon: 'credit-card',
        anyOf: ['16'],
        items: [
          { path: '/main/deposit-online', label: 'Dépôt', auth: '16' },
          { path: '/main/mcard-translate', label: 'Attribution de cartes', auth: '16' },
          { path: '/main/mcard-update', label: 'Modification de cartes', auth: '16' },
        ],
      },
      {
        label: "Recherche d'appareils",
        icon: 'search',
        anyOf: ['18'],
        items: [{ path: '/main/device-search', label: "Recherche d'appareils", auth: '18' }],
      },
      {
        label: 'Sécurité des appareils',
        icon: 'safety',
        anyOf: ['19'],
        items: [{ path: '/main/device-safe', label: 'Surveillance de sécurité des stations', auth: '19' }],
      },
    ],
  },
  {
    label: 'Audit de certification',
    visible: (me, has) => adminTypes(me) && any(has, '20', '21'),
    groups: [
      {
        label: 'Audit de certification',
        icon: 'audit',
        items: [{ path: '/main/order-submit', label: 'Validation des commandes' }],
      },
    ],
  },
  {
    label: 'Factures',
    visible: (me, has) => adminTypes(me) && has('22'),
    groups: [{ label: 'Factures', icon: 'file-text', items: [{ path: '/main/bill-order', label: 'Factures' }] }],
  },
  {
    label: 'Surveillance des appareils',
    visible: (me, has) => adminTypes(me) && any(has, '23', '24'),
    groups: [
      {
        label: 'Surveillance des appareils',
        icon: 'video-camera',
        items: [
          { path: '/main/monitor-device-list', label: 'Liste des appareils surveillés', auth: '23' },
          { path: '/main/monitor-device', label: 'Surveillance des appareils', auth: '24' },
        ],
      },
    ],
  },
  {
    label: 'Opérations sur cartes',
    visible: (me, has) => adminTypes(me) && has('25'),
    groups: [
      {
        label: 'Opérations sur cartes',
        icon: 'credit-card',
        items: [{ path: '/main/card-update-server', label: 'Modification de carte' }],
      },
    ],
  },
  {
    label: 'Autres',
    visible: (me, has) => adminTypes(me) && any(has, '37', '38'),
    groups: [
      {
        label: 'Autres',
        icon: 'appstore',
        items: [{ path: '/main/advert', label: 'Publicités', auth: '38' }],
      },
    ],
  },
  {
    label: 'Flottes',
    visible: (me, has) => adminTypes(me) && any(has, '42', '43', '50', '51', '52'),
    groups: [
      {
        label: 'Flottes',
        icon: 'car',
        items: [
          { path: '/main/fleet-list', label: 'Liste des flottes', auth: '42' },
          { path: '/main/fleet-recharge', label: 'Factures des flottes', auth: '43' },
          { path: '/main/fleet-driver', label: 'Chauffeurs des flottes', auth: '50' },
          { path: '/main/fleet-distribute', label: 'Répartitions des flottes', auth: '51' },
          { path: '/main/fleet-card-record', label: 'Consommations des cartes flotte', auth: '52' },
        ],
      },
    ],
  },
  {
    label: 'Achats',
    visible: (me, has) => adminTypes(me) && any(has, '40', '41'),
    groups: [
      {
        label: 'Achats',
        icon: 'shopping',
        items: [
          { path: '/main/purchase-oil-price', label: 'Liste des prix carburant', auth: '41' },
          { path: '/main/purchase-order', label: "Commandes d'achat", auth: '40' },
        ],
      },
    ],
  },
  {
    label: 'Paiement par QR code',
    visible: (me, has) => adminTypes(me) && has('54'),
    groups: [
      {
        label: 'Paiement par QR code',
        icon: 'qrcode',
        items: [
          { path: '/main/scan-pay-records', label: 'Transactions par QR code', auth: '54' },
          { path: '/main/scan-pay-barcode', label: 'Génération de QR code de paiement', auth: '55' },
        ],
      },
    ],
  },
  {
    label: 'Communication sans fil',
    visible: (me) => me.loginType === '2',
    groups: [
      {
        label: 'Communication sans fil',
        icon: 'wifi',
        items: [
          { path: '/', label: 'Tableau de bord' },
          { path: '/main/tcp-device-show', label: 'Données en temps réel' },
          { path: '/main/normal-transaction', label: 'Transactions' },
          { path: '/main/tcp-platform', label: 'Paramètres de la plateforme' },
          { path: '/main/box-configure', label: 'Paramètres de base' },
          { path: '/main/tcp-device-bind', label: 'Liaison des appareils' },
        ],
      },
    ],
  },
  {
    label: 'GPRS',
    visible: (me) => me.loginType === '0' || me.loginType === '99',
    groups: [
      {
        label: 'Clients',
        icon: 'team',
        items: [
          { path: '/gprs/iccid-len', label: 'Traitement des zones spéciales', auth: 'role0' },
          { path: '/gprs/customers', label: 'Liste des clients', auth: 'role01' },
        ],
      },
      {
        label: 'Paramètres de base',
        icon: 'setting',
        items: [
          { path: '/gprs/stations', label: 'Configuration des stations' },
          { path: '/gprs/nozzles', label: 'Configuration des pistolets' },
          { path: '/gprs/tanks', label: 'Configuration des cuves' },
        ],
      },
      {
        label: 'Recherche',
        icon: 'search',
        items: [
          { path: '/gprs/nozzle-search', label: 'État des pistolets' },
          { path: '/gprs/transactions', label: 'Transactions' },
          { path: '/gprs/reports', label: 'Rapports' },
        ],
      },
    ],
  },
]

/** Une entrée est visible si elle n'exige aucun droit ou si l'utilisateur l'a. */
export function linkVisible(me: Me, link: MenuLink, has: (id: string) => boolean): boolean {
  if (!link.auth) return true
  if (link.auth === 'role0') return me.roleId === 0
  if (link.auth === 'role01') return me.roleId <= 1
  return has(link.auth)
}
