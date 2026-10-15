package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.FileStorageService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publicités des écrans de station (Main/Advert d'origine) : une campagne (station_ad) par station
 * avec une plage horaire, et ses images (ad_content, type "0").
 */
@RestController
@RequestMapping("/api/adverts")
public class AdvertController {

    private final Db db;
    private final FileStorageService files;

    public AdvertController(Db db, FileStorageService files) {
        this.db = db;
        this.files = files;
    }

    @GetMapping
    public PageResult<Map<String, Object>> list(@CurrentUser SessionUser user, @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        PageResult<Map<String, Object>> r = db.page("SELECT a.*, s.name station_name, s.code station_code FROM station_ad a "
                + "LEFT JOIN stations s ON s.station_id = a.station_id WHERE a.company_id = :c",
                Map.of("c", user.companyGroupId()), "a.id", page, size);
        r.rows().forEach(row -> row.put("images", images(((Number) row.get("id")).intValue())));
        return r;
    }

    public record AdvertForm(Integer stationId, Integer startTime, Integer endTime, List<String> images) {
    }

    /** Création (SaveAdvert + UploadToSaveAD) : content_id = dernier numéro de la station + 1. */
    @PostMapping
    @Transactional
    public Map<String, Object> add(@CurrentUser SessionUser user, @RequestBody AdvertForm f) {
        validate(f);
        Integer max = db.jdbc().queryForObject("SELECT COALESCE(max(content_id), 0) FROM station_ad WHERE station_id = :s",
                Map.of("s", f.stationId()), Integer.class);
        Number id = db.jdbc().queryForObject("INSERT INTO station_ad (company_id, station_id, start_time, end_time, content_id) "
                + "VALUES (:c, :s, :st, :en, :ct) RETURNING id", new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(
                Map.of("c", user.companyGroupId(), "s", f.stationId(), "st", f.startTime(), "en", f.endTime(),
                        "ct", (max == null ? 0 : max) + 1)), Number.class);
        saveImages(user, id.intValue(), f.images());
        return Map.of("id", id);
    }

    /** Modification (AdvertUpdate) : les images sont remplacées par la nouvelle liste. */
    @PutMapping("/{id}")
    @Transactional
    public void update(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody AdvertForm f) {
        validate(f);
        if (db.update("UPDATE station_ad SET station_id = :s, start_time = :st, end_time = :en WHERE id = :id",
                Map.of("s", f.stationId(), "st", f.startTime(), "en", f.endTime(), "id", id)) == 0) {
            throw new ApiException("La publicité n'existe pas");
        }
        List<String> keep = f.images() == null ? List.of() : f.images().stream().map(AdvertController::url).toList();
        for (String old : images(id)) {
            if (!keep.contains(old)) {
                files.deleteByUrl(old);
            }
        }
        db.update("DELETE FROM ad_content WHERE content_id = :id", Map.of("id", id));
        saveImages(user, id, f.images());
    }

    /** Suppression (AdvertDelete) : supprime aussi les fichiers images. */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable int id) {
        images(id).forEach(files::deleteByUrl);
        db.update("DELETE FROM ad_content WHERE content_id = :id", Map.of("id", id));
        if (db.update("DELETE FROM station_ad WHERE id = :id", Map.of("id", id)) == 0) {
            throw new ApiException("La publicité n'existe pas");
        }
    }

    private List<String> images(int id) {
        return db.jdbc().queryForList("SELECT url FROM ad_content WHERE content_id = :id ORDER BY id", Map.of("id", id), String.class);
    }

    private void saveImages(SessionUser user, int id, List<String> images) {
        if (images == null) {
            return;
        }
        for (String img : images) {
            db.update("INSERT INTO ad_content (content_id, url, type, company_id) VALUES (:id, :u, '0', :c)",
                    Map.of("id", id, "u", url(img), "c", user.companyGroupId()));
        }
    }

    /** Normalise un nom de fichier ou une URL en /img/xxx (comme l'original). */
    private static String url(String img) {
        int i = img.indexOf("/img");
        return i == -1 ? "/img/" + img : img.substring(i);
    }

    private static void validate(AdvertForm f) {
        if (f.stationId() == null || f.startTime() == null || f.endTime() == null) {
            throw new ApiException("Station et plage horaire obligatoires");
        }
    }
}
