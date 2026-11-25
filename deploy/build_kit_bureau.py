"""
Prépare le kit des POSTES DE BUREAU dans deploy/out/LTStation-Kit-Bureau/ (jamais publié : il contient les clés de cartes).

  python deploy/build_kit_bureau.py

  LTStation-Kit-Bureau/
      INSTALLER-BUREAU.bat     double-clic sur chaque PC de bureau (application + réglages, sans PostgreSQL)
      LISEZ-MOI-BUREAU.txt     fiche
      station/                 site-acces-bureaux.ps1, à lancer une fois sur le PC de la station
      fichiers/                application, clés de cartes de la station, installer-bureau.ps1

Aucun mot de passe n'est créé ici : celui des bureaux est créé sur le PC de la station par site-acces-bureaux.ps1.
Prérequis : installateur de l'application dans desktop/dist, kit de la station déjà généré (build_kit.py) pour ses clés de cartes.
"""
import glob
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(HERE, "bureau")
STATION_KIT_KEYS = os.path.join(HERE, "out", "LTStation-Kit", "fichiers", "cles-station.properties")
OUT = os.path.join(HERE, "out", "LTStation-Kit-Bureau")
FILES = os.path.join(OUT, "fichiers")


def copy_text(src, dst, bom=False, crlf=True):
    """Scripts PowerShell 5.1 : UTF-8 avec BOM et fins de ligne Windows."""
    text = open(src, encoding="utf-8-sig").read().replace("\r\n", "\n")
    open(dst, "w", encoding="utf-8-sig" if bom else "utf-8", newline="\r\n" if crlf else "\n").write(text)


def main():
    app = sorted(glob.glob(os.path.join(ROOT, "desktop", "dist", "*.exe")))
    if not app:
        sys.exit("Installateur de l'application absent : le générer (desktop, npm run dist)")
    if not os.path.exists(STATION_KIT_KEYS):
        sys.exit("Clés de cartes absentes : générer d'abord le kit de la station (python deploy/build_kit.py)")
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(FILES)
    os.makedirs(os.path.join(OUT, "station"))
    shutil.copy2(app[-1], FILES)
    shutil.copy2(STATION_KIT_KEYS, FILES)
    copy_text(os.path.join(SRC, "installer-bureau.ps1"), os.path.join(FILES, "installer-bureau.ps1"), bom=True)
    copy_text(os.path.join(SRC, "site-acces-bureaux.ps1"), os.path.join(OUT, "station", "site-acces-bureaux.ps1"), bom=True)
    copy_text(os.path.join(SRC, "INSTALLER-BUREAU.bat"), os.path.join(OUT, "INSTALLER-BUREAU.bat"))
    copy_text(os.path.join(SRC, "LISEZ-MOI-BUREAU.txt"), os.path.join(OUT, "LISEZ-MOI-BUREAU.txt"), bom=True)
    print("Kit des postes de bureau prêt :", OUT)
    print("  application :", os.path.basename(app[-1]))


if __name__ == "__main__":
    main()
