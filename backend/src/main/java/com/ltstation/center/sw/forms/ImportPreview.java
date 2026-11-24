package com.ltstation.center.sw.forms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ImportPreview(Key) : contrôle d'un fichier Excel ligne par ligne avant import (équipements, employés).
 * Rien n'est enregistré ici : « Importer » renvoie OK à la fenêtre appelante, qui enregistre tout ou rien.
 */
@SwWindow("ImportPreview")
public class ImportPreview extends SwForm {

    /** Une ligne contrôlée : n° de ligne Excel, action (Création / Mise à jour), contenu résumé, erreurs. */
    public record Line(int number, String action, String detail, List<String> errors) {
    }

    public record Check(String title, List<Line> lines) {

        public long errorCount() {
            return lines.stream().filter(l -> !l.errors().isEmpty()).count();
        }
    }

    private static final Map<String, Check> CHECKS = new ConcurrentHashMap<>();

    /** Dépose un contrôle et renvoie sa clé (argument Key de la fenêtre). */
    public static String put(Check check) {
        String key = UUID.randomUUID().toString();
        CHECKS.put(key, check);
        return key;
    }

    public static void drop(String key) {
        CHECKS.remove(key);
    }

    private Check check;

    @Override
    protected void init() {
        check = CHECKS.get(argStr("Key"));
    }

    public void ImportPreview_Load() {
        if (check == null) {
            MessageBox("Contrôle du fichier expiré : recommencez l'import");
            Close();
            return;
        }
        Title(check.title());
        g("LineView").RowsClear();
        for (Line l : check.lines()) {
            int i = g("LineView").RowsAdd(l.number(), l.errors().isEmpty() ? l.action() : "Erreur", l.detail(), String.join(" ; ", l.errors()));
            if (!l.errors().isEmpty()) {
                g("LineView").RowForeColor(i, "red");
            }
        }
        long errors = check.errorCount();
        int total = check.lines().size();
        c("SummaryLabel").Text(errors > 0
            ? errors + " ligne(s) en erreur sur " + total + " : corrigez le fichier puis recommencez. Rien n'a été enregistré."
            : total + " ligne(s) prête(s) à importer. Rien n'est encore enregistré : cliquez sur « Importer » pour confirmer.");
        c("SummaryLabel").ForeColor(errors > 0 ? "red" : "green");
        c("ImportBtn").Enabled(errors == 0);
        c("ImportBtn").Text("Importer " + total + " ligne(s)");
    }

    public void ImportBtn_Click() {
        Close("OK");
    }

    public void CloseBtn_Click() {
        Close();
    }
}
