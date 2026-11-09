"""
Prépare le kit d'installation d'un poste client dans deploy/out/LTStation-Kit/ (jamais publié : il contient des clés).

  python deploy/build_kit.py

  LTStation-Kit/
      INSTALLER.bat      double-clic : installe tout (PostgreSQL, bases, licence, clés, application, réglages)
      ACCES.txt          identifiants créés pour ce kit
      LISEZ-MOI.txt      fiche
      fichiers/          PostgreSQL, application, scripts SQL, clés, installer.ps1, config.ps1

Bases : structure actuelle + tables de référence seulement (aucune station, carte, client ni opération).
Prérequis : installateur de l'application dans desktop/dist, installateur PostgreSQL dans deploy/vendor.
"""
import glob
import os
import secrets
import shutil
import string
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "database", "tools"))
from activation_code import center as center_licence  # noqa: E402
from migrate_data import load_env  # noqa: E402

# Tables de référence copiées telles quelles (déjà traduites en français)
CENTER_REF = [
    "account_role", "action_list", "alarm_type", "apps", "authority_mess", "card_status_types", "card_types", "certificate_types",
    "cylinder_types", "data_versions", "def_closing_status", "def_colors", "def_currencies", "def_location_status", "def_payments",
    "def_trashed_status", "def_unit_packages", "def_unit_weights", "def_units", "def_vendor_categories", "dispatcher", "genders",
    "groups", "media_types", "menu_list", "operator_types", "score_trans_types", "station_types", "transaction_types", "users",
    "vehicle_colors", "vehicle_types",
]
STATION_REF = [
    "alarm_type", "authority_mess", "certificate_types", "com_config", "data_versions", "dt_def", "duties_config", "genders", "group",
    "monitors", "operator", "out_config", "shift_config", "system_parm",
]
# Seul le groupe système « Particulier » est gardé
STATION_AFTER = 'DELETE FROM public."group" WHERE group_id <> 100000001;\n'
STATION_KEYS = ["CardConstKey", "CardConstTempKey", "MifareCardKey", "DHTransferKey", "FlyingAppKey"]
ADMIN_USER = "admin"

OUT = os.path.join(HERE, "out")
KIT = os.path.join(OUT, "LTStation-Kit")
FILES = os.path.join(KIT, "fichiers")


def tool(name):
    found = shutil.which(name) or sorted(glob.glob(rf"C:\Program Files\PostgreSQL\*\bin\{name}.exe"))
    return found if isinstance(found, str) else found[-1]


def dump(env, db, *args):
    e = dict(os.environ, PGPASSWORD=env["DB_PASSWORD"])
    r = subprocess.run([tool("pg_dump"), "-h", env["DB_HOST"], "-p", env["DB_PORT"], "-U", env["DB_USER"], "-d", db,
                        "--no-owner", "--no-privileges", *args], env=e, capture_output=True)
    if r.returncode:
        sys.exit(r.stderr.decode("utf-8", "replace"))
    return r.stdout.decode("utf-8")


def tables(names):
    return [a for n in names for a in ("-t", f'public."{n}"')]


def write(path, text, bom=False):
    open(path, "w", encoding="utf-8-sig" if bom else "utf-8", newline="\r\n").write(text.replace("\r\n", "\n"))


def build_sql(env, db, ref, after=""):
    schema = dump(env, db, "--schema-only")
    if "zz_" in schema:
        sys.exit(f"Table de test zz_ présente dans {db} : nettoyer avant de construire le kit")
    data = dump(env, db, "--data-only", "--column-inserts", *tables(ref))
    return schema + "\n-- Tables de référence\n" + data + after


def password(n=16):
    """Lettres et chiffres seulement : sans caractère que les scripts devraient échapper."""
    alphabet = string.ascii_letters + string.digits
    while True:
        p = "".join(secrets.choice(alphabet) for _ in range(n))
        if any(c.isdigit() for c in p) and any(c.isupper() for c in p) and any(c.islower() for c in p):
            return p


