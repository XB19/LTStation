# LTStation — React / Spring Boot / PostgreSQL

Réécriture de la plateforme **Center** (ASP.NET MVC + SQL Server d'origine) et des logiciels **Station** et **CardMake**, avec :

- **frontend/** : React 18, TypeScript, Vite, Ant Design (interface en français) ; deux applications :
  - le Center (`/`) ;
  - le logiciel Station (`/station/`) ;
- **backend/** : Spring Boot 3 (Java 17), JDBC/JPA ; un seul serveur pour les deux applications ;
- **desktop/** : application de bureau Windows (Electron) qui ouvre le logiciel Station dans sa propre fenêtre ;
- **database/** : schéma PostgreSQL généré depuis la base SQL Server et outils de migration.

## Prérequis

- Java 17 (JDK) ;
- Node.js 18 ou plus récent ;
- PostgreSQL 14 ou plus récent ;
- Python 3 avec `psycopg` (et `pyodbc` pour migrer depuis SQL Server).

## Configuration

Copier `.env.example` en `.env` à la racine du projet (`LTStation-Center/`), puis le remplir.

Ce fichier contient des secrets : il est exclu de Git et n'est lu que localement par le backend et les scripts.

| Variable | Rôle |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Connexion PostgreSQL |
| `LICENSE_ENABLED` | Vérification de la licence (table `active_web`). Mettre `false` en développement. |
| `SUPERADMIN_USER`, `SUPERADMIN_PASSWORD` | Compte administrateur avec tous les droits. Il remplace les accès codés en dur de l'original. Laisser vide pour le désactiver. |
| `WECHAT_*`, `PUBLIC_URL` | Compte public / mini-programme / paiement WeChat |
| `EZVIZ_APP_KEY`, `EZVIZ_APP_SECRET` | Vidéosurveillance EZVIZ |

Les variables facultatives et leurs valeurs par défaut sont dans `backend/src/main/resources/application.yml` :
- `SERVER_PORT` (8080) ;
- `UPLOAD_DIR` (`./uploads`) ;
- `CORS_ORIGINS` ;
- `LEASE_MODE`.

## Base de données

```bash
cd database/tools
python apply_schema.py            # crée la base et applique le schéma (tables, compléments, vues)
python migrate_data.py Center     # copie les données depuis la base SQL Server « Center » (facultatif)
python seed_demo.py               # données de démonstration (facultatif ; --remove pour les retirer)
```

- `apply_schema.py --reset` supprime et recrée la base.
- `migrate_data.py` vide toutes les tables avant de recopier les données.

Les données de démonstration utilisent :
- la station « Station Démo Lomé » ;
- l'employé `demo` / `demo1234`.

## Développement

```bash
./dev-backend.sh                  # backend sur http://localhost:8080 (arrête l'instance déjà lancée)
cd frontend && npm install && npm run dev   # interface sur http://localhost:5173
```

Le serveur Vite redirige `/api` et `/img` vers le backend.

## Production

```bash
cd frontend && npm run build      # compile l'interface dans backend/src/main/resources/static
cd ../backend && ./mvnw package -DskipTests
java -jar target/*.jar            # un seul processus sert l'API et l'interface
```

Le fichier `.env` doit se trouver à côté du jar ou dans le dossier parent.

## Logiciel Station (caisse)

Le logiciel Station reproduit l'application d'origine **Station SoftWare**, logiciel CardMake inclus. On y retrouve :
- la même fenêtre principale ;
- les mêmes menus et la même barre d'outils ;
- les mêmes fenêtres, avec les mêmes champs.

La disposition de chaque fenêtre est générée depuis les fichiers d'origine (`.resx` / `.cs`) par `database/tools/station/genlayout.py`. Le résultat se trouve dans `frontend/src/station/winforms/layouts/` et est dessiné par `WinForm.tsx`, en français.

Il fonctionne **sans aucun équipement** :
- les fonctions liées au matériel sont retirées : pompes, boîtiers, lecteur de cartes, jaugeurs, PSAM, clé secrète, menu Communication ;
- une vente se saisit en cliquant sur la tuile d'un pistolet ;
- une carte se lit en saisissant son numéro (bouton « Lire la carte ») ;
- le mot de passe de la carte (code PIN) est demandé pour payer ou débiter.

Il utilise le même serveur et la même base que le Center : tout ce qui est saisi en station est visible immédiatement dans le Center.

**Accès**
- Dans un navigateur : `http://<serveur>:8080/station/`.
- Avec l'application de bureau (voir ci-dessous).
- On se connecte avec l'identifiant et le mot de passe de la fiche employé, par exemple `demo` / `demo1234` pour la station de démonstration.

**Fenêtre principale**
- Menus : Paramètres système, Gestion de la station, Extensions, Utilisateurs, À propos.
- Barre d'outils : Prix, Impression, Création de carte, Mise à jour carte, Carte perdue, Remplacement, Solde, Transactions, Employés, Rapport de poste, Rapport journalier, Rapport mensuel, Journal des cartes, Points, Remises, Activité, Code PIN oublié, Droits, Annuler la clôture.
- Panneau de gauche : transactions des postes non clôturés, et totaux par employé.
- Onglets « Pistolets » (tuiles) et « Tableau des pistolets ».
- Barre d'état : poste en cours et employé connecté.

**Règles reprises de l'original**
- **Postes** : ils suivent les horaires configurés, avec un numéro AAAAMMJJNN. La clôture (« Rapport de poste ») écrit `duty_gun_info` et `duty_brand_sales` ; elle peut être annulée.
- **Index des pistolets** : chaque pistolet a un index cumulé (`volume_total`). Les rapports donnent l'index de début, l'index de fin et l'écart.
- **Profils de droits** : 24 droits, chacun correspondant à une fenêtre ou à un menu. Un employé sans profil a tous les droits.
- **Rapports** journalier et mensuel, avec « Clôture » (tables `daily_*` et `monthly_*`).

**Avant la première vente**
1. Prix : menu « Prix ».
2. Pistolets : Paramètres système > Configuration des pistolets.
3. Postes : Paramètres système > Gestion des postes.

Les cartes déjà émises doivent recevoir un code PIN (« Code PIN oublié » ou « Mise à jour carte ») avant de pouvoir payer.

### Application de bureau

```bash
cd desktop
npm install
npm start            # ouvre le logiciel Station (serveur par défaut : http://localhost:8080)
npm run dist         # crée l'installateur Windows dans desktop/dist/
```

L'adresse du serveur se change dans le menu **Station > Adresse du serveur…**. Elle est enregistrée sur le PC de la station.

## Contenu porté

Toutes les entrées du menu web d'origine ont leur page.

**Comptes employé / administrateur**
- Plateforme, régions, villes et stations, activation, prix ;
- Appareils et cuves (sans fil et IC) ;
- Employés, remises, prix spéciaux ;
- Transactions, recharges, journaux, clients ;
- Rapports, synthèse des cartes, cartes M1 ;
- Factures, vidéosurveillance, publicité ;
- Flottes, achats ;
- Paiement par QR code.

**Comptes groupe** (communication sans fil)
- Paramètres de la plateforme ;
- Stations à boîtier, énergies, prix, cuves ;
- Liaison des boîtiers ;
- Données en temps réel et commandes à distance ;
- Transactions.

**Clients GPRS**
- Clients et zones ICCID ;
- Stations, énergies, pistolets, cuves avec barème CSV ;
- État et surveillance des pistolets ;
- Transactions et rapports.

Les commandes destinées aux équipements sont écrites dans les mêmes tables et au même format que l'original :
- `cmd_list` : A1 à A4 ;
- `gprscmd` : types 1 à 5.

## Différences voulues avec l'original

- **Accès codés en dur supprimés** (mots de passe « OITY », « BlueSky »…). Ils sont remplacés par le super-administrateur défini dans `.env`.
- **Contrôle de périmètre** : chaque API vérifie que les données demandées appartiennent bien au compte connecté.
- **Cartes Baidu remplacées par OpenStreetMap** (Leaflet).
- **Corrections de bugs de l'original** :
  - alerte d'eau ;
  - date d'expiration ;
  - date des statistiques GPRS ;
  - trimestres ;
  - enregistrement des cuves GPRS ;
  - rôle des clients GPRS ;
  - surveillance GPRS limitée au compte.
- **Menu « fournisseur de produits chimiques » (27–33) non porté** : il n'a pas de pages dans l'original.

## Reste à faire

- **API `/ServerDeal/*`** : n'est plus nécessaire pour le nouveau logiciel Station. Elle ne servirait qu'à brancher les anciens logiciels Station et CardMake.
- **Logiciel Station** : les menus qui, dans l'original, n'ont pas de fenêtre décrite dans les ressources n'ont pas été refaits :
  - gestion des véhicules (Car User Manager) ;
  - contrôle des véhicules (Car Check).
- **Interfaces mobiles et WeChat** :
  - vues App et Phone ;
  - mini-programme ;
  - JoyFill ;
  - wxJoyFuelingOfficial.
- **Notifications de paiement WeChat.**
