package com.ltstation.center.sw.core;

import com.ltstation.center.legacy.Dto;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Sugar;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Accès statique à la base locale de la station, comme BaseFunction.serverDeal.baseSql.Db dans
 * le logiciel d'origine (le code porté reste ainsi proche du C#).
 */
@Component
public class SW {

    private static Sugar db;
    private static final Dto DTO = new Dto("station");

    private final Sugar sugar;
    private final String configFile;

    public SW(@Qualifier("stationSugar") Sugar sugar, @Value("${station.config-file:station/StationSoftware.properties}") String configFile) {
        this.sugar = sugar;
        this.configFile = configFile;
    }

    @PostConstruct
    void init() {
        db = sugar;
        LegacyLog.setRoot(Path.of("logs"));
        IniInfo.FILE = Path.of(configFile);
        IniInfo.load();
    }

    /** Base locale de la station (StationNEW). */
    public static Sugar db() {
        return db;
    }

    /** Classes d'échange du logiciel Station (DataBaseModel, NormalModel…). */
    public static Dto dto() {
        return DTO;
    }
}
