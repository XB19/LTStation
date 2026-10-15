"""Génère le schéma PostgreSQL à partir de la base SQL Server Center d'origine.

Usage : python schema_gen.py [base_sqlserver] > ../schema.sql
Les noms de tables/colonnes sont convertis en snake_case. Un fichier
naming.json (ancien nom -> nouveau nom) est écrit à côté pour la migration.
"""
import json
import os
import re
import sys

import pyodbc

SRC_DB = sys.argv[1] if len(sys.argv) > 1 else "Center_Test"
CONN = (
    "DRIVER={ODBC Driver 18 for SQL Server};SERVER=.\\SQLEXPRESS;"
    f"DATABASE={SRC_DB};Trusted_Connection=yes;TrustServerCertificate=yes"
)

RESERVED = {
    "user", "order", "group", "desc", "default", "table", "check", "column",
    "limit", "offset", "end", "primary", "references", "select", "where",
    "from", "to", "do", "both", "case", "cast", "all", "and", "any", "as",
    "asc", "else", "for", "in", "into", "is", "not", "null", "on", "or",
    "only", "some", "then", "union", "unique", "using", "when", "with",
    "window", "grant", "analyse", "analyze", "array", "current_date",
    "current_time", "current_user", "deferrable", "distinct", "fetch",
    "foreign", "having", "initially", "leading", "lateral", "placing",
    "returning", "session_user", "symmetric", "trailing", "variadic",
}


def snake(name: str) -> str:
    s = name.strip()
    s = re.sub(r"[^0-9A-Za-z_]", "_", s)
    s = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", s)
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", s)
    s = re.sub(r"_+", "_", s).strip("_").lower()
    return s


def q(ident: str) -> str:
    return f'"{ident}"' if ident in RESERVED else ident


def pg_type(t, max_len, prec, scale):
    if t in ("int",):
        return "integer"
    if t == "bigint":
        return "bigint"
    if t in ("smallint", "tinyint"):
        return "smallint"
    if t == "bit":
        return "boolean"
    if t in ("float",):
        return "double precision"
    if t == "real":
        return "real"
    if t in ("decimal", "numeric"):
        return f"numeric({prec},{scale})"
    if t in ("money", "smallmoney"):
        return "numeric(19,4)"
    if t in ("datetime", "datetime2", "smalldatetime"):
        return "timestamp"
    if t == "date":
        return "date"
    if t == "time":
        return "time"
    if t == "datetimeoffset":
        return "timestamptz"
    if t in ("nvarchar", "nchar"):
        if max_len == -1:
            return "text"
        n = max_len // 2
        return f"varchar({n})" if t == "nvarchar" else f"char({n})"
    if t in ("varchar", "char"):
        if max_len == -1:
            return "text"
        return f"varchar({max_len})" if t == "varchar" else f"char({max_len})"
    if t in ("text", "ntext", "xml"):
        return "text"
    if t in ("varbinary", "binary", "image", "timestamp", "rowversion"):
        return "bytea"
    if t == "uniqueidentifier":
        return "uuid"
    raise ValueError(f"type non géré : {t}")


def pg_default(raw, pgt):
    if raw is None:
        return None
    d = raw.strip()
    while d.startswith("(") and d.endswith(")"):
        d = d[1:-1].strip()
    low = d.lower()
    if low in ("getdate()", "sysdatetime()", "getutcdate()"):
        return "now()"
    if low == "newid()":
        return "gen_random_uuid()"
    if pgt == "boolean":
        return "true" if d in ("1", "'1'", "'true'") else "false"
    if d.startswith("N'"):
        d = d[1:]
    if re.fullmatch(r"-?\d+(\.\d+)?", d) or d.startswith("'"):
        if pgt == "timestamp" and d.startswith("'"):
            return f"{d}::timestamp"
        return d
    return None


