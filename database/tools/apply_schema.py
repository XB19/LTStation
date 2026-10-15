"""Crée la base PostgreSQL (si absente) et applique schema_tables.sql + schema_views.sql.

Usage : python apply_schema.py [--reset]
  --reset : supprime et recrée la base (toutes les données sont perdues).
"""
import os
import sys

import psycopg

from migrate_data import HERE, load_env, pg_conn


def main():
    env = load_env()
    reset = "--reset" in sys.argv
    with pg_conn(env, "postgres") as admin:
        admin.autocommit = True
        exists = admin.execute("SELECT 1 FROM pg_database WHERE datname=%s", (env["DB_NAME"],)).fetchone()
        if exists and reset:
            admin.execute(f'DROP DATABASE "{env["DB_NAME"]}" WITH (FORCE)')
            exists = None
        if not exists:
            admin.execute(f'CREATE DATABASE "{env["DB_NAME"]}" ENCODING \'UTF8\' TEMPLATE template0')
            print(f"Base {env['DB_NAME']} créée")
    with pg_conn(env) as pg:
        has_tables = pg.execute(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'").fetchone()[0]
        if has_tables == 0:
            for name in ("schema_tables.sql", "schema_extra.sql", "schema_views.sql"):
                with open(os.path.join(HERE, "..", name), encoding="utf-8") as f:
                    pg.execute(f.read())
                print(f"{name} appliqué")
        else:
            for name in ("schema_extra.sql", "schema_views.sql"):
                with open(os.path.join(HERE, "..", name), encoding="utf-8") as f:
                    pg.execute(f.read())
            print("Schéma déjà présent : tables complémentaires et vues mises à jour")


if __name__ == "__main__":
    main()
