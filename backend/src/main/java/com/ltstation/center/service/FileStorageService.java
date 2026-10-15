package com.ltstation.center.service;

import com.ltstation.center.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stockage des fichiers envoyés (images de publicité, logo de ticket…). Les images sont
 * enregistrées dans {upload-dir}/img et servies sous /img/{nom}, comme ~/img/ dans l'original.
 */
@Service
public class FileStorageService {

    private static final Set<String> IMAGE_EXT = Set.of(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp");

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** Enregistre une image et renvoie son nom de fichier (millisecondes + extension, comme UploadImage). */
    public String saveImage(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXT.contains(ext)) {
            throw new ApiException("Format d'image non pris en charge");
        }
        String fileName = System.currentTimeMillis() + ext;
        try (InputStream in = file.getInputStream()) {
            Path dir = root.resolve("img");
            Files.createDirectories(dir);
            Files.copy(in, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException("Enregistrement du fichier impossible : " + e.getMessage());
        }
        return fileName;
    }

    /** Supprime un fichier référencé par son URL publique (/img/xxx). */
    public void deleteByUrl(String url) {
        if (url == null || !url.startsWith("/img/")) {
            return;
        }
        Path p = root.resolve(url.substring(1)).normalize();
        if (p.startsWith(root)) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // fichier déjà absent ou verrouillé : sans conséquence
            }
        }
    }
}
