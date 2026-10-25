"""Base locale Station (StationNEW d'origine) dans PostgreSQL.

Usage :
  python station_db.py apply [--reset]   crée la base et applique station/schema_*.sql
  python station_db.py migrate [base]    copie les données de la base SQL Server Station

La base s'appelle STATION_DB_NAME (défaut : station). Elle utilise le même serveur et le
même compte que Center, sauf si STATION_DB_HOST / STATION_DB_PORT / STATION_DB_USER /
STATION_DB_PASSWORD sont définis dans LTStation-Center/.env.
"""
import json
import os
import sys

import psycopg
import pyodbc

from migrate_data import load_env

HERE = os.path.dirname(os.path.abspath(__file__))
STATION_DIR = os.path.join(HERE, "..", "station")


def station_env():
    env = load_env()
    return {k: env.get("STATION_" + k) or env[k] for k in ("DB_HOST", "DB_PORT", "DB_USER", "DB_PASSWORD")} | {
        "DB_NAME": env.get("STATION_DB_NAME") or "station"}


def conn(env, dbname=None):
    return psycopg.connect(host=env["DB_HOST"], port=env["DB_PORT"], user=env["DB_USER"],
                           password=env["DB_PASSWORD"], dbname=dbname or env["DB_NAME"])


def q(ident):
    return '"' + ident + '"'


def apply(reset):
    env = station_env()
    with conn(env, "postgres") as admin:
        admin.autocommit = True
        exists = admin.execute("SELECT 1 FROM pg_database WHERE datname=%s", (env["DB_NAME"],)).fetchone()
        if exists and reset:
            admin.execute(f'DROP DATABASE {q(env["DB_NAME"])} WITH (FORCE)')
            exists = None
        if not exists:
            admin.execute(f"CREATE DATABASE {q(env['DB_NAME'])} ENCODING 'UTF8' TEMPLATE template0")
            print(f"Base {env['DB_NAME']} créée")
    with conn(env) as pg:
        has_tables = pg.execute(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'").fetchone()[0]
        names = ("schema_tables.sql", "schema_extra.sql", "schema_views.sql") if has_tables == 0 \
            else ("schema_extra.sql", "schema_views.sql")
        for name in names:
            path = os.path.join(STATION_DIR, name)
            if os.path.exists(path):
                with open(path, encoding="utf-8") as f:
                    pg.execute(f.read())
                print(f"{name} appliqué")


def migrate(src_db):
    env = station_env()
    with open(os.path.join(STATION_DIR, "naming.json"), encoding="utf-8") as f:
        naming = json.load(f)
    src = pyodbc.connect(
        "DRIVER={ODBC Driver 18 for SQL Server};SERVER=.\\SQLEXPRESS;"
        f"DATABASE={src_db};Trusted_Connection=yes;TrustServerCertificate=yes").cursor()
    with conn(env) as pg:
        bool_cols = {}
        for t, c in pg.execute("SELECT table_name, column_name FROM information_schema.columns "
                               "WHERE table_schema='public' AND data_type='boolean'"):
            bool_cols.setdefault(t, set()).add(c)
        pg.execute("TRUNCATE " + ", ".join(q(t) for t in naming["tables"].values()))
        total = 0
        for old_t, new_t in naming["tables"].items():
            cols = naming["columns"][old_t]
            old_cols = list(cols)
            rows = src.execute(f"SELECT {', '.join('[' + c + ']' for c in old_cols)} FROM [{old_t}]").fetchall()
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
                data.append([v.replace("\x00", "") if isinstance(v, str) else v for v in r])
            with pg.cursor() as cur:
                cur.executemany(
                    f"INSERT INTO {q(new_t)} ({', '.join(q(c) for c in new_cols)}) "
                    f"OVERRIDING SYSTEM VALUE VALUES ({', '.join(['%s'] * len(new_cols))})", data)
            total += len(data)
            print(f"{old_t:32s} -> {new_t:32s} {len(data):>6d} lignes")
        for t, c in pg.execute("SELECT table_name, column_name FROM information_schema.columns "
                               "WHERE table_schema='public' AND is_identity='YES'").fetchall():
            pg.execute(f"SELECT setval(pg_get_serial_sequence('{q(t)}', '{c}'), "
                       f"COALESCE((SELECT MAX({q(c)}) FROM {q(t)}), 0) + 1, false)")
        print(f"Total : {total} lignes copiées depuis {src_db}")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "apply"
    if cmd == "apply":
        apply("--reset" in sys.argv)
    elif cmd == "migrate":
        migrate(sys.argv[2] if len(sys.argv) > 2 else "LTStation_Source_StationNEW_Test")
    else:
        sys.exit(__doc__)
