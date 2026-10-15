"""Copie toutes les données de la base SQL Server Center vers PostgreSQL.

Usage : python migrate_data.py [base_sqlserver]
Lit la connexion PostgreSQL dans LTStation-Center/.env (jamais en dur).
Prérequis : schema.sql déjà appliqué (python apply_schema.py).
"""
import json
import os
import sys

import psycopg
import pyodbc

HERE = os.path.dirname(os.path.abspath(__file__))
SRC_DB = sys.argv[1] if len(sys.argv) > 1 else "Center_Test"


def load_env():
    env = {}
    with open(os.path.join(HERE, "..", "..", ".env"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip()
    return env


def pg_conn(env, dbname=None):
    return psycopg.connect(host=env["DB_HOST"], port=env["DB_PORT"], user=env["DB_USER"],
                           password=env["DB_PASSWORD"], dbname=dbname or env["DB_NAME"])


def q(ident):
    return '"' + ident + '"'


def main():
    env = load_env()
    with open(os.path.join(HERE, "naming.json"), encoding="utf-8") as f:
        naming = json.load(f)
    src = pyodbc.connect(
        "DRIVER={ODBC Driver 18 for SQL Server};SERVER=.\\SQLEXPRESS;"
        f"DATABASE={SRC_DB};Trusted_Connection=yes;TrustServerCertificate=yes").cursor()
    with pg_conn(env) as pg:
        pg.execute("SET session_replication_role = replica")  # désactive les FK pendant la copie
        bool_cols = {}
        for t, c in pg.execute(
                "SELECT table_name, column_name FROM information_schema.columns "
                "WHERE table_schema='public' AND data_type='boolean'"):
            bool_cols.setdefault(t, set()).add(c)
        # Vider d'abord toutes les tables : un TRUNCATE CASCADE fait après coup
        # effacerait les tables déjà copiées qui la référencent.
        pg.execute("TRUNCATE " + ", ".join(q(t) for t in naming["tables"].values()) + " CASCADE")
        total = 0
        for old_t, new_t in naming["tables"].items():
            cols = naming["columns"][old_t]
            old_cols = list(cols.keys())
            rows = src.execute(
                f"SELECT {', '.join('[' + c + ']' for c in old_cols)} FROM [{old_t}]").fetchall()
            if not rows:
                continue
            new_cols = [cols[c] for c in old_cols]
            bools = [i for i, c in enumerate(new_cols) if c in bool_cols.get(new_t, set())]
            data = []
            for r in rows:
                r = list(r)
                for i in bools:
                    if r[i] is not None:
                        r[i] = bool(r[i])
                for i, v in enumerate(r):
                    if isinstance(v, str):
                        r[i] = v.replace("\x00", "")
                data.append(r)
            with pg.cursor() as cur:
                cur.executemany(
                    f"INSERT INTO {q(new_t)} ({', '.join(q(c) for c in new_cols)}) "
                    f"OVERRIDING SYSTEM VALUE VALUES ({', '.join(['%s'] * len(new_cols))})", data)
            total += len(data)
            print(f"{old_t:40s} -> {new_t:40s} {len(data):>7d} lignes")
        # Recale les séquences d'identité
        for t, c in pg.execute(
                "SELECT table_name, column_name FROM information_schema.columns "
                "WHERE table_schema='public' AND is_identity='YES'").fetchall():
            pg.execute(
                f"SELECT setval(pg_get_serial_sequence('{q(t)}', '{c}'), "
                f"COALESCE((SELECT MAX({q(c)}) FROM {q(t)}), 0) + 1, false)")
        pg.execute("SET session_replication_role = origin")
        print(f"Total : {total} lignes migrées")


if __name__ == "__main__":
    main()
