package com.ltstation.center.service;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.crypto.LegacyAes;
import com.ltstation.center.domain.entity.ActiveWeb;
import com.ltstation.center.domain.repository.ActiveWebRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Licence d'activation de la plateforme (table active_web).
 * secret_info contient une date d'expiration chiffrée (LegacyAes), comme à l'origine.
 */
@Service
public class LicenseService {

    private static final List<DateTimeFormatter> FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    private final ActiveWebRepository repo;
    private final boolean enabled;

    public LicenseService(ActiveWebRepository repo, @Value("${app.license.enabled}") boolean enabled) {
        this.repo = repo;
        this.enabled = enabled;
    }

    /** Lève une erreur si la plateforme n'est pas activée ou si l'activation a expiré. */
    public void checkActive() {
        if (!enabled) {
            return;
        }
        ActiveWeb active = repo.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ApiException("La plateforme n'est pas encore activée"));
        LocalDateTime expiry = parseExpiry(active.getSecretInfo())
                .orElseThrow(() -> new ApiException("Informations d'activation invalides"));
        if (LocalDateTime.now().isAfter(expiry)) {
            throw new ApiException("L'activation a expiré");
        }
    }

    public Optional<LocalDateTime> expiry() {
        return repo.findFirstByOrderByIdAsc().flatMap(a -> parseExpiry(a.getSecretInfo()));
    }

    /**
     * Activation par code (équivalent de ServerDeal/Active).
     * Le code est le JSON chiffré {Path, ActiveMess} produit par l'outil de licence :
     * ActiveMess déchiffré donne la date d'expiration chiffrée, stockée dans active_web.
     * Un code contenant directement la date chiffrée est aussi accepté.
     *
     * @param host nom d'hôte de la requête, comparé à Path comme dans l'original
     */
    @Transactional
    public LocalDateTime activate(String code, String host) {
        String secret = code.trim();
        try {
            String plain = LegacyAes.decrypt(secret);
            if (plain.trim().startsWith("{")) {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(plain);
                String path = node.path("Path").asText("");
                if (!path.isEmpty() && host != null && !path.equalsIgnoreCase(host)) {
                    throw new ApiException("Adresse de la plateforme incorrecte pour ce code");
                }
                secret = LegacyAes.decrypt(node.path("ActiveMess").asText(""));
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Code d'activation invalide");
        }
        LocalDateTime expiry = parseExpiry(secret)
                .orElseThrow(() -> new ApiException("Code d'activation invalide"));
        ActiveWeb active = repo.findFirstByOrderByIdAsc().orElseGet(ActiveWeb::new);
        active.setSecretInfo(secret);
        repo.save(active);
        return expiry;
    }

    private Optional<LocalDateTime> parseExpiry(String secret) {
        if (secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        String plain;
        try {
            plain = LegacyAes.decrypt(secret).trim();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        for (DateTimeFormatter f : FORMATS) {
            try {
                return Optional.of(LocalDateTime.parse(plain, f));
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        try {
            return Optional.of(LocalDate.parse(plain.replace('/', '-')).atTime(23, 59, 59));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
