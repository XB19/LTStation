#!/bin/bash
# Premier démarrage du conteneur PostgreSQL : crée les bases Center et Station.
# Données : database/docker/dumps/<base>.sql s'il existe (copie des bases locales), sinon schéma vide.
set -euo pipefail

CENTER_DB="${CENTER_DB:-center}"
STATION_DB="${STATION_DB:-station}"

run() {
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" "$@"
}

load() {
  local db="$1" dump="$2"
  shift 2
  run --dbname postgres -c "CREATE DATABASE \"$db\" ENCODING 'UTF8' TEMPLATE template0"
  if [ -f "$dump" ]; then
    echo "LTStation : $db restaurée depuis $(basename "$dump")"
    run --dbname "$db" -q -f "$dump"
  else
    echo "LTStation : $db créée avec le schéma vide"
    for f in "$@"; do
      run --dbname "$db" -q -f "$f"
    done
  fi
}

load "$CENTER_DB" /schema/docker/dumps/center.sql \
  /schema/schema_tables.sql /schema/schema_extra.sql /schema/schema_views.sql
load "$STATION_DB" /schema/docker/dumps/station.sql \
  /schema/station/schema_tables.sql /schema/station/schema_views.sql
