"""
Prépare le kit d'installation pour un poste client, dans deploy/out/ (jamais publié).

  python deploy/build_kit.py

  deploy/out/LTStation-Kit/          à transmettre (clé USB, lien de partage)
      1-Installateur/                LTStation Station Setup.exe
      2-Base/                        center.sql, station.sql, installer-base.bat
      LISEZ-MOI.txt
  deploy/out/PRIVE-remettre-en-main-propre/
      cles-cartes.sql, cles-station.properties, installer-cles.bat, installer-cles.ps1, licence-center.txt

Les bases du kit : structure actuelle + tables de référence seulement (aucune station, carte, client ni opération).
"""
import glob
import os
import shutil
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

OUT = os.path.join(HERE, "out")
KIT = os.path.join(OUT, "LTStation-Kit")
PRIVATE = os.path.join(OUT, "PRIVE-remettre-en-main-propre")


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


def crlf(src, dst):
    text = open(src, encoding="utf-8").read().replace("\r\n", "\n")
    open(dst, "w", encoding="utf-8", newline="\r\n").write(text)


def build_sql(env, db, ref, after=""):
    schema = dump(env, db, "--schema-only")
    if "zz_" in schema:
        sys.exit(f"Table de test zz_ présente dans {db} : nettoyer avant de construire le kit")
    data = dump(env, db, "--data-only", "--column-inserts", *tables(ref))
    return schema + "\n-- Tables de référence\n" + data + after


def main():
    env = load_env()
    station_db = env.get("STATION_DB_NAME") or "station"
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(os.path.join(KIT, "1-Installateur"))
    os.makedirs(os.path.join(KIT, "2-Base"))
    os.makedirs(PRIVATE)

    exe = sorted(glob.glob(os.path.join(ROOT, "desktop", "dist", "*.exe")))
    if not exe:
        sys.exit("Installateur absent : le générer d'abord (desktop, electron-builder)")
    shutil.copy2(exe[-1], os.path.join(KIT, "1-Installateur"))

    base = os.path.join(KIT, "2-Base")
    open(os.path.join(base, "center.sql"), "w", encoding="utf-8").write(build_sql(env, env["DB_NAME"], CENTER_REF))
    open(os.path.join(base, "station.sql"), "w", encoding="utf-8").write(build_sql(env, station_db, STATION_REF, STATION_AFTER))
    crlf(os.path.join(HERE, "installer-base.bat"), os.path.join(base, "installer-base.bat"))
    crlf(os.path.join(HERE, "LISEZ-MOI.txt"), os.path.join(KIT, "LISEZ-MOI.txt"))

    # Partie privée : clés de cartes et licence
    open(os.path.join(PRIVATE, "cles-cartes.sql"), "w", encoding="utf-8").write(
        "DELETE FROM public.data;\n" + dump(env, env["DB_NAME"], "--data-only", "--column-inserts", *tables(["data"])))
    props = os.path.join(ROOT, "backend", "station", "StationSoftware.properties")
    lines = [l.rstrip("\r\n") for l in open(props, encoding="utf-8") if l.split("=", 1)[0].strip() in STATION_KEYS]
    if len(lines) != len(STATION_KEYS) or any(not l.split("=", 1)[1].strip() for l in lines):
        sys.exit("Clés de cartes incomplètes dans backend/station/StationSoftware.properties")
    open(os.path.join(PRIVATE, "cles-station.properties"), "w", encoding="utf-8", newline="\r\n").write(
        "# Clés de cartes du logiciel Station : CONFIDENTIEL\n" + "\n".join(lines) + "\n")
    crlf(os.path.join(HERE, "installer-cles.bat"), os.path.join(PRIVATE, "installer-cles.bat"))
    crlf(os.path.join(HERE, "installer-cles.ps1"), os.path.join(PRIVATE, "installer-cles.ps1"))
    code, day = center_licence()
    open(os.path.join(PRIVATE, "licence-center.txt"), "w", encoding="utf-8", newline="\r\n").write(
        f"Code d'activation de la plateforme Center, valable jusqu'au {day:%d/%m/%Y}.\n"
        "A saisir sur la page de connexion de Center, lien « Activer la plateforme ».\n\n" + code + "\n")

    size = sum(os.path.getsize(os.path.join(d, f)) for d, _, fs in os.walk(OUT) for f in fs)
    print(f"Kit prêt dans {OUT} ({size / 1e6:.0f} Mo)")
    for d, _, fs in os.walk(OUT):
        for f in sorted(fs):
            print("  ", os.path.relpath(os.path.join(d, f), OUT))


if __name__ == "__main__":
    main()
