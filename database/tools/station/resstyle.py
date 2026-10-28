"""Apparence des contrôles du logiciel Station : couleurs, polices, alignements et bordures, lus dans le .resx
(objets .NET sérialisés) ou dans InitializeComponent du .cs."""
import base64
import re
import struct

# System.Drawing.KnownColor courants (les autres noms CSS sont les mêmes, en minuscules)
KNOWN = {'Control': '#f0f0f0', 'ControlLight': '#e3e3e3', 'ControlDark': '#a0a0a0', 'ControlText': '#000000', 'Window': '#ffffff',
         'WindowText': '#000000', 'Highlight': '#0078d7', 'HighlightText': '#ffffff', 'GradientActiveCaption': '#b9d1ea',
         'GradientInactiveCaption': '#d7e4f2', 'ActiveCaption': '#99b4d1', 'InactiveCaption': '#bfcddb', 'ButtonFace': '#f0f0f0',
         'ButtonHighlight': '#ffffff', 'ButtonShadow': '#a0a0a0', 'Info': '#ffffe1', 'InfoText': '#000000', 'Menu': '#f0f0f0',
         'MenuText': '#000000', 'ScrollBar': '#c8c8c8', 'AppWorkspace': '#ababab', 'GrayText': '#6d6d6d', 'MenuBar': '#f0f0f0',
         'Transparent': 'transparent'}

ALIGN = {1: 'top-left', 2: 'top-center', 4: 'top-right', 16: 'middle-left', 32: 'middle-center', 64: 'middle-right',
         256: 'bottom-left', 512: 'bottom-center', 1024: 'bottom-right'}
TIR = {0: 'overlay', 1: 'image-above', 2: 'text-above', 4: 'image-before', 8: 'text-before'}
BORDER = {0: None, 1: 'single', 2: 'fixed3d'}


def _raw(v):
    try:
        return base64.b64decode(re.sub(r'\s+', '', v or ''))
    except Exception:
        return b''


def enum_value(v):
    """Valeur entière d'une énumération sérialisée (dernier Int32 du flux) ; texte brut accepté."""
    if not v:
        return None
    s = v.strip()
    if re.fullmatch(r'-?\d+', s):
        return int(s)
    b = _raw(s)
    return struct.unpack('<i', b[-5:-1])[0] if len(b) >= 5 else None


def _color_name(name):
    if name in KNOWN:
        return KNOWN[name]
    return name.lower()


def color(v):
    """« LightSeaGreen », « 9, 163, 220 » ou « 255, 9, 163, 220 » → couleur CSS."""
    if not v:
        return None
    s = v.strip()
    if re.fullmatch(r'\d+,\s*\d+,\s*\d+(,\s*\d+)?', s):
        p = [int(x) for x in s.split(',')]
        if len(p) == 4:
            a, r, g, b = p
            return f'rgba({r},{g},{b},{a / 255:.3f})'
        return '#%02x%02x%02x' % tuple(p)
    if re.fullmatch(r'[A-Za-z]+', s):
        return _color_name(s)
    return None


def cs_color(expr):
    """System.Drawing.Color.X / Color.FromArgb(r, g, b) / SystemColors.X du code du concepteur."""
    if (m := re.search(r'(?:System\.Drawing\.)?(?:Color|SystemColors)\.(\w+)\s*$', expr.strip())):
        return _color_name(m.group(1))
    if (m := re.search(r'FromArgb\((.*)\)', expr)):
        nums = [int(x) for x in re.findall(r'-?\d+', re.sub(r'\(\w+\)', '', m.group(1)))]
        if len(nums) == 3:
            return '#%02x%02x%02x' % tuple(n & 255 for n in nums)
        if len(nums) == 4:
            a, r, g, b = (n & 255 for n in nums)
            return f'rgba({r},{g},{b},{a / 255:.3f})'
        if len(nums) == 1:
            n = nums[0] & 0xFFFFFFFF
            return '#%06x' % (n & 0xFFFFFF)
    return None


