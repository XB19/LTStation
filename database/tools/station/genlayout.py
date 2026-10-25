"""Génère la description JSON (positions, tailles, textes, colonnes) d'un formulaire WinForms du logiciel Station.

Usage : python genlayout.py <dossier_decomp> <dossier_sortie> Form1 Form2 ...
Sources : <Form>.resx neutre (positions), <Form>.en.resx (textes anglais), <Form>.cs (ordre des onglets, colonnes des grilles).
"""
import base64, json, os, re, struct, sys
import xml.etree.ElementTree as ET

DECOMP, OUT = sys.argv[1], sys.argv[2]


def load_resx(path):
    if not os.path.exists(path):
        return {}
    raw = re.sub(r'&#x?[0-9a-fA-F]+;', ' ', open(path, encoding='utf-8-sig').read())
    d = {}
    for x in ET.fromstring(raw).findall('data'):
        v = x.find('value')
        d[x.get('name')] = v.text if v is not None and v.text else ''
    return d


def pair(v):
    if not v:
        return None
    if re.fullmatch(r'-?\d+,\s*-?\d+', v.strip()):
        a, b = v.split(',')
        return [int(a), int(b)]
    try:
        b = base64.b64decode(v)
        return list(struct.unpack('<ii', b[-9:-1]))
    except Exception:
        return None


def enum(v, names):
    if not v:
        return None
    if v.strip().isalpha():
        return v.strip().lower()
    try:
        b = base64.b64decode(v)
        return names.get(struct.unpack('<i', b[-5:-1])[0])
    except Exception:
        return None


DOCK = {0: None, 1: 'top', 2: 'bottom', 3: 'left', 4: 'right', 5: 'fill'}


def gen(form, cs_dir, resx_dir, en_dir, out, res=None, key=None):
    """form : nom de la classe ; res : nom complet de la ressource (StationSoftWare.Forms.X par défaut) ;
    key : nom du fichier de sortie (form par défaut)."""
    res = res or f'StationSoftWare.Forms.{form}'
    key = key or form
    neutral = load_resx(os.path.join(resx_dir, f'{res}.resx'))
    en = load_resx(os.path.join(en_dir, f'{res}.en.resx'))
    cs_path = os.path.join(cs_dir, f'{form}.cs')
    cs = open(cs_path, encoding='utf-8').read() if os.path.exists(cs_path) else ''
    controls = {}
    for k in neutral:
        if k.startswith('>>') and k.endswith('.Parent'):
            name = k[2:-7]
            typ = neutral.get(f'>>{name}.Type', '').split(',')[0].split('.')[-1]
            controls[name] = {
                'name': name,
                'type': typ,
                'parent': neutral[k],
                'loc': pair(neutral.get(f'{name}.Location')) or [0, 0],
                'size': pair(neutral.get(f'{name}.Size')) or [0, 0],
                'text': (en.get(f'{name}.Text') or neutral.get(f'{name}.Text') or '').strip(),
                'dock': enum(neutral.get(f'{name}.Dock'), DOCK),
                'visible': neutral.get(f'{name}.Visible', '').strip().lower() != 'false',
                'z': int(neutral.get(f'>>{name}.ZOrder', '0') or 0),
            }
    # Ordre d'ajout dans les conteneurs (ordre des onglets)
    order = {}
    for m in re.finditer(r'this\.(\w+)\.Controls\.Add\(this\.(\w+)\)', cs):
        order.setdefault(m.group(1), []).append(m.group(2))
    for c in controls.values():
        sib = order.get(c['parent'] if c['parent'] != '$this' else '', [])
        c['order'] = sib.index(c['name']) if c['name'] in sib else 999
    # Colonnes des grilles
    columns = {}
    for m in re.finditer(r'this\.(\w+)\.Columns\.AddRange\(([^;]*?)\);', cs, re.S):
        cols = re.findall(r'this\.(\w+)', m.group(2))
        columns[m.group(1)] = [{'name': c, 'header': (en.get(f'{c}.HeaderText') or neutral.get(f'{c}.HeaderText') or c).strip(),
                                'visible': not re.search(rf'this\.{c}\.Visible = false', cs),
                                'prop': (pm.group(1) if (pm := re.search(rf'this\.{c}\.DataPropertyName = "(\w+)"', cs)) else None),
                                'width': int(w.group(1)) if (w := re.search(rf'this\.{c}\.Width = (\d+)', cs)) else None} for c in cols]
    for m in re.finditer(r'(\w+)\.Columns\.Add\(', cs):
        pass
    # Gestionnaires d'événements : this.X.Click += new EventHandler(Y) ; base.Load += ...
    events = {}
    for m in re.finditer(r'(?:this|base)\.(?:(\w+)\.)?(\w+) \+= new [\w.]+\((?:this\.)?(\w+)\);', cs):
        ctl = m.group(1) or '$this'
        events.setdefault(ctl, {})[m.group(2)] = m.group(3)
    layout = {
        'name': key,
        'title': (en.get('$this.Text') or neutral.get('$this.Text') or form).strip(),
        'size': pair(neutral.get('$this.ClientSize')) or [800, 600],
        'controls': sorted(controls.values(), key=lambda c: (c['parent'], c['order'], c['loc'][1], c['loc'][0])),
        'columns': columns,
        'events': events,
    }
    with open(os.path.join(out, f'{key}.json'), 'w', encoding='utf-8') as f:
        json.dump(layout, f, ensure_ascii=False, indent=1)
    return layout


def all_forms(base):
    """(classe, ressource, dossier .cs, clé de sortie) de toutes les fenêtres ayant un .resx."""
    out = []
    for fn in sorted(os.listdir(base)):
        if not fn.endswith('.resx') or fn.count('.') < 2:
            continue
        res = fn[:-5]
        ns, form = res.rsplit('.', 1)
        if ns == 'StationSoftWare.Forms':
            key = form
        elif ns == 'StationSoftWare':
            key = form
        elif ns.startswith('StationSoftware.NormalStatue.'):
            key = ns[len('StationSoftware.NormalStatue.'):] + '.' + form
        else:
            continue
        out.append((form, res, os.path.join(base, ns), key))
    return out


if __name__ == '__main__':
    base = os.path.join(DECOMP, 'StationSoftware')
    en_dir = os.path.join(DECOMP, 'Station_en')
    wanted = set(sys.argv[3:])
    for form, res, cs_dir, key in all_forms(base):
        if wanted and key not in wanted and form not in wanted and 'ALL' not in wanted:
            continue
        lay = gen(form, cs_dir, base, en_dir, OUT, res, key)
        print(key, '|', lay['title'], lay['size'], len(lay['controls']), 'contrôles', {k: len(v) for k, v in lay['columns'].items()})
