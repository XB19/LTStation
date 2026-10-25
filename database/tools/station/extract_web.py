"""Extrait les pages web embarquées dans StationSoftware.exe (code décompilé par ilspycmd) en
reconstituant leur arborescence d'origine (web/login, web/tank, web/NormalStatue…).

Usage : python extract_web.py <dossier_decompile_StationSoftware> <dossier_sortie>
"""
import os
import shutil
import sys

DIRS = {"web", "login", "css", "fonts", "iconic", "images", "vendor", "bootstrap", "tank", "js", "NormalStatue",
        "NormalNoz", "image", "PLC", "RFID"}


def main(src, out):
    n = 0
    for fn in os.listdir(src):
        if not fn.startswith("StationSoftware.web."):
            continue
        segs = fn[len("StationSoftware."):].split(".")
        path = []
        while segs and segs[0] in DIRS and len(segs) - 1 >= 2 and segs[1:] != ["min", "css"]:
            path.append(segs.pop(0))
        dest = os.path.join(out, *path, ".".join(segs))
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        shutil.copyfile(os.path.join(src, fn), dest)
        n += 1
    print(n, "fichiers ->", out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
