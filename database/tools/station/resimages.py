"""Extraction des images des ressources .resx du logiciel Station (Bitmap .NET sérialisés : le fichier
PNG / JPEG / GIF / BMP d'origine est contenu tel quel dans le flux binaire)."""
import base64
import re

SIGS = [(b'\x89PNG\r\n\x1a\n', 'png'), (b'\xff\xd8\xff', 'jpg'), (b'GIF8', 'gif'), (b'BM', 'bmp')]


def decode(b64):
    """(octets du fichier image, extension) ou None."""
    try:
        raw = base64.b64decode(re.sub(r'\s+', '', b64))
    except Exception:
        return None
    best = None
    for sig, ext in SIGS:
        i = raw.find(sig)
        if i >= 0 and (best is None or i < best[0]):
            # « BM » apparaît facilement par hasard : seulement s'il n'y a rien d'autre
            if ext == 'bmp' and best is not None:
                continue
            best = (i, ext)
    if best is None:
        return None
    i, ext = best
    data = raw[i:]
    if ext == 'png':
        end = data.find(b'IEND')
        data = data[:end + 8] if end >= 0 else data
    elif ext == 'jpg':
        end = data.rfind(b'\xff\xd9')
        data = data[:end + 2] if end >= 0 else data
    elif ext == 'bmp' and len(data) >= 6:
        size = int.from_bytes(data[2:6], 'little')
        data = data[:size] if 0 < size <= len(data) else data
    return data, ext


def resx_images(path):
    """{nom de ressource : (octets, extension)} des images d'un fichier .resx."""
    import xml.etree.ElementTree as ET
    out = {}
    try:
        raw = re.sub(r'&#x?[0-9a-fA-F]+;', ' ', open(path, encoding='utf-8-sig').read())
        root = ET.fromstring(raw)
    except Exception:
        return out
    for d in root.findall('data'):
        mt = d.get('mimetype') or ''
        typ = d.get('type') or ''
        v = d.find('value')
        if v is None or not v.text:
            continue
        if 'base64' in mt or 'Bitmap' in typ or 'Icon' in typ:
            img = decode(v.text)
            if img:
                out[d.get('name')] = img
    return out


if __name__ == '__main__':
    import os
    import sys
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    for name, (data, ext) in resx_images(src).items():
        with open(os.path.join(out, f'{name}.{ext}'), 'wb') as f:
            f.write(data)
        print(name, ext, len(data))
