package com.ltstation.center.common;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Accès SQL direct pour les rapports et requêtes dynamiques (équivalent des
 * SqlQueryable de SqlSugar). Les colonnes sont renvoyées en camelCase.
 */
@Component
public class Db {

    private static final RowMapper<Map<String, Object>> CAMEL = Db::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public Db(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Paramètres nommés acceptant les valeurs null (contrairement à Map.of) : p("a", 1, "b", null). */
    public static Map<String, Object> p(Object... kv) {
        Map<String, Object> m = new java.util.HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    public List<Map<String, Object>> list(String sql, Map<String, ?> params) {
        return jdbc.query(sql, source(params), CAMEL);
    }

    public Map<String, Object> one(String sql, Map<String, ?> params) {
        List<Map<String, Object>> rows = list(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long count(String sql, Map<String, ?> params) {
        Number n = jdbc.queryForObject("SELECT count(*) FROM (" + sql + ") c", source(params), Number.class);
        return n == null ? 0 : n.longValue();
    }

    public BigDecimal decimal(String sql, Map<String, ?> params) {
        BigDecimal v = jdbc.queryForObject(sql, source(params), BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }

    public int update(String sql, Map<String, ?> params) {
        return jdbc.update(sql, source(params));
    }

    /** Page SQL : ajoute LIMIT/OFFSET et calcule le total. */
    public PageResult<Map<String, Object>> page(String sql, Map<String, ?> params, String orderBy, int page, int size) {
        long total = count(sql, params);
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 10000);
        String paged = sql + (orderBy == null || orderBy.isBlank() ? "" : " ORDER BY " + orderBy)
                + " LIMIT " + s + " OFFSET " + ((long) (p - 1) * s);
        return PageResult.of(list(paged, params), total);
    }

    private static MapSqlParameterSource source(Map<String, ?> params) {
        MapSqlParameterSource src = new MapSqlParameterSource();
        if (params != null) {
            params.forEach((k, v) -> src.addValue(k, v instanceof Collection<?> c && c.isEmpty() ? List.of(-2147483648) : v));
        }
        return src;
    }

    private static Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            Object v = rs.getObject(i);
            if (v instanceof Timestamp ts) {
                v = ts.toLocalDateTime();
            }
            row.put(camel(md.getColumnLabel(i)), v);
        }
        return row;
    }

    public static String camel(String col) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : col.toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return sb.toString();
    }
}
