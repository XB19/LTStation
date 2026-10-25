"""Prépare la base locale Station pour la station de démonstration DEMO001 (comme après l'assistant
« Créer la station » de l'original) : fiche station copiée depuis Center, licence (RegisterInfo)
pour les adresses MAC de ce poste, employés de la station, versions remises à zéro (la première
synchronisation télécharge alors toutes les listes depuis Center).

Usage : python seed_station_demo.py
"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from cryptography.hazmat.primitives import padding  # noqa: E402
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes  # noqa: E402

from migrate_data import load_env, pg_conn  # noqa: E402
from station_db import conn, station_env  # noqa: E402

KEY = b"IXNRPASLSX729SI3Y2I9AN4MS57W25Z8"
IV = b"Z72NX84FDK9A72H2"
CODE = "DEMO001"


def enc(s):
    p = padding.PKCS7(128).padder()
    data = p.update(s.encode()) + p.finalize()
    e = Cipher(algorithms.AES(KEY), modes.CBC(IV)).encryptor()
    return (e.update(data) + e.finalize()).hex()


def macs():
    """Adresses MAC du poste au format .NET (AA-BB-CC-DD-EE-FF)."""
    out = subprocess.run(["getmac", "/fo", "csv", "/nh"], capture_output=True, text=True).stdout
    res = []
    for line in out.splitlines():
        m = line.split(",")[0].strip('"')
        if len(m) == 17:
            res.append(m.upper())
    return res


def main():
    center = pg_conn(load_env())
    st = center.execute("""SELECT s.station_id, s.code, s.name, s.address, s.manager, s.phone, s.email1, s.city_id, c.code,
                                  c.region_id, r.code, s.company_id
                           FROM stations s JOIN cities c ON c.city_id = s.city_id JOIN regions r ON r.region_id = c.region_id
                           WHERE s.code = %s""", (CODE,)).fetchone()
    if not st:
        sys.exit("Station DEMO001 absente de Center : lancez d'abord seed_demo.py")
    # Rôle « administrateur » (PostCode 1) pour l'employé de démonstration, sinon le logiciel refuse la connexion.
    center.execute("UPDATE employees SET post_code = '1' WHERE station_id = %s AND coalesce(post_code, '') = ''", (st[0],))
    center.commit()
    emps = center.execute("""SELECT employee_id, code_inner, name, address, memo, post_code, certify_key, birthday, gender_id
                             FROM employees WHERE station_id = %s""", (st[0],)).fetchall()
    with conn(station_env()) as s:
        s.execute("DELETE FROM stations")
        s.execute("""INSERT INTO stations (station_id, code, name, address, manager, tel, email, city_id, city_code, region_id,
                     region_code, super_code, auto_measure, measure_time) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'0001',false,
                     to_char(now(), 'YYYY-MM-DD HH24:MI:SS'))""",
                  (st[0], st[1], st[2], st[3], st[4], st[5], st[6], st[7], st[8], st[9], st[10]))
        s.execute("DELETE FROM register_info")
        for m in macs():
            s.execute("INSERT INTO register_info (source_info, secret_info) VALUES (%s, %s)", (enc(m), enc(enc(m))))
        s.execute("DELETE FROM employees")
        for e in emps:
            s.execute("""INSERT INTO employees (station_id, employee_id, code, name, memo1, memo2, station_type, certify_key,
                         birthday, status, gender) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,'0',%s)""",
                      (st[0], e[0], str(e[1] or 0), e[2], e[3], e[4] or "654789", e[5], e[6], str(e[7]) if e[7] else None, str(e[8] or 1)))
        s.execute("""UPDATE data_versions SET machine_cfg = 0, ppu = 0, grade_spec = 0, basic_black = 0, append_black = 0,
                     del_black = 0, white = 0, gray = 0, employee = 0, coupon = 0, distribution_points = 0, transport_infoes = 0""")
        print("Station", st[1], st[2], "| licence pour", len(macs()), "adresse(s) MAC |", len(emps), "employé(s)")


if __name__ == "__main__":
    main()
