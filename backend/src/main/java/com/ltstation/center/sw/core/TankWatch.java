package com.ltstation.center.sw.core;

import com.ltstation.center.legacy.Row;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alerte de variation des cuves : le volume de chaque cuve (TankStatus) est comparé à la mesure précédente,
 * une fois par minute ; une variation d'au moins IniInfo.TankAlertVolume litres (réglage TankAlertVolume,
 * 0 = désactivée) est journalisée et affichée sur la fenêtre principale du logiciel Station.
 */
public final class TankWatch {

    private static final Logger LOG = LoggerFactory.getLogger("Cuves");
    private static final Map<Integer, Double> LAST = new ConcurrentHashMap<>();
    private static final Queue<String> PENDING = new ConcurrentLinkedQueue<>();

    private TankWatch() {
    }

    /** Appelé par le moteur de la station toutes les minutes. */
    public static void check() {
        if (IniInfo.TankAlertVolume <= 0) {
            return;
        }
        List<Row> tanks = SW.db().list("TankStatus");
        for (Row t : tanks) {
            int id = t.i("ID");
            double vol = t.d("OilVol");
            Double before = LAST.put(id, vol);
            if (before == null) {
                continue;
            }
            double delta = vol - before;
            if (Math.abs(delta) >= IniInfo.TankAlertVolume) {
                String grade = "";
                try {
                    Row cfg = SW.db().first("tankConfig", "{ID} = :id", com.ltstation.center.legacy.Sugar.p("id", id));
                    grade = cfg == null ? "" : " (" + BaseFunction.GetOilNameByCode(cfg.i("OilCode")) + ")";
                } catch (RuntimeException ignored) {
                    // nom de l'énergie facultatif
                }
                String text = String.format("Alerte cuve %d%s : le volume est passé de %,.0f L à %,.0f L (%s%,.0f L) en une minute.",
                        id, grade, before, vol, delta > 0 ? "+" : "", delta);
                LOG.warn(text);
                PENDING.add(text);
            }
        }
    }

    /** Alertes à afficher (vidées à la lecture). */
    public static String poll() {
        StringBuilder b = new StringBuilder();
        for (String s; (s = PENDING.poll()) != null; ) {
            b.append(b.length() == 0 ? "" : "\n").append(s);
        }
        return b.toString();
    }
}