def font(v):
    """Police sérialisée (System.Drawing.Font) → {family, size (pt), bold, italic, underline}."""
    b = _raw(v)
    if not b:
        return None
    i = b.find(b'Unit')
    if i < 0:
        return None
    # Nom : enregistrement BinaryObjectString (0x06, id Int32, longueur 7 bits, UTF-8)
    p = b.find(b'\x06', i)
    while p >= 0:
        q = p + 5
        n, shift = 0, 0
        while q < len(b):
            c = b[q]
            q += 1
            n |= (c & 0x7F) << shift
            shift += 7
            if not c & 0x80:
                break
        if 0 < n < 64 and q + n + 4 <= len(b):
            try:
                family = b[q:q + n].decode('utf-8')
                size = struct.unpack('<f', b[q + n:q + n + 4])[0]
                break
            except UnicodeDecodeError:
                pass
        p = b.find(b'\x06', p + 1)
    else:
        return None
    style = 0
    j = b.find(b'value__')
    if j >= 0 and j + 13 + 4 <= len(b):
        style = struct.unpack('<i', b[j + 13:j + 17])[0]
        if not 0 <= style < 16:
            style = 0
    return {'family': family, 'size': round(size, 2), 'bold': bool(style & 1), 'italic': bool(style & 2), 'underline': bool(style & 4)}


def cs_font(expr):
    """new System.Drawing.Font("微软雅黑", 10.5F, System.Drawing.FontStyle.Bold, …)."""
    m = re.search(r'Font\("([^"]*)",\s*([\d.]+)F?(.*)\)', expr)
    if not m:
        return None
    rest = m.group(3)
    return {'family': m.group(1), 'size': float(m.group(2)), 'bold': 'Bold' in rest, 'italic': 'Italic' in rest, 'underline': 'Underline' in rest}


def style_of(name, neutral, cs, target='this'):
    """Apparence d'un contrôle (target = 'base' pour la fenêtre)."""
    n = re.escape(name)
    pre = rf'{target}\.{n}' if target == 'this' else r'base'
    key = name if target == 'this' else '$this'
    st = {}
    for prop, css in (('BackColor', 'back'), ('ForeColor', 'fore')):
        if (m := re.search(rf'{pre}\.{prop} = ([^;]+);', cs)):
            c = cs_color(m.group(1))
        else:
            c = color(neutral.get(f'{key}.{prop}'))
        if c:
            st[css] = c
    if (m := re.search(rf'{pre}\.Font = ([^;]+);', cs)):
        f = cs_font(m.group(1))
    else:
        f = font(neutral.get(f'{key}.Font'))
    if f:
        st['font'] = f
    if (m := re.search(rf'{pre}\.TextAlign = System\.Drawing\.ContentAlignment\.(\w+);', cs)):
        st['align'] = re.sub(r'([a-z])([A-Z])', r'\1-\2', m.group(1)).lower()
    elif (a := enum_value(neutral.get(f'{key}.TextAlign'))) in ALIGN:
        st['align'] = ALIGN[a]
    if (m := re.search(rf'{pre}\.BorderStyle = System\.Windows\.Forms\.BorderStyle\.(\w+);', cs)):
        st['border'] = {'FixedSingle': 'single', 'Fixed3D': 'fixed3d'}.get(m.group(1))
    elif (bs := enum_value(neutral.get(f'{key}.BorderStyle'))) in BORDER and BORDER[bs]:
        st['border'] = BORDER[bs]
    if (m := re.search(rf'{pre}\.Padding = new System\.Windows\.Forms\.Padding\(([\d, ]+)\);', cs)):
        p = [int(x) for x in m.group(1).split(',')]
        st['padding'] = p * 4 if len(p) == 1 else p
    return {k: v for k, v in st.items() if v}
