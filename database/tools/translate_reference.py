"""
Traduit en français les tables de référence reprises de l'original (droits, types de cartes, statuts, unités…).
Remplace uniquement les valeurs exactes de fr_reference.json, dans les colonnes de COLUMNS : les données saisies
par les stations ne sont pas touchées. Sans effet si la traduction est déjà faite.

Usage : python translate_reference.py [center] [station]
Lancé aussi à la fin de migrate_data.py et de station_db.py migrate.
"""
import json
import os
import sys

CENTER = "center"
STATION = "station"

COLUMNS = {
    CENTER: [
        "action_list.action_name", "action_list.memo", "apps.app_desc", "authority_mess.name", "card_status_types.card_status_type_desc",
        "card_types.card_type_desc", "certificate_types.certificate_type_desc", "cylinder_types.name", "def_categories.category_desc",
        "def_classifications.classification_desc", "def_closing_status.closing_status_desc", "def_location_status.location_status_desc",
        "def_payments.payment_desc", "def_trashed_status.trashed_status_desc", "def_unit_packages.unit_package_desc",
        "def_unit_weights.unit_weight_desc", "def_units.unit_desc", "dispatcher.sub_text", "dispatcher.text", "groups.name", "groups.short_name",
        "media_types.media_type_desc", "menu_list.menu_name", "operator_types.operator_type_desc", "score_trans_types.name",
        "transaction_types.transaction_type_desc", "vehicle_colors.name", "vehicle_types.name",
    ],
    STATION: ["authority_mess.name", "group.name", "group.short_name"],
}


def widen(conn, table, name, need):
    """Le français est plus long que le chinois : élargit la colonne si une traduction n'y tient pas."""
    limit = conn.execute("SELECT character_maximum_length FROM information_schema.columns "
                         "WHERE table_schema = 'public' AND table_name = %s AND column_name = %s", (table, name)).fetchone()[0]
    if limit is not None and need > limit:
        conn.execute(f'ALTER TABLE "{table}" ALTER COLUMN "{name}" TYPE varchar(60)')


def translate(kind, conn):
    """Traduit une base (connexion psycopg, validée par l'appelant). Renvoie le nombre de lignes modifiées."""
    with open(os.path.join(os.path.dirname(__file__), "fr_reference.json"), encoding="utf-8") as f:
        fr = json.load(f)
    total = 0
    for col in COLUMNS[kind]:
        table, name = col.split(".")
        if conn.execute("SELECT to_regclass(%s)", (f'public."{table}"',)).fetchone()[0] is None:
            continue
        present = [r[0] for r in conn.execute(f'SELECT DISTINCT "{name}" FROM "{table}"').fetchall() if r[0] in fr]
        if present:
            widen(conn, table, name, max(len(fr[v]) for v in present))
        for zh in present:
            total += conn.execute(f'UPDATE "{table}" SET "{name}" = %s WHERE "{name}" = %s', (fr[zh], zh)).rowcount
    return total


def main():
    from migrate_data import load_env, pg_conn
    import station_db

    env = load_env()
    for kind in sys.argv[1:] or [CENTER, STATION]:
        with (pg_conn(env) if kind == CENTER else station_db.conn(station_db.station_env())) as pg:
            print(f"{kind} : {translate(kind, pg)} lignes traduites")


if __name__ == "__main__":
    main()
