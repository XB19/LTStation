package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demandes de facture (Main/BillOrder d'origine) — statue : 0 non émise, 1 émise, 2 refusée. */
@RestController
@RequestMapping("/api/tax-bills")
public class TaxBillController {

    private final Db db;

    public TaxBillController(Db db) {
        this.db = db;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return db.list("SELECT * FROM tax_bill ORDER BY statue, id DESC", Map.of());
    }

    public record StatusForm(int statue) {
    }

    @PutMapping("/{id}")
    @Transactional
    public void setStatus(@PathVariable int id, @RequestBody StatusForm f) {
        if (db.update("UPDATE tax_bill SET statue = :s, check_time = now() WHERE id = :id", Map.of("s", f.statue(), "id", id)) == 0) {
            throw new ApiException("La facture n'existe pas");
        }
    }
}