def main():
    cur = pyodbc.connect(CONN).cursor()
    tables = [r[0] for r in cur.execute(
        "SELECT name FROM sys.tables WHERE is_ms_shipped=0 ORDER BY name")]
    cols = cur.execute("""
        SELECT t.name, c.name, ty.name, c.max_length, c.precision, c.scale,
               c.is_nullable, c.is_identity, OBJECT_DEFINITION(c.default_object_id), c.column_id
        FROM sys.columns c
        JOIN sys.tables t ON t.object_id=c.object_id
        JOIN sys.types ty ON ty.user_type_id=c.user_type_id
        ORDER BY t.name, c.column_id""").fetchall()
    pks = cur.execute("""
        SELECT t.name, c.name, ic.key_ordinal
        FROM sys.indexes i
        JOIN sys.tables t ON t.object_id=i.object_id
        JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
        JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
        WHERE i.is_primary_key=1 ORDER BY t.name, ic.key_ordinal""").fetchall()
    fks = cur.execute("""
        SELECT fk.name, tp.name, cp.name, tr.name, cr.name, fk.delete_referential_action
        FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
        JOIN sys.tables tp ON tp.object_id=fkc.parent_object_id
        JOIN sys.columns cp ON cp.object_id=fkc.parent_object_id AND cp.column_id=fkc.parent_column_id
        JOIN sys.tables tr ON tr.object_id=fkc.referenced_object_id
        JOIN sys.columns cr ON cr.object_id=fkc.referenced_object_id AND cr.column_id=fkc.referenced_column_id
        ORDER BY fk.name, fkc.constraint_column_id""").fetchall()

    naming = {"tables": {}, "columns": {}}
    pk_map = {}
    for t, c, _ in pks:
        pk_map.setdefault(t, []).append(c)

    out = ["-- Schéma PostgreSQL généré depuis la base SQL Server Center d'origine.",
           "-- Ne pas modifier à la main : relancer database/tools/schema_gen.py.", ""]
    by_table = {}
    for row in cols:
        by_table.setdefault(row[0], []).append(row)

    for t in tables:
        nt = snake(t)
        naming["tables"][t] = nt
        naming["columns"][t] = {}
        lines = []
        for (_, c, ty, ml, pr, sc, nullable, ident, dflt, _cid) in by_table[t]:
            nc = snake(c)
            naming["columns"][t][c] = nc
            pgt = pg_type(ty, ml, pr, sc)
            parts = [f"    {q(nc)} {pgt}"]
            if ident:
                parts.append("GENERATED BY DEFAULT AS IDENTITY")
            d = pg_default(dflt, pgt)
            if d is not None and not ident:
                parts.append(f"DEFAULT {d}")
            if not nullable:
                parts.append("NOT NULL")
            lines.append(" ".join(parts))
        if t not in pk_map and any(r[1].lower() == "id" and r[7] for r in by_table[t]):
            # Table sans clé primaire dans l'original : on utilise sa colonne identité "id"
            pk_map[t] = [next(r[1] for r in by_table[t] if r[1].lower() == "id")]
        if t in pk_map:
            lines.append("    PRIMARY KEY (" + ", ".join(q(snake(c)) for c in pk_map[t]) + ")")
        out.append(f"CREATE TABLE {q(nt)} (\n" + ",\n".join(lines) + "\n);\n")

    # Clés étrangères regroupées par contrainte
    grouped = {}
    for name, tp, cp, tr, cr, act in fks:
        g = grouped.setdefault(name, {"tp": tp, "tr": tr, "cp": [], "cr": [], "act": act})
        g["cp"].append(cp)
        g["cr"].append(cr)
    for name, g in grouped.items():
        on_del = " ON DELETE CASCADE" if g["act"] == 1 else (" ON DELETE SET NULL" if g["act"] == 2 else "")
        cname = snake(name)[:63]
        out.append(
            f"ALTER TABLE {q(snake(g['tp']))} ADD CONSTRAINT {q(cname)} FOREIGN KEY ("
            + ", ".join(q(snake(c)) for c in g["cp"]) + f") REFERENCES {q(snake(g['tr']))} ("
            + ", ".join(q(snake(c)) for c in g["cr"]) + f"){on_del};")
    out.append("")
    with open(os.path.join(os.path.dirname(__file__), "naming.json"), "w", encoding="utf-8") as f:
        json.dump(naming, f, ensure_ascii=False, indent=1)
    sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(out))


if __name__ == "__main__":
    main()
