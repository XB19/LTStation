package com.ltstation.center.legacy;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Équivalent des appels SqlSugar du code d'origine (Queryable / Insertable / Updateable /
 * Deleteable, BeginTran / CommitTran / RollbackTran) sur une base PostgreSQL.
 *
 * <p>Les enregistrements ({@link Row}) portent les noms de colonnes d'origine ; les clauses
 * WHERE / ORDER BY sont écrites en SQL PostgreSQL (noms snake_case). Comme SqlSugar,
 * Insertable/Updateable écrivent toutes les colonnes de l'entité (les absentes prennent la
 * valeur par défaut du type C#) et Updateable se base sur la clé primaire déclarée par le modèle.
 */
public class Sugar {

    /** Colonne PostgreSQL. */
    public record Col(String snake, String orig, String type, boolean nullable, boolean identity) {
    }

    public record Meta(Naming.Table table, List<Col> cols, Map<String, Col> bySnake, List<String> pkSnake) {
    }

    private final String name;
    private final NamedParameterJdbcTemplate jdbc;
    private final PlatformTransactionManager tm;
    private final Naming naming;
    private final Map<String, Meta> metas = new ConcurrentHashMap<>();
    private final ThreadLocal<java.util.Deque<Tx>> txs = ThreadLocal.withInitial(java.util.ArrayDeque::new);

    public Sugar(String name, NamedParameterJdbcTemplate jdbc, PlatformTransactionManager tm, Naming naming) {
        this.name = name;
        this.jdbc = jdbc;
        this.tm = tm;
        this.naming = naming;
    }

