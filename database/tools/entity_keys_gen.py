"""Extrait des modèles C# d'origine (code décompilé) les clés primaires et identités déclarées
par SqlSugar ([SugarColumn(IsPrimaryKey/IsIdentity)]) pour chaque table.

Usage : python entity_keys_gen.py <dossier_modeles> [<dossier_modeles> ...] <sortie.json>
Les Updateable/Deleteable de SqlSugar se basent sur ces clés (et non sur celles de la base) :
le backend Java les utilise pour reproduire exactement les mêmes mises à jour.
"""
import json
import os
import re
import sys

CLASS = re.compile(r'(?:\[SugarTable\("(?P<table>[^"]+)"\)\]\s*)?public (?:partial )?class (?P<cls>\w+)')
PROP = re.compile(r'(?P<attrs>(?:\[[^\]]*\]\s*)*)public (?P<type>[\w<>?,\[\] .]+?) (?P<name>\w+) \{ get; set; \}')


def scan(folder, out):
    for root, _, files in os.walk(folder):
        for fn in files:
            if not fn.endswith(".cs"):
                continue
            src = open(os.path.join(root, fn), encoding="utf-8-sig").read()
            m = CLASS.search(src)
            if not m:
                continue
            table = m.group("table") or m.group("cls")
            pk, ident, ignore, props = [], [], [], []
            for p in PROP.finditer(src[m.end():]):
                a, name = p.group("attrs"), p.group("name")
                props.append(name)
                if "IsPrimaryKey = true" in a:
                    pk.append(name)
                if "IsIdentity = true" in a:
                    ident.append(name)
                if "IsIgnore = true" in a:
                    ignore.append(name)
            if not pk and not ident:
                continue
            prev = out.get(table)
            if prev and prev["pk"] != pk:
                prev.setdefault("variants", []).append({"class": m.group("cls"), "pk": pk})
                continue
            out[table] = {"pk": pk, "identity": ident, "ignore": ignore}


def main():
    *folders, dest = sys.argv[1:]
    out = {}
    for f in folders:
        scan(f, out)
    with open(dest, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(out.items())), f, ensure_ascii=False, indent=1)
    print(f"{len(out)} tables -> {dest}")


if __name__ == "__main__":
    main()
