package com.ltstation.center.sw.form;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.sw.core.MultiLanguage;
import com.ltstation.center.sw.core.SW;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fenêtre (Form) du logiciel Station exécutée côté serveur. Les gestionnaires d'événements du
 * C# d'origine sont des méthodes publiques sans paramètre portant le même nom (ex. SaveBtn_Click) ;
 * les contrôles sont manipulés comme en WinForms via {@link #c(String)} et {@link #g(String)}.
 *
 * <p>MessageBox Oui/Non et ShowDialog d'une sous-fenêtre suspendent l'événement : le client pose
 * la question (ou ouvre la sous-fenêtre) puis rejoue l'événement avec la réponse.
 */
public abstract class SwForm {

    /** Interruption d'un gestionnaire en attente d'une réponse du client. */
    public static final class Suspend extends RuntimeException {
        final Map<String, Object> request;

        Suspend(Map<String, Object> request) {
            super(null, null, false, false);
            this.request = request;
        }
    }

    String id;
    String formName;
    Layouts.Layout layout;
    final Map<String, Ctl> ctls = new LinkedHashMap<>();
    final Map<String, Grid> grids = new LinkedHashMap<>();
    final List<Map<String, Object>> messages = java.util.Collections.synchronizedList(new ArrayList<>());
    final Map<String, Integer> timers = new LinkedHashMap<>();
    Map<String, Object> answers = new LinkedHashMap<>();
    String handler = "";
    int suspendCounter;
    String closeResult;
    String title;
    /** Paramètres passés à l'ouverture (arguments du constructeur d'origine). */
    protected Map<String, Object> args = new LinkedHashMap<>();
    /** Contrôle à l'origine de l'événement (sender). */
    protected String sender = "";
    /** Arguments de l'événement (RowIndex, ColumnIndex, KeyChar…). */
    protected Row e = new Row();
    /** Session du poste (connexion de l'employé). */
    jakarta.servlet.http.HttpSession session;

    /** Employé connecté (IniInfo.LoginID) : la session du poste est ouverte. */
    protected void SessionLogin() {
        if (session != null) {
            session.setAttribute(com.ltstation.center.sw.web.SwBootController.SESSION_KEY, com.ltstation.center.sw.core.IniInfo.LoginID);
        }
    }

    /** Fenêtre propriétaire (base.Owner) : celle qui a ouvert cette fenêtre. */
    protected SwForm Owner;
    /** Valeurs renvoyées à la fenêtre parente à la fermeture. */
    protected final Row result = new Row();

    // ------------------------------------------------------------ accès aux contrôles

    /** Contrôle par son nom d'origine. */
    public Ctl c(String name) {
        return ctls.computeIfAbsent(name, n -> {
            Ctl c = new Ctl(n);
            c.owner = this;
            return c;
        });
    }

    private int raiseDepth;

    /** Déclenche le gestionnaire d'origine d'un événement du contrôle (modification depuis le code). */
    void raise(String ctl, String... events) {
        if (layout == null || raiseDepth > 8) {
            return;
        }
        Map<String, String> ev = layout.events().getOrDefault(ctl, Map.of());
        Map<String, String> extra = extraEvents.getOrDefault(ctl, Map.of());
        for (String name : events) {
            String h = extra.containsKey(name) ? extra.get(name) : ev.get(name);
            if (h == null) {
                continue;
            }
            String prevSender = sender;
            raiseDepth++;
            try {
                sender = ctl;
                invoke(h);
            } finally {
                raiseDepth--;
                sender = prevSender;
            }
        }
    }

    /** Bouton radio coché : les autres boutons radio du même conteneur sont décochés (avant son propre événement). */
    void radioChecked(String name) {
        if (layout == null || !layout.types().getOrDefault(name, "").endsWith("RadioButton")) {
            return;
        }
        String parent = layout.parents().get(name);
        layout.types().forEach((n, t) -> {
            if (!n.equals(name) && t.endsWith("RadioButton") && parent.equals(layout.parents().get(n)) && c(n).Checked()) {
                c(n).Checked(false);
            }
        });
    }

    /** Appel d'un gestionnaire par son nom (méthode sans paramètre). */
    void invoke(String handler) {
        java.lang.reflect.Method m = SwFormController.method(this, handler);
        if (m == null) {
            return;
        }
        try {
            m.invoke(this);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Tableau (DataGridView) par son nom d'origine. */
    public Grid g(String name) {
        return grids.computeIfAbsent(name, n -> new Grid(n, layout == null ? List.of() : layout.columns().get(n)));
    }

    /** Controls d'un conteneur (ordre d'origine) : noms des contrôles enfants. */
    protected List<String> Controls(String parent) {
        return layout == null ? List.of() : layout.children().getOrDefault(parent, List.of());
    }

    /** Type d'origine d'un contrôle (ex. "CheckBox", "SkinTextBox"). */
    protected String TypeOf(String name) {
        return layout == null ? "" : layout.types().getOrDefault(name, "");
    }

    /** Événements branchés dans le code (X.SkinTxt.TextChanged += …) en plus de ceux du concepteur. */
    final Map<String, Map<String, String>> extraEvents = new LinkedHashMap<>();

    protected void On(String ctl, String event, String handler) {
        extraEvents.computeIfAbsent(ctl, k -> new LinkedHashMap<>()).put(event, handler);
    }

    /** Raccourci : texte d'un contrôle sans espaces de début et de fin (Text.Trim()). */
    protected String t(String name) {
        return c(name).Text().trim();
    }

    protected static String L(String key) {
        return MultiLanguage.getText(key);
    }

    protected static Sugar db() {
        return SW.db();
    }

    protected static Map<String, Object> p(Object... kv) {
        return Sugar.p(kv);
    }

    // ------------------------------------------------------------ boîtes de dialogue

    /** MessageBox.Show(texte) */
    protected void MessageBox(String text) {
        messages.add(Map.of("text", text == null ? "" : text));
    }

    /** MessageBox.Show(texte, titre) */
    protected void MessageBox(String text, String caption) {
        messages.add(Map.of("text", text == null ? "" : text, "caption", caption == null ? "" : caption));
    }

    private String nextKey() {
        return handler + "#" + (suspendCounter++);
    }

    /**
     * MessageBox.Show(texte, titre, YesNo) == Yes, par rejeu : l'événement est rejoué depuis le
     * début avec la réponse. À utiliser uniquement avant toute écriture (sinon {@link #Ask}).
     */
    protected boolean Confirm(String text) {
        String key = nextKey();
        if (answers.containsKey(key)) {
            return Conv.toBool(answers.get(key));
        }
        throw new Suspend(Map.of("type", "confirm", "key", key, "text", text == null ? "" : text));
    }

    /** Réponse d'une sous-fenêtre ouverte par ShowDialog : résultat (OK/Cancel) et valeurs renvoyées. */
    public record DialogResult(String result, Row data) {
        public boolean ok() {
            return "OK".equals(result);
        }
    }

    /**
     * new Form(args).ShowDialog() par rejeu : l'événement est rejoué avec le résultat. À utiliser
     * uniquement avant toute écriture (sinon {@link #ShowDialog(String, Consumer, Object...)}).
     */
    protected DialogResult ShowDialog(String form, Object... kv) {
        String key = nextKey();
        Object a = answers.get(key);
        if (a instanceof Map<?, ?> m) {
            Row r = new Row((Map<String, ?>) m);
            return new DialogResult(r.s("result"), r.row("data") == null ? new Row() : r.row("data"));
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "dialog");
        req.put("key", key);
        req.put("form", form);
        req.put("args", Row.of(kv));
        throw new Suspend(req);
    }

    final Map<String, Consumer<DialogResult>> pending = new LinkedHashMap<>();
    private int pendingSeq;

    /** ShowDialog suivi de la suite du gestionnaire (exécutée à la fermeture de la sous-fenêtre). */
    protected void ShowDialog(String form, Consumer<DialogResult> then, Object... kv) {
        String key = "c" + (++pendingSeq);
        pending.put(key, then);
        messages.add(Map.of("dialog", form, "args", Row.of(kv), "key", key));
    }

    /**
     * OpenFileDialog : le poste choisit un fichier (filtre d'origine, ex. "Excel(*.xls;*.xlsx)|*.xls;*.xlsx"),
     * qui est enregistré dans station/Upload ; la suite reçoit son chemin.
     */
    protected void OpenFileDialog(String filter, Consumer<String> then) {
        String key = "c" + (++pendingSeq);
        pending.put(key, r -> {
            if (!r.ok()) {
                return;
            }
            try {
                String name = java.nio.file.Path.of(r.data().s("name")).getFileName().toString();
                java.nio.file.Path dir = java.nio.file.Path.of("station", "Upload");
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Path file = dir.resolve(System.currentTimeMillis() + "-" + name);
                java.nio.file.Files.write(file, java.util.Base64.getDecoder().decode(r.data().s("content")));
                then.accept(file.toAbsolutePath().toString());
            } catch (java.io.IOException ex) {
                MessageBox(ex.getMessage());
            }
        });
        messages.add(Map.of("openFile", filter == null ? "" : filter, "key", key));
    }

    /** MessageBox Oui/Non suivi de la suite du gestionnaire (yes = réponse Oui). */
    protected void Ask(String text, Consumer<Boolean> then) {
        String key = "c" + (++pendingSeq);
        pending.put(key, r -> then.accept(r.ok()));
        messages.add(Map.of("confirm", text == null ? "" : text, "key", key));
    }

    /** Reprise d'une suite en attente : e = {key, result, data}. */
    void resume() {
        Consumer<DialogResult> then = pending.remove(e.s("key"));
        if (then != null) {
            then.accept(new DialogResult(e.s("result"), e.row("data") == null ? new Row() : e.row("data")));
        }
    }

    /** new Form(args).Show() / ShowDialog() sans attendre le résultat. */
    protected void Open(String form, Object... kv) {
        messages.add(Map.of("open", form, "args", Row.of(kv)));
    }

    /** this.DialogResult = …; Close() */
    protected void Close(String dialogResult) {
        closeResult = dialogResult == null ? "Cancel" : dialogResult;
    }

    protected void Close() {
        Close("Cancel");
    }

    /** Close() suivi d'une nouvelle valeur de DialogResult dans le même gestionnaire : la fenêtre reste ouverte. */
    protected void CancelClose() {
        closeResult = null;
    }

    /** Titre de la fenêtre (Text). */
    protected void Title(String text) {
        title = text;
    }

    /** Timer.Start() : le client appelle le gestionnaire toutes les ms millisecondes ; 0 = Stop(). */
    protected void Timer(String handlerName, int ms) {
        timers.put(handlerName, ms);
    }

    /**
     * PrintClass.Print() : impression directe sur l'imprimante 58 mm du poste ; sans cette
     * imprimante, le ticket est proposé à l'impression dans le navigateur (dialogue d'impression).
     */
    protected void PrintTicket(com.ltstation.center.sw.hw.PrintClass pc) {
        if (!pc.Print()) {
            Print(pc.lines());
        }
    }

    /**
     * Form.TopLevel = false ; Container.Controls.Add(form) ; form.Show() : la fenêtre enfant est
     * affichée dans le conteneur indiqué. Elle reste accessible par sa référence Java.
     */
    protected <T extends SwForm> T Embed(T child, String container) {
        if (child.id != null) {
            // Fenêtre déjà créée (rechargement de la page) : rattachée à cette fenêtre et à la session
            child.Owner = this;
            child.session = session;
            SwFormController.register(session, child);
            messages.add(Map.of("embed", child.formName, "id", child.id, "into", container));
            return child;
        }
        SwWindow w = child.getClass().getAnnotation(SwWindow.class);
        SwFormController.attach(this, child, w == null ? child.getClass().getSimpleName() : w.value());
        messages.add(Map.of("embed", child.formName, "id", child.id, "into", container));
        return child;
    }

    /** Interrogation périodique du serveur (mises à jour venues des fils de la station). */
    protected void Poll(int ms) {
        timers.put("$poll", ms);
    }

    /** Fichier à télécharger par le client (export Excel, impression…). */
    protected void Download(String fileName, String contentType, byte[] data) {
        messages.add(Map.of("download", fileName, "contentType", contentType, "data", java.util.Base64.getEncoder().encodeToString(data)));
    }

    /** Fichier exporté (ExportFile) envoyé au poste : Process.Start(chemin) de l'original. */
    protected void DownloadFile(String path) {
        try {
            java.nio.file.Path p = java.nio.file.Path.of(path);
            String name = p.getFileName().toString();
            String type = name.endsWith(".pdf") ? "application/pdf" : name.endsWith(".xls") ? "application/vnd.ms-excel"
                : "application/octet-stream";
            Download(name, type, java.nio.file.Files.readAllBytes(p));
        } catch (java.io.IOException e) {
            MessageBox(e.getMessage());
        }
    }

    /** Impression d'un ticket (lignes de texte) sur l'imprimante du poste. */
    protected void Print(List<String> lines) {
        messages.add(Map.of("print", lines));
    }

    // ------------------------------------------------------------ cycle de vie

    /** Arguments d'ouverture disponibles : appelé avant Load. */
    protected void init() {
    }

    /** Valeur d'un argument d'ouverture. */
    protected Object arg(String name) {
        return args.get(name);
    }

    protected int argInt(String name) {
        return Conv.toInt(args.get(name));
    }

    protected String argStr(String name) {
        return Conv.toStr(args.get(name));
    }
}
