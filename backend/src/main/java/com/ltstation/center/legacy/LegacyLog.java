package com.ltstation.center.legacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Journal texte de l'original (classe Log : un dossier Log/&lt;nom&gt; avec un fichier par jour).
 * Les fichiers sont écrits sous ./logs/&lt;nom&gt;/ ; les messages passent aussi par SLF4J.
 */
public class LegacyLog {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static volatile Path root = Path.of("logs");

    private final String name;
    private final Logger log;

    public LegacyLog(String name) {
        this.name = name;
        this.log = LoggerFactory.getLogger("legacy." + name);
    }

    public static void setRoot(Path dir) {
        root = dir;
    }

    public synchronized void writeLog(String text) {
        log.debug(text);
        LocalDateTime now = LocalDateTime.now();
        Path dir = root.resolve(name);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(now.format(DAY) + ".txt"), now.format(TIME) + "  " + text + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Écriture du journal impossible : {}", e.getMessage());
        }
    }
}