    public String name() {
        return name;
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    public Naming naming() {
        return naming;
    }

    /** Paramètres nommés (accepte null) : p("asn", asn, "id", 3). */
    public static Map<String, Object> p(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Nom PostgreSQL (entre guillemets) d'une table désignée par son nom d'origine. */
    public String t(String table) {
        return '"' + naming.table(table).snake() + '"';
    }

    public Meta meta(String table) {
        Naming.Table nt = naming.table(table);
        return metas.computeIfAbsent(nt.snake(), k -> loadMeta(nt));
    }

    private Meta loadMeta(Naming.Table nt) {
        List<Col> cols = jdbc.query(
            "SELECT column_name, data_type, is_nullable = 'YES' AS nullable, is_identity = 'YES' AS ident "
                + "FROM information_schema.columns WHERE table_schema = 'public' AND table_name = :t ORDER BY ordinal_position",
            Map.of("t", nt.snake()),
            (rs, i) -> new Col(rs.getString(1), nt.snakeToOrig().getOrDefault(rs.getString(1), rs.getString(1)),
                rs.getString(2), rs.getBoolean(3), rs.getBoolean(4)));
        Map<String, Col> by = new LinkedHashMap<>();
        cols.forEach(c -> by.put(c.snake(), c));
        List<String> pk = new ArrayList<>();
        for (String o : nt.pk()) {
            String s = nt.origToSnake().get(o);
            if (s != null) {
                pk.add(s);
            }
        }
        if (pk.isEmpty()) {
            pk.addAll(jdbc.queryForList(
                "SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) "
                    + "WHERE i.indrelid = ('\"' || :t || '\"')::regclass AND i.indisprimary ORDER BY array_position(i.indkey, a.attnum)",
                Map.of("t", nt.snake()), String.class));
        }
        return new Meta(nt, cols, by, pk);
    }

    // ---------------------------------------------------------------- lecture

    /** Queryable&lt;T&gt;().Where(...).First() : première ligne ou null. "where" peut contenir ORDER BY. */
    public Row first(String table, String where, Map<String, ?> params) {
        List<Row> l = jdbc.query(select(table, where) + " LIMIT 1", src(params), mapper(meta(table)));
        return l.isEmpty() ? null : l.get(0);
    }

    public Row first(String table) {
        return first(table, null, null);
    }

    public List<Row> list(String table, String where, Map<String, ?> params) {
        return jdbc.query(select(table, where), src(params), mapper(meta(table)));
    }

    public List<Row> list(String table) {
        return list(table, null, null);
    }

    public boolean any(String table, String where, Map<String, ?> params) {
        return first(table, where, params) != null;
    }

    public long count(String table, String where, Map<String, ?> params) {
        Number n = jdbc.queryForObject("SELECT count(*) FROM " + t(table) + clause(sql(table, where)), src(params), Number.class);
        return n == null ? 0 : n.longValue();
    }

    private String select(String table, String where) {
        return "SELECT * FROM " + t(table) + clause(sql(table, where));
    }

    private static final java.util.regex.Pattern PH = java.util.regex.Pattern.compile("\\{(?:(\\w+)\\.)?(\\w+)}|\\[(\\w+)]");

    /**
     * Traduit les noms d'origine dans un fragment SQL : {Col} = colonne de la table principale,
     * {Table.Col} = colonne qualifiée, [Table] = nom de table. Lève une erreur si le nom est inconnu.
     */
    public String sql(String table, String fragment) {
        if (fragment == null || fragment.indexOf('{') < 0 && fragment.indexOf('[') < 0) {
            return fragment;
        }
        java.util.regex.Matcher m = PH.matcher(fragment);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String rep;
            if (m.group(3) != null) {
                rep = naming.has(m.group(3)) ? t(m.group(3)) : m.group(0);
            } else if (m.group(1) != null) {
                rep = t(m.group(1)) + ".\"" + col(m.group(1), m.group(2)) + "\"";
            } else {
                if (table == null) {
                    throw new IllegalArgumentException("Pas de table pour {" + m.group(2) + "}");
                }
                rep = "\"" + col(table, m.group(2)) + "\"";
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Requête libre écrite avec les noms d'origine ({T.Col}, [T]). */
    public String sql(String fragment) {
        return sql(null, fragment);
    }

    private static String clause(String where) {
        if (where == null || where.isBlank()) {
            return "";
        }
        String w = where.trim();
        String up = w.toUpperCase();
        return up.startsWith("ORDER BY") || up.startsWith("LIMIT") ? " " + w : " WHERE " + w;
    }

    /** Résultat de ToPageList(pageIndex, pageSize, ref totalNumber, ref totalPage). */
    public record Page(List<Row> rows, int total, int totalPage) {
    }

    /** Queryable&lt;T&gt;().Where(...).OrderBy(...).ToPageList(...) ; orderBy peut être null. */
    public Page page(String table, String where, Map<String, ?> params, String orderBy, int pageIndex, int pageSize) {
        String base = select(table, where);
        return pageOf(base, params, sql(table, orderBy), pageIndex, pageSize, mapper(meta(table)));
    }

    /** SqlQueryable(sql).ToPageList(...) : colonnes sous leur libellé SQL. */
    public Page pageQuery(String sql, Map<String, ?> params, String orderBy, int pageIndex, int pageSize) {
        return pageOf(sql(sql), params, sql(orderBy), pageIndex, pageSize, RAW);
    }

    private Page pageOf(String sql, Map<String, ?> params, String orderBy, int pageIndex, int pageSize, RowMapper<Row> rm) {
        Number n = jdbc.queryForObject("SELECT count(*) FROM (" + sql + ") pq", src(params), Number.class);
        int total = n == null ? 0 : n.intValue();
        int size = Math.max(pageSize, 1);
        int idx = Math.max(pageIndex, 1);
        String paged = sql + (orderBy == null || orderBy.isBlank() ? "" : " ORDER BY " + orderBy)
            + " LIMIT " + size + " OFFSET " + ((long) (idx - 1) * size);
        List<Row> rows = jdbc.query(paged, src(params), rm);
        return new Page(rows, total, (total + size - 1) / size);
    }

    /** SqlQueryable : requête libre, colonnes renvoyées sous leur libellé SQL (alias). */
    public List<Row> query(String sql, Map<String, ?> params) {
        return jdbc.query(sql(sql), src(params), RAW);
    }

    public Row queryFirst(String sql, Map<String, ?> params) {
        List<Row> l = query(sql, params);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Requête libre dont les colonnes sont celles d'une table (renommées comme l'original). */
    public List<Row> queryAs(String table, String sql, Map<String, ?> params) {
        return jdbc.query(sql(table, sql), src(params), mapper(meta(table)));
    }

    public <T> T scalar(String sql, Map<String, ?> params, Class<T> type) {
        List<T> l = jdbc.queryForList(sql(sql), src(params), type);
        return l.isEmpty() ? null : l.get(0);
    }

    public int exec(String sql, Map<String, ?> params) {
        return jdbc.update(sql(sql), src(params));
    }

    // ---------------------------------------------------------------- écriture

    /** Insertable(entity).ExecuteCommand() ; renvoie la valeur d'identité générée (ou null). */
    public Object insert(String table, Map<String, ?> entity) {
        Meta m = meta(table);
        List<String> names = new ArrayList<>();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        boolean override = false;
        String identity = null;
        for (Col c : m.cols()) {
            Object v = value(entity, c);
            if (c.identity()) {
                identity = c.snake();
                if (v == null || (v instanceof Number n && n.longValue() == 0)) {
                    continue;
                }
                override = true;
            }
            names.add(c.snake());
            ps.addValue(c.snake(), toDb(c, v == null ? defaultFor(c) : v));
        }
        String sql = "INSERT INTO " + t(table) + " (" + join(names, "\"", "\"") + ")"
            + (override ? " OVERRIDING SYSTEM VALUE" : "") + " VALUES (" + join(names, ":", "") + ")";
        if (identity == null) {
            jdbc.update(sql, ps);
            return null;
        }
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(sql, ps, kh, new String[] {identity});
        Number key = kh.getKey();
        Object id = key == null ? null : (key instanceof Long l && l <= Integer.MAX_VALUE ? (Object) l.intValue() : key);
        if (entity instanceof Row r) {
            r.put(m.bySnake().get(identity).orig(), id);
        }
        return id;
    }

    public int insertAll(String table, Collection<? extends Map<String, ?>> rows) {
        int n = 0;
        for (Map<String, ?> r : rows) {
            insert(table, r);
            n++;
        }
        return n;
    }

    /** Updateable(entity).ExecuteCommand() : toutes les colonnes, selon la clé de l'entité. */
    public int update(String table, Map<String, ?> entity) {
        Meta m = meta(table);
        List<String> cols = new ArrayList<>();
        for (Col c : m.cols()) {
            if (!m.pkSnake().contains(c.snake()) && !c.identity()) {
                cols.add(c.snake());
            }
        }
        return doUpdate(m, entity, cols, true);
    }

    /** Updateable(entity).UpdateColumns(...) : seulement les colonnes données (noms d'origine). */
    public int updateColumns(String table, Map<String, ?> entity, String... origCols) {
        Meta m = meta(table);
        List<String> cols = new ArrayList<>();
        for (String o : origCols) {
            cols.add(snakeOf(m, o));
        }
        return doUpdate(m, entity, cols, false);
    }

    /** Mise à jour des colonnes présentes dans l'entité seulement (IgnoreColumns(ignoreAllNull)). */
    public int updatePresent(String table, Map<String, ?> entity) {
        Meta m = meta(table);
        List<String> cols = new ArrayList<>();
        for (Col c : m.cols()) {
            if (!m.pkSnake().contains(c.snake()) && !c.identity() && entity.containsKey(c.orig())) {
                cols.add(c.snake());
            }
        }
        return doUpdate(m, entity, cols, false);
    }

    private int doUpdate(Meta m, Map<String, ?> entity, List<String> cols, boolean defaults) {
        if (m.pkSnake().isEmpty()) {
            throw new IllegalStateException("Table sans clé primaire : " + m.table().orig());
        }
        if (cols.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder set = new StringBuilder();
        for (String s : cols) {
            Col c = m.bySnake().get(s);
            Object v = value(entity, c);
            if (v == null && defaults) {
                v = defaultFor(c);
            }
            if (!set.isEmpty()) {
                set.append(", ");
            }
            set.append('"').append(s).append("\" = :").append(s);
            ps.addValue(s, toDb(c, v));
        }
        StringBuilder where = new StringBuilder();
        for (String k : m.pkSnake()) {
            Col c = m.bySnake().get(k);
            if (!where.isEmpty()) {
                where.append(" AND ");
            }
            where.append('"').append(k).append("\" = :pk_").append(k);
            ps.addValue("pk_" + k, toDb(c, value(entity, c)));
        }
        return jdbc.update("UPDATE \"" + m.table().snake() + "\" SET " + set + " WHERE " + where, ps);
    }

    public int updateAll(String table, Collection<? extends Map<String, ?>> rows) {
        int n = 0;
        for (Map<String, ?> r : rows) {
            n += update(table, r);
        }
        return n;
    }

    /** Deleteable&lt;T&gt;().Where(...).ExecuteCommand(). Sans condition : vide la table. */
    public int delete(String table, String where, Map<String, ?> params) {
        return jdbc.update("DELETE FROM " + t(table) + clause(sql(table, where)), src(params));
    }

    /** Deleteable(entity) : suppression par la clé de l'entité. */
    public int deleteEntity(String table, Map<String, ?> entity) {
        Meta m = meta(table);
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder();
        for (String k : m.pkSnake()) {
            Col c = m.bySnake().get(k);
            if (!where.isEmpty()) {
                where.append(" AND ");
            }
            where.append('"').append(k).append("\" = :").append(k);
            ps.addValue(k, toDb(c, value(entity, c)));
        }
        return jdbc.update("DELETE FROM \"" + m.table().snake() + "\" WHERE " + where, ps);
    }

    /** Nom snake_case d'une colonne désignée par son nom d'origine. */
    public String col(String table, String origCol) {
        return snakeOf(meta(table), origCol);
    }

    private static String snakeOf(Meta m, String orig) {
        String s = m.table().origToSnake().get(orig);
        if (s == null) {
            for (Map.Entry<String, String> e : m.table().origToSnake().entrySet()) {
                if (e.getKey().equalsIgnoreCase(orig)) {
                    return e.getValue();
                }
            }
            throw new IllegalArgumentException("Colonne inconnue " + m.table().orig() + "." + orig);
        }
        return s;
    }

    private static Object value(Map<String, ?> entity, Col c) {
        if (entity instanceof Row r) {
            return r.get(c.orig());
        }
        Object v = entity.get(c.orig());
        return v != null ? v : entity.get(c.snake());
    }

    /** Valeur par défaut d'une propriété C# non initialisée. */
    private static Object defaultFor(Col c) {
        if (c.nullable()) {
            return null;
        }
        return switch (c.type()) {
            case "integer", "smallint", "bigint", "numeric", "double precision", "real" -> 0;
            case "boolean" -> false;
            case "timestamp without time zone", "timestamp with time zone" -> Conv.MIN_DATE;
            case "date" -> LocalDate.of(1, 1, 1);
            case "character varying", "text", "character" -> "";
            default -> null;
        };
    }

    /** Conversion vers le type de la colonne (comme les conversions implicites de SQL Server). */
    private static Object toDb(Col c, Object v) {
        if (v == null) {
            return null;
        }
        return switch (c.type()) {
            case "integer", "smallint" -> v instanceof String s && s.isBlank() ? null : Conv.toInt(v);
            case "bigint" -> v instanceof String s && s.isBlank() ? null : Conv.toLong(v);
            case "numeric" -> v instanceof String s && s.isBlank() ? null : Conv.toDecimal(v);
            case "double precision", "real" -> v instanceof String s && s.isBlank() ? null : Conv.toDouble(v);
            case "boolean" -> v instanceof String s ? (s.equals("1") || s.equalsIgnoreCase("true")) : Conv.toBool(v);
            case "timestamp without time zone", "timestamp with time zone" ->
                v instanceof String s && s.isBlank() ? null : Timestamp.valueOf(Conv.toDateTime(v));
            case "date" -> java.sql.Date.valueOf(Conv.toDateTime(v).toLocalDate());
            case "time without time zone" -> v instanceof LocalTime lt ? java.sql.Time.valueOf(lt) : java.sql.Time.valueOf(Conv.toStr(v));
            case "character varying", "text", "character" -> Conv.toStr(v);
            case "uuid" -> v instanceof java.util.UUID ? v : java.util.UUID.fromString(Conv.toStr(v));
            default -> v;
        };
    }

    // ---------------------------------------------------------------- transactions

    /** BeginTran() : à refermer par commit() ; close() annule si rien n'a été validé. */
    public Tx begin() {
        Tx tx = new Tx(tm.getTransaction(new DefaultTransactionDefinition()));
        txs.get().push(tx);
        return tx;
    }

    /** Annule les transactions encore ouvertes sur ce fil (gestionnaire interrompu). */
    public void rollbackOpen() {
        java.util.Deque<Tx> d = txs.get();
        while (!d.isEmpty()) {
            d.peek().rollback();
        }
    }

    public final class Tx implements AutoCloseable {
        private final TransactionStatus status;
        private boolean done;

        private Tx(TransactionStatus status) {
            this.status = status;
        }

        public void commit() {
            if (!done) {
                done = true;
                txs.get().remove(this);
                tm.commit(status);
            }
        }

        public void rollback() {
            if (!done) {
                done = true;
                txs.get().remove(this);
                tm.rollback(status);
            }
        }

        @Override
        public void close() {
            rollback();
        }
    }

    // ---------------------------------------------------------------- mapping

    private static MapSqlParameterSource src(Map<String, ?> params) {
        MapSqlParameterSource s = new MapSqlParameterSource();
        if (params != null) {
            params.forEach((k, v) -> s.addValue(k, v instanceof LocalDateTime dt ? Timestamp.valueOf(dt)
                : (v instanceof Collection<?> c && c.isEmpty() ? java.util.Collections.singletonList(null) : v)));
        }
        return s;
    }

    private static RowMapper<Row> mapper(Meta m) {
        return (rs, i) -> {
            ResultSetMetaData md = rs.getMetaData();
            Row r = new Row();
            for (int c = 1; c <= md.getColumnCount(); c++) {
                String label = md.getColumnLabel(c);
                Col col = m.bySnake().get(label);
                r.put(col != null ? col.orig() : label, read(rs, c));
            }
            return r;
        };
    }

    private static final RowMapper<Row> RAW = (rs, i) -> {
        ResultSetMetaData md = rs.getMetaData();
        Row r = new Row();
        for (int c = 1; c <= md.getColumnCount(); c++) {
            r.put(md.getColumnLabel(c), read(rs, c));
        }
        return r;
    };

    private static Object read(ResultSet rs, int c) throws SQLException {
        Object v = rs.getObject(c);
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().atStartOfDay();
        }
        if (v instanceof java.sql.Time t) {
            return t.toLocalTime();
        }
        if (v instanceof BigDecimal || v instanceof Number || v instanceof Boolean || v instanceof String || v == null) {
            return v;
        }
        return v;
    }

    private static String join(List<String> names, String pre, String post) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(pre).append(n).append(post);
        }
        return sb.toString();
    }
}
