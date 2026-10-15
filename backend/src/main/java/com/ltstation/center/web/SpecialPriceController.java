package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.service.DataVersionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Prix préférentiels par énergie et période (Main/SpecialPrice d'origine). */
@RestController
@RequestMapping("/api/special-prices")
public class SpecialPriceController {

    private final Db db;
    private final DataVersionService versions;

    public SpecialPriceController(Db db, DataVersionService versions) {
        this.db = db;
        this.versions = versions;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return db.list("SELECT p.*, g.name grade_name FROM sprice_price_set p "
                + "LEFT JOIN grade_specifics g ON g.grade_specific_id = p.grade_id ORDER BY p.id", Map.of());
    }

    public record SpecialPriceForm(String name, Integer gradeId, BigDecimal price, LocalDateTime start, LocalDateTime end) {
    }

    /** Ajout : incrémente la version des prix pour que les stations le récupèrent. */
    @PostMapping
    @Transactional
    public void add(@RequestBody SpecialPriceForm f) {
        validate(f);
        if (db.one("SELECT 1 FROM sprice_price_set WHERE name = :n", Db.p("n", f.name())) != null) {
            throw new ApiException("Nom en double");
        }
        db.update("INSERT INTO sprice_price_set (name, grade_id, sprice_price, start_time, end_time, create_time, is_out) "
                + "VALUES (:n, :g, :p, :s, :e, now(), false)",
                Db.p("n", f.name(), "g", f.gradeId(), "p", f.price(), "s", f.start(), "e", f.end()));
        versions.bump("ppu");
    }

    /** Modification : la version des prix n'est incrémentée que si la période n'a pas encore commencé (comme l'original). */
    @PutMapping("/{id}")
    @Transactional
    public void update(@PathVariable int id, @RequestBody SpecialPriceForm f) {
        validate(f);
        if (db.one("SELECT 1 FROM sprice_price_set WHERE id = :id", Db.p("id", id)) == null) {
            throw new ApiException("Information inexistante");
        }
        if (db.one("SELECT 1 FROM sprice_price_set WHERE name = :n AND id <> :id", Db.p("n", f.name(), "id", id)) != null) {
            throw new ApiException("Nom en double");
        }
        db.update("UPDATE sprice_price_set SET name = :n, grade_id = :g, sprice_price = :p, start_time = :s, end_time = :e "
                + "WHERE id = :id", Db.p("n", f.name(), "g", f.gradeId(), "p", f.price(), "s", f.start(), "e", f.end(), "id", id));
        if (LocalDateTime.now().isBefore(f.start())) {
            versions.bump("ppu");
        }
    }

    /** Suppression : version des prix incrémentée si le prix préférentiel n'avait pas encore expiré. */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable int id) {
        Map<String, Object> row = db.one("SELECT end_time FROM sprice_price_set WHERE id = :id", Db.p("id", id));
        if (row == null) {
            throw new ApiException("Information inexistante");
        }
        db.update("DELETE FROM sprice_price_set WHERE id = :id", Db.p("id", id));
        if (row.get("endTime") instanceof LocalDateTime end && LocalDateTime.now().isBefore(end)) {
            versions.bump("ppu");
        }
    }

    private static void validate(SpecialPriceForm f) {
        if (f.name() == null || f.name().isBlank() || f.gradeId() == null || f.price() == null || f.start() == null
                || f.end() == null) {
            throw new ApiException("Informations incomplètes");
        }
        if (f.end().isBefore(f.start())) {
            throw new ApiException("La fin de période doit être après le début");
        }
    }
}
