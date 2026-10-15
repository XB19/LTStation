"""Génère les entités JPA + repositories Spring Data à partir du schéma PostgreSQL.

Usage : python entity_gen.py
Écrit dans backend/src/main/java/com/ltstation/center/domain/{entity,repository}.
Les noms de classes reprennent ceux des tables SQL Server d'origine (PascalCase).
"""
import json
import os
import re
import shutil

from migrate_data import HERE, load_env, pg_conn

BASE = os.path.join(HERE, "..", "..", "backend", "src", "main", "java", "com", "ltstation", "center", "domain")
PKG = "com.ltstation.center.domain"

TYPES = {
    "integer": "Integer", "bigint": "Long", "smallint": "Short", "boolean": "Boolean",
    "double precision": "Double", "real": "Float", "numeric": "BigDecimal",
    "timestamp without time zone": "LocalDateTime", "timestamp with time zone": "OffsetDateTime",
    "date": "LocalDate", "time without time zone": "LocalTime", "text": "String",
    "character varying": "String", "character": "String", "bytea": "byte[]", "uuid": "UUID",
}
IMPORTS = {
    "BigDecimal": "java.math.BigDecimal", "LocalDateTime": "java.time.LocalDateTime",
    "OffsetDateTime": "java.time.OffsetDateTime", "LocalDate": "java.time.LocalDate",
    "LocalTime": "java.time.LocalTime", "UUID": "java.util.UUID",
}
JAVA_KEYWORDS = {"default", "class", "package", "new", "public", "private", "static", "int", "long",
                 "short", "char", "byte", "boolean", "double", "float", "void", "return", "switch",
                 "case", "native", "final", "this", "super", "goto", "const", "enum", "interface"}


def camel(s):
    parts = s.split("_")
    out = parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:])
    return out + "_" if out in JAVA_KEYWORDS else out


def class_name(old_table):
    parts = re.split(r"_", old_table)
    return "".join(p[:1].upper() + p[1:] for p in parts if p)


def main():
    env = load_env()
    with open(os.path.join(HERE, "naming.json"), encoding="utf-8") as f:
        naming = json.load(f)
    reverse = {v: k for k, v in naming["tables"].items()}
    # Tables complémentaires (schema_extra.sql) : nom de classe = nom de l'entité EF d'origine
    reverse.update({"joy_fill_renew_order": "JoyFill_renewOrder", "joy_fill_scan_pay_record": "JoyFill_scanPayRecord",
                    "supplier_purchase_request_buy": "Supplier_PurchaseRequestBuy"})
    with pg_conn(env) as pg:
        cols = pg.execute("""
            SELECT table_name, column_name, data_type, is_identity, is_nullable, character_maximum_length,
                   numeric_precision, numeric_scale
            FROM information_schema.columns
            WHERE table_schema='public' AND table_name IN
                  (SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE')
            ORDER BY table_name, ordinal_position""").fetchall()
        pks = pg.execute("""
            SELECT tc.table_name, kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name=kcu.constraint_name AND tc.table_schema=kcu.table_schema
            WHERE tc.constraint_type='PRIMARY KEY' AND tc.table_schema='public'
            ORDER BY tc.table_name, kcu.ordinal_position""").fetchall()
    pk_map = {}
    for t, c in pks:
        pk_map.setdefault(t, []).append(c)
    by_table = {}
    for row in cols:
        by_table.setdefault(row[0], []).append(row)

    # Les entités sont régénérées ; les repositories existants (qui peuvent contenir
    # des requêtes ajoutées à la main) sont conservés.
    entity_dir = os.path.join(BASE, "entity")
    if os.path.isdir(entity_dir):
        shutil.rmtree(entity_dir)
    os.makedirs(entity_dir)
    os.makedirs(os.path.join(BASE, "repository"), exist_ok=True)

    for table, rows in sorted(by_table.items()):
        cls = class_name(reverse[table])
        pk_cols = pk_map.get(table, [])
        composite = len(pk_cols) > 1
        imports = {"jakarta.persistence.*", "lombok.Getter", "lombok.Setter"}
        fields = []
        id_fields = []
        for (_, col, dt, ident, nullable, maxlen, prec, scale) in rows:
            jt = TYPES[dt]
            if jt in IMPORTS:
                imports.add(IMPORTS[jt])
            name = camel(col)
            ann = []
            if col in pk_cols:
                ann.append("@Id")
                if ident == "YES" and not composite:
                    ann.append("@GeneratedValue(strategy = GenerationType.IDENTITY)")
                id_fields.append((jt, name))
            quoted = col in ("user", "order", "group", "using", "desc", "default", "table", "check",
                             "column", "limit", "offset", "end", "primary", "references", "select",
                             "where", "from", "to", "do")
            colname = f'\\"{col}\\"' if quoted else col
            ann.append(f'@Column(name = "{colname}")')
            fields.append("    " + "\n    ".join(ann) + f"\n    private {jt} {name};\n")
        lines = [f"package {PKG}.entity;", ""]
        if composite:
            imports.add("java.io.Serializable")
            imports.add("lombok.EqualsAndHashCode")
        for imp in sorted(imports):
            lines.append(f"import {imp};")
        lines += ["", f"/** Table {table} (origine SQL Server : {reverse[table]}). */", "@Getter", "@Setter",
                  "@Entity", f'@Table(name = "{table}")']
        if composite:
            lines.append(f"@IdClass({cls}.Pk.class)")
        lines.append(f"public class {cls} {{")
        lines.append("")
        lines += fields
        if composite:
            lines += ["    @Getter", "    @Setter", "    @EqualsAndHashCode",
                      "    public static class Pk implements Serializable {"]
            for jt, name in id_fields:
                lines.append(f"        private {jt} {name};")
            lines.append("    }")
        lines.append("}")
        with open(os.path.join(BASE, "entity", cls + ".java"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")

        if composite:
            id_type = f"{cls}.Pk"
        elif id_fields:
            id_type = id_fields[0][0]
        else:
            continue
        repo = [f"package {PKG}.repository;", "",
                f"import {PKG}.entity.{cls};",
                "import org.springframework.data.jpa.repository.JpaRepository;",
                "import org.springframework.data.jpa.repository.JpaSpecificationExecutor;", ""]
        if id_type in IMPORTS:
            repo.insert(2, f"import {IMPORTS[id_type]};")
        repo += [f"public interface {cls}Repository extends JpaRepository<{cls}, {id_type}>, "
                 f"JpaSpecificationExecutor<{cls}> {{", "}"]
        repo_path = os.path.join(BASE, "repository", cls + "Repository.java")
        if os.path.exists(repo_path):
            continue
        with open(repo_path, "w", encoding="utf-8") as f:
            f.write("\n".join(repo) + "\n")
    print(f"{len(by_table)} entités générées")


if __name__ == "__main__":
    main()
