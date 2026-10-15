package com.ltstation.center.web;

import com.ltstation.center.service.FileStorageService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Envoi d'images (Home/UploadImage d'origine) : renvoie le nom du fichier et son URL /img/…. */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final FileStorageService files;

    public UploadController(FileStorageService files) {
        this.files = files;
    }

    @PostMapping("/image")
    public Map<String, String> image(@RequestPart("file") MultipartFile file) {
        String name = files.saveImage(file);
        return Map.of("name", name, "url", "/img/" + name);
    }
}
