package com.ltstation.center.service;

import com.ltstation.center.common.Db;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Compteurs de version (table data_versions) que les stations consultent pour savoir
 * qu'elles doivent retélécharger une configuration (prix, employés, listes noires…).
 */
@Service
public class DataVersionService {

    private final Db db;

    public DataVersionService(Db db) {
        this.db = db;
    }

    /** Incrémente les compteurs donnés (noms de colonnes de data_versions, ex. "machine_cfg", "ppu"). */
    public void bump(String... columns) {
        if (columns.length == 0) {
            return;
        }
        StringBuilder sql = new StringBuilder("UPDATE data_versions SET ");
        for (int i = 0; i < columns.length; i++) {
            String c = columns[i];
            if (!c.matches("[a-z_]+")) {
                throw new IllegalArgumentException(c);
            }
            sql.append(i == 0 ? "" : ", ").append(c).append(" = ").append(c).append(" + 1, ")
                    .append(c).append("_time = now()");
        }
        sql.append(" WHERE data_version_id = (SELECT min(data_version_id) FROM data_versions)");
        db.update(sql.toString(), Map.of());
    }
}
