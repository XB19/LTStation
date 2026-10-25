"""Extrait les classes d'échange C# d'origine (propriétés publiques, ordre, type) depuis le
code décompilé, pour que le backend Java produise exactement le même JSON que
JavaScriptSerializer (toutes les propriétés, valeurs par défaut C# comprises).

Usage : python dto_gen.py <sortie.json> <dossier> [<dossier> ...]
Clé : "Namespace.Classe" et, si le nom est unique, "Classe".
"""
import json
import os
import re
import sys

NS = re.compile(r'^namespace ([\w.]+)', re.M)
CLASS = re.compile(r'^(?:\[[^\]]*\]\s*)*public (?:sealed |partial |abstract )*class (\w+)(?:<[^>]+>)?(?:\s*:\s*[\w.<>, ]+)?\s*\{', re.M)
PROP = re.compile(r'^\t(?:\[[^\]]*\]\s*)*public (?!static|const|override)(?:virtual )?([\w<>?,\[\] .]+?) (\w+) \{ get; set; \}(?: = ([^;]+);)?', re.M)


def class_bodies(src):
    """Découpe le fichier en (nom de classe, corps) au premier niveau."""
    out = []
    for m in CLASS.finditer(src):
        start = m.end()
        depth = 1
        i = start
        while i < len(src) and depth:
            c = src[i]
            if c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
            i += 1
        out.append((m.group(1), src[start:i - 1]))
    return out


def main():
    dest, *folders = sys.argv[1:]
    full = {}
    for folder in folders:
        for root, _, files in os.walk(folder):
            for fn in files:
                if not fn.endswith('.cs'):
                    continue
                src = open(os.path.join(root, fn), encoding='utf-8-sig').read()
                ns = NS.search(src)
                ns = ns.group(1) if ns else ''
                for cls, body in class_bodies(src):
                    props = []
                    for p in PROP.finditer(body):
                        typ, name, init = p.group(1).strip(), p.group(2), p.group(3)
                        props.append([name, typ] + ([init.strip()] if init else []))
                    if props:
                        full[f'{ns}.{cls}'] = props
    names = {}
    for k in full:
        names.setdefault(k.rsplit('.', 1)[1], []).append(k)
    out = dict(sorted(full.items()))
    for short, keys in names.items():
        if len(keys) == 1:
            out[short] = full[keys[0]]
    with open(dest, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(out, f, ensure_ascii=False, indent=0)
    print(f'{len(full)} classes ({sum(1 for v in names.values() if len(v) == 1)} noms uniques) -> {dest}')


if __name__ == '__main__':
    main()
