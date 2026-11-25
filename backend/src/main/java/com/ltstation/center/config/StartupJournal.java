package com.ltstation.center.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Première ligne utile du journal de diagnostic : versions, bases et dossiers utilisés par ce poste. */
@Component
public class StartupJournal {

    private static final Logger LOG = LoggerFactory.getLogger("Demarrage");

    @Value("${spring.datasource.url:}")
    private String centerDb;
    @Value("${app.station-datasource.url:${station.datasource.url:}}")
    private String stationDb;
    @Value("${server.port:8080}")
    private String port;
    @Value("${app.log-dir:logs}")
    private String logDir;

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        LOG.info("LTStation démarré : version {}, Java {}, {} {}, port {}", getClass().getPackage().getImplementationVersion(),
                System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.version"), port);
        LOG.info("Base Center : {} ; base Station : {}", centerDb, stationDb.isEmpty() ? "(voir configuration)" : stationDb);
        LOG.info("Dossier de travail : {} ; journaux : {}", System.getProperty("user.dir"),
                java.nio.file.Path.of(logDir).toAbsolutePath());
    }
}
