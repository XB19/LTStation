"""
Codes d'activation (rôle du fournisseur, à garder sur le poste de développement, jamais dans le kit client).

  python activation_code.py station <code machine>
      Code machine : affiché par la fenêtre « Enregistrement » du logiciel Station sur le poste du client.
      Renvoie le code d'activation à saisir dans cette même fenêtre.

  python activation_code.py center [AAAA-MM-JJ]
      Licence de la plateforme Center jusqu'à la date donnée (défaut : dans un an).
      Le code se saisit sur la page de connexion de Center, lien « Activer la plateforme ».

Chiffrement : celui de LegacyAes.java (clé lue dans le source, jamais affichée).
"""
import datetime
import os
import re
import sys

from cryptography.hazmat.primitives import padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

SRC = os.path.join(os.path.dirname(__file__), "..", "..", "backend", "src", "main", "java", "com", "ltstation", "center", "crypto",
                   "LegacyAes.java")


def _key_iv():
    src = open(SRC, encoding="utf-8").read()
    return re.search(r'KEY = "([^"]+)"', src).group(1).encode(), re.search(r'IV = "([^"]+)"', src).group(1).encode()


def encrypt(text):
    key, iv = _key_iv()
    p = padding.PKCS7(128).padder()
    data = p.update(text.encode("utf-8")) + p.finalize()
    e = Cipher(algorithms.AES(key), modes.CBC(iv)).encryptor()
    return (e.update(data) + e.finalize()).hex().upper()


def decrypt(hex_text):
    key, iv = _key_iv()
    d = Cipher(algorithms.AES(key), modes.CBC(iv)).decryptor()
    data = d.update(bytes.fromhex(hex_text)) + d.finalize()
    u = padding.PKCS7(128).unpadder()
    return (u.update(data) + u.finalize()).decode("utf-8")


def station(machine_code):
    """La fenêtre Enregistrement accepte le code si son déchiffrement redonne le code machine."""
    machine_code = machine_code.strip()
    decrypt(machine_code)  # refuse un code machine mal recopié
    return encrypt(machine_code)


def center(until=None):
    day = datetime.date.fromisoformat(until) if until else datetime.date.today().replace(year=datetime.date.today().year + 1)
    return encrypt(day.strftime("%Y-%m-%d") + " 23:59:59"), day


def main():
    if len(sys.argv) >= 3 and sys.argv[1] == "station":
        try:
            print(station(sys.argv[2]))
        except ValueError:
            sys.exit("Code machine illisible : vérifiez qu'il est recopié en entier, sans espace.")
    elif len(sys.argv) >= 2 and sys.argv[1] == "center":
        code, day = center(sys.argv[2] if len(sys.argv) > 2 else None)
        print(f"Licence Center valable jusqu'au {day:%d/%m/%Y} :")
        print(code)
    else:
        print(__doc__)


if __name__ == "__main__":
    main()