def main():
    env = load_env()
    station_db = env.get("STATION_DB_NAME") or "station"
    app = sorted(glob.glob(os.path.join(ROOT, "desktop", "dist", "*.exe")))
    pg = sorted(glob.glob(os.path.join(HERE, "vendor", "postgresql-*-windows-x64.exe")))
    if not app:
        sys.exit("Installateur de l'application absent : le générer (desktop, electron-builder)")
    if not pg:
        sys.exit("Installateur PostgreSQL absent de deploy/vendor")
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(FILES)

    shutil.copy2(app[-1], FILES)
    shutil.copy2(pg[-1], FILES)
    write(os.path.join(FILES, "center.sql"), build_sql(env, env["DB_NAME"], CENTER_REF))
    write(os.path.join(FILES, "station.sql"), build_sql(env, station_db, STATION_REF, STATION_AFTER))
    write(os.path.join(FILES, "cles-cartes.sql"),
          "DELETE FROM public.data;\n" + dump(env, env["DB_NAME"], "--data-only", "--column-inserts", *tables(["data"])))
    props = os.path.join(ROOT, "backend", "station", "StationSoftware.properties")
    lines = [l.rstrip("\r\n") for l in open(props, encoding="utf-8") if l.split("=", 1)[0].strip() in STATION_KEYS]
    if len(lines) != len(STATION_KEYS) or any(not l.split("=", 1)[1].strip() for l in lines):
        sys.exit("Clés de cartes incomplètes dans backend/station/StationSoftware.properties")
    write(os.path.join(FILES, "cles-station.properties"), "# Clés de cartes du logiciel Station : CONFIDENTIEL\n" + "\n".join(lines) + "\n")
    licence, until = center_licence()
    write(os.path.join(FILES, "licence.sql"), f"DELETE FROM public.active_web;\nINSERT INTO public.active_web (id, secret_info) VALUES (1, '{licence}');\n")

    pg_pw, admin_pw = password(), password()
    write(os.path.join(FILES, "config.ps1"), f"$PgPassword = '{pg_pw}'\n$AdminUser = '{ADMIN_USER}'\n$AdminPassword = '{admin_pw}'\n", bom=True)
    write(os.path.join(FILES, "installer.ps1"), open(os.path.join(HERE, "installer.ps1"), encoding="utf-8").read(), bom=True)
    write(os.path.join(KIT, "INSTALLER.bat"), open(os.path.join(HERE, "INSTALLER.bat"), encoding="utf-8").read())
    write(os.path.join(KIT, "LISEZ-MOI.txt"), open(os.path.join(HERE, "LISEZ-MOI.txt"), encoding="utf-8").read())
    write(os.path.join(KIT, "ACCES.txt"), f"""ACCÈS LTSTATION - CONFIDENTIEL
==============================
Kit préparé le {__import__('datetime').date.today():%d/%m/%Y}. Ces identifiants sont propres à ce kit.
Gardez ce fichier en lieu sûr ; ne l'envoyez pas par e-mail ni messagerie.

CENTER (plateforme de gestion, dans un navigateur)
  Adresse          : http://localhost:8080/
  Identifiant      : {ADMIN_USER}
  Mot de passe     : {admin_pw}
  Droits           : tous (compte System Manage). Sert à créer la station, les carburants, les employés.
  Licence          : déjà installée par INSTALLER.bat, valable jusqu'au {until:%d/%m/%Y}.

BASE DE DONNÉES (PostgreSQL, installé par INSTALLER.bat)
  Serveur          : localhost, port 5432
  Utilisateur      : postgres
  Mot de passe     : {pg_pw}
  Bases            : center et station

LOGICIEL STATION (icône LTStation Station du bureau)
  Premier lancement : assistant de création de la station. Le poste s'active tout seul à la fin
  de l'assistant (aucun code à demander).
  Identifiants      : ceux de l'employé que vous créez dans l'assistant (étape Employés).

CARDMAKE (menu Station > CardMake de l'application)
  Activation        : le code est déjà rempli ; saisir le nom de la station et l'identifiant /
                      mot de passe d'un employé ayant les droits de cartes.

CLÉS DE CARTES
  Installées par INSTALLER.bat (base Center et réglages du logiciel Station).
""")

    size = sum(os.path.getsize(os.path.join(d, f)) for d, _, fs in os.walk(KIT) for f in fs)
    print(f"Kit prêt : {KIT} ({size / 1e6:.0f} Mo)")
    for d, _, fs in os.walk(KIT):
        for f in sorted(fs):
            print("  ", os.path.relpath(os.path.join(d, f), KIT))


if __name__ == "__main__":
    main()
