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


STR = r'"((?:[^"\\]|\\.)*)"'
SKIP_TYPES = ('Container', 'ComponentResourceManager', 'DataGridViewCellStyle', 'ContextMenuStrip', 'Timer', 'ImageList',
              'ToolTip', 'BindingSource', 'ErrorProvider', 'SerialPort', 'PrintDocument', 'PrintPreviewDialog')


def cs_str(v):
    """Littéral de chaîne C# → texte."""
    return re.sub(r'\\(.)', lambda m: {'n': '\n', 't': '\t', 'r': ''}.get(m.group(1), m.group(1)), v).strip()


def designer_controls(cs):
    """Contrôles décrits dans InitializeComponent (fenêtres sans .resx : textes et positions dans le code)."""
    m = re.search(r'private void InitializeComponent\(\)\s*\{(.*)', cs, re.S)
    body = m.group(1) if m else ''
    grid_cols = set()
    for g in re.finditer(r'this\.\w+\.Columns\.AddRange\(([^;]*?)\);', body, re.S):
        grid_cols.update(re.findall(r'this\.(\w+)', g.group(1)))
    controls = {}
    for m in re.finditer(r'this\.(\w+) = new ([\w.]+)\(', body):
        name, typ = m.group(1), m.group(2).split('.')[-1]
        if typ in SKIP_TYPES or typ.startswith('ToolStrip') or typ.startswith('DataGridView') and typ != 'DataGridView' \
                or name in grid_cols or name in controls:
            continue
        controls[name] = {'name': name, 'type': typ, 'parent': None, 'loc': [0, 0], 'size': [0, 0],
                          'text': '', 'dock': None, 'visible': True, 'z': 0}
    for m in re.finditer(r'(?:this\.(\w+)|base)\.Controls\.Add\(this\.(\w+)\);', body):
        if m.group(2) in controls:
            controls[m.group(2)]['parent'] = m.group(1) or '$this'
    # Contrôles jamais ajoutés à un conteneur : composants (ignorés)
    controls = {k: v for k, v in controls.items() if v['parent']}
    for name, c in controls.items():
        n = re.escape(name)
        if (x := re.search(rf'this\.{n}\.Location = new System\.Drawing\.Point\((-?\d+), (-?\d+)\);', body)):
            c['loc'] = [int(x.group(1)), int(x.group(2))]
        if (x := re.search(rf'this\.{n}\.Size = new System\.Drawing\.Size\((\d+), (\d+)\);', body)):
            c['size'] = [int(x.group(1)), int(x.group(2))]
        if (x := re.search(rf'this\.{n}\.Text = {STR};', body)):
            c['text'] = cs_str(x.group(1))
        if (x := re.search(rf'this\.{n}\.Dock = System\.Windows\.Forms\.DockStyle\.(\w+);', body)):
            c['dock'] = None if x.group(1) == 'None' else x.group(1).lower()
        if re.search(rf'this\.{n}\.Visible = false;', body):
            c['visible'] = False
    title = cs_str(x.group(1)) if (x := re.search(rf'base\.Text = {STR};', body)) else ''
    size = [int(x.group(1)), int(x.group(2))] if (x := re.search(r'base\.ClientSize = new System\.Drawing\.Size\((\d+), (\d+)\);', body)) else None
    headers = {c: cs_str(x.group(1)) for c in grid_cols if (x := re.search(rf'this\.{re.escape(c)}\.HeaderText = {STR};', body))}
    return controls, title, size, headers


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
    title = size = None
    headers = {}
    if not neutral and cs:
        controls, title, size, headers = designer_controls(cs)
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
        columns[m.group(1)] = [{'name': c, 'header': (en.get(f'{c}.HeaderText') or neutral.get(f'{c}.HeaderText') or headers.get(c) or c).strip(),
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
        'title': (en.get('$this.Text') or neutral.get('$this.Text') or title or form).strip(),
        'size': pair(neutral.get('$this.ClientSize')) or size or [800, 600],
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
    # Fenêtres sans .resx : conception entièrement dans le code
    have = {k for *_, k in out}
    dirs = [('StationSoftWare.Forms', '')] + [(f'StationSoftware.NormalStatue.{d}', d + '.') for d in
                                             ('BoxScreenView', 'BoxScreenViewV2', 'Card', 'NormalForm', 'Port', 'Station', 'TankConfig')]
    for ns, prefix in dirs:
        d = os.path.join(base, ns)
        if not os.path.isdir(d):
            continue
        for fn in sorted(os.listdir(d)):
            form = fn[:-3]
            key = prefix + form
            if not fn.endswith('.cs') or key in have:
                continue
            src = open(os.path.join(d, fn), encoding='utf-8').read()
            if 'private void InitializeComponent()' in src and re.search(r'class ' + form + r'\s*:\s*\w+', src):
                out.append((form, f'{ns}.{form}', d, key))
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
