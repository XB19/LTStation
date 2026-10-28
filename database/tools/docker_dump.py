"""Copie des bases PostgreSQL locales (center, station) pour le premier démarrage de Docker.

Usage : python docker_dump.py
Écrit database/docker/dumps/center.sql et station.sql (exclus de git et de l'image : ils contiennent les données).
Les identifiants sont lus dans LTStation-Center/.env et ne sont jamais affichés.
"""
import os
import shutil
import subprocess
import sys

from migrate_data import load_env

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'docker', 'dumps')


def pg_dump_exe():
    exe = shutil.which('pg_dump')
    if exe:
        return exe
    base = r'C:\Program Files\PostgreSQL'
    for v in sorted(os.listdir(base), reverse=True) if os.path.isdir(base) else []:
        p = os.path.join(base, v, 'bin', 'pg_dump.exe')
        if os.path.exists(p):
            return p
    sys.exit('pg_dump introuvable')


def main():
    env = load_env()
    os.makedirs(OUT, exist_ok=True)
    proc_env = dict(os.environ, PGPASSWORD=env['DB_PASSWORD'])
    for name, db in (('center', env.get('DB_NAME') or 'center'), ('station', env.get('STATION_DB_NAME') or 'station')):
        path = os.path.join(OUT, f'{name}.sql')
        r = subprocess.run([pg_dump_exe(), '-h', env.get('DB_HOST', 'localhost'), '-p', str(env.get('DB_PORT', '5432')), '-U', env['DB_USER'],
                            '-d', db, '--no-owner', '--no-privileges', '--encoding=UTF8', '-f', path], env=proc_env, capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit(f'{db} : ' + r.stderr.replace(env['DB_PASSWORD'], '***'))
        print(f'{db} -> {os.path.normpath(path)} ({os.path.getsize(path) // 1024} Ko)')


if __name__ == '__main__':
    main()
