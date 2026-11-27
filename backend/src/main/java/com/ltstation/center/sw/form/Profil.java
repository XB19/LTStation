package com.ltstation.center.sw.form;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Profils restreints du logiciel Station (en plus des droits 1 à 24 de AuthorityMess).
 *
 * <p>Un profil devient restreint quand son AuthorityContent contient le mot SAISIE ou CONSULTATION :
 * <ul>
 *   <li><b>Opérateur de saisie</b> (SAISIE) : cartes employé (création), mise à jour, carte perdue, remplacement,
 *       solde en lecture, historique des cartes et des transactions, exports. Aucune recharge ni mouvement de solde.</li>
 *   <li><b>Consultation</b> (CONSULTATION) : solde, historique des cartes, transactions, rapports et activité,
 *       en lecture seule : ni modification, ni clôture, ni export, ni impression.</li>
 * </ul>
 * Les autres profils (Administrateur…) gardent le fonctionnement d'origine.
 *
 * <p>Le contrôle est fait côté serveur, à trois niveaux : ouverture des fenêtres, gestionnaires d'événements
 * et appels au Center (BaseFunction.HttpPost). Masquer les boutons ne sert qu'au confort de l'écran.
 */
public final class Profil {

    private static final Logger LOG = LoggerFactory.getLogger(Profil.class);

    public enum Mode { COMPLET, SAISIE, CONSULTATION }

    public static final String SAISIE = "SAISIE";
    public static final String CONSULTATION = "CONSULTATION";

    // ------------------------------------------------------------ deux administrateurs

    /**
     * Administrateur général : tout le Center et toute l'application ; lui seul donne l'accès au Center et crée
     * ou modifie les comptes administrateurs. Administrateur application : toute l'application, jamais le Center.
     */
    public static final String ADMIN_GENERAL = "ADMIN_GENERAL";
    public static final String ADMIN_APPLICATION = "ADMIN_APPLICATION";
    public static final String ONLY_GENERAL = "Réservé à l'administrateur général";

    private static boolean has(String content, String token) {
        if (content != null) {
            for (String s : content.split(",")) {
                if (token.equalsIgnoreCase(s.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Enregistre le nom et les droits d'un profil (la table n'a pas de clé primaire déclarée : mise à jour par son numéro). */
    public static void saveProfile(Object userIdMess, String name, String content) {
        Sugar db = SW.db();
        db.exec(db.sql("AuthorityMess", "UPDATE [AuthorityMess] SET {Name} = :n, {AuthorityContent} = :c WHERE {User_IDMess} = :u"),
            Sugar.p("n", name, "c", content, "u", com.ltstation.center.legacy.Conv.toInt(userIdMess)));
    }

    /** Profil d'administrateur (général ou application). */
    public static boolean isAdmin(String content) {
        return has(content, ADMIN_GENERAL) || has(content, ADMIN_APPLICATION);
    }

    public static boolean isGeneral(String content) {
        return has(content, ADMIN_GENERAL);
    }

    /** Profil par son numéro (Employees.StationType = AuthorityMess.User_IDMess). */
    public static Row profile(Object userIdMess) {
        if (userIdMess == null || String.valueOf(userIdMess).isBlank()) {
            return null;
        }
        return SW.db().first("AuthorityMess", "{User_IDMess} = :id", Sugar.p("id", com.ltstation.center.legacy.Conv.toInt(userIdMess)));
    }

    /** Le numéro de profil désigne-t-il un administrateur (général ou application) ? */
    public static boolean adminProfile(Object userIdMess) {
        Row a = profile(userIdMess);
        return a != null && isAdmin(a.s("AuthorityContent"));
    }

    /** L'employé connecté est-il administrateur général ? */
    public static boolean currentIsGeneral() {
        try {
            Row a = profile(IniInfo.LoginType);
            return a != null && isGeneral(a.s("AuthorityContent"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Profils que l'employé connecté peut donner : tous pour l'administrateur général, sinon sans les administrateurs. */
    public static List<Row> assignable(List<Row> all) {
        if (currentIsGeneral()) {
            return all;
        }
        List<Row> out = new ArrayList<>();
        for (Row r : all) {
            if (!isAdmin(r.s("AuthorityContent"))) {
                out.add(r);
            }
        }
        return out;
    }

    /** Assistant de création de la station : le premier employé est l'administrateur général. */
    public static List<Row> firstEmployeeProfiles(List<Row> all) {
        List<Row> out = new ArrayList<>();
        for (Row r : all) {
            if (isGeneral(r.s("AuthorityContent"))) {
                out.add(r);
            }
        }
        return out.isEmpty() ? all : out;
    }

    /** Vrai pendant l'exécution d'un gestionnaire de fenêtre (pas pour les fils du moteur de la station). */
    private static final ThreadLocal<Boolean> UI = new ThreadLocal<>();

    private Profil() {
    }

    // ------------------------------------------------------------ profil courant

    /** Mode d'un AuthorityContent (« 2,4,7,SAISIE, »). */
    public static Mode of(String content) {
        if (content == null) {
            return Mode.COMPLET;
        }
        for (String s : content.split(",")) {
            String t = s.trim();
            if (CONSULTATION.equalsIgnoreCase(t)) {
                return Mode.CONSULTATION;
            }
            if (SAISIE.equalsIgnoreCase(t)) {
                return Mode.SAISIE;
            }
        }
        return Mode.COMPLET;
    }

    /** Mode du profil de l'employé connecté (IniInfo.LoginType = AuthorityMess.User_IDMess). */
    public static Mode current() {
        try {
            Row a = SW.db().first("AuthorityMess", "{User_IDMess} = :id", Sugar.p("id", IniInfo.LoginType));
            return a == null ? Mode.COMPLET : of(a.s("AuthorityContent"));
        } catch (RuntimeException e) {
            LOG.warn("Profil de l'employé illisible : {}", e.getMessage());
            return Mode.COMPLET;
        }
    }

    /** Mots du mode à conserver quand la fenêtre Authority réécrit les cases cochées. */
    public static String tokens(String content) {
        StringBuilder b = new StringBuilder();
        if (content != null) {
            for (String s : content.split(",")) {
                String t = s.trim();
                if (SAISIE.equalsIgnoreCase(t) || CONSULTATION.equalsIgnoreCase(t) || ADMIN_GENERAL.equalsIgnoreCase(t)
                    || ADMIN_APPLICATION.equalsIgnoreCase(t)) {
                    b.append(t.toUpperCase()).append(',');
                }
            }
        }
        return b.toString();
    }

    private static String label(Mode m) {
        return m == Mode.SAISIE ? "Opérateur de saisie" : "Consultation";
    }

    private static String refused(Mode m) {
        return "Action non autorisée pour le profil « " + label(m) + " »";
    }

    /** Fenêtres de CardMake et du mode Normal : droits propres, hors de ces profils. */
    private static boolean outOfScope(String form) {
        return form == null || form.startsWith("CardMake.") || form.startsWith("NormalForm.");
    }

    // ------------------------------------------------------------ fenêtres

    private static final Set<String> COMMON = Set.of("Login", "Station", "PWDInput", "ProBar");

    private static final Set<String> SAISIE_FORMS = Set.of("CardMake", "CardUpDate", "CardLost", "EmpLost", "CardChange", "Deposit",
        // CardWorkOrder : « Historique des ordres » des fenêtres de carte (ordre de travail des cartes employé)
        "CardLog", "OilTransacation", "CardWorkOrder");

    private static final Set<String> CONSULTATION_FORMS = Set.of("Deposit", "CardLog", "OilTransacation", "ShiftForm", "RDaily",
        "DailyClose", "MonthClose", "BusinessReportMess");

    /** Ouverture d'une fenêtre : refusée si elle n'est pas prévue pour le profil. */
    static void checkOpen(String form) {
        if (outOfScope(form) || COMMON.contains(form)) {
            return;
        }
        Mode m = current();
        if (m == Mode.COMPLET) {
            return;
        }
        Set<String> allowed = m == Mode.SAISIE ? SAISIE_FORMS : CONSULTATION_FORMS;
        if (!allowed.contains(form)) {
            LOG.info("Fenêtre {} refusée au profil {}", form, m);
            throw new com.ltstation.center.sw.web.SwException(refused(m), 403);
        }
    }

    // ------------------------------------------------------------ boutons et gestionnaires

    /** Solde : tout ce qui modifie un solde (recharge, retrait, consommation, transfert, report, remboursement). */
    private static final Set<String> DEPOSIT_SENDERS = Set.of("DepositButton", "DeductionButton", "ConsumeButton", "MasterDeposter",
        "STConfirm", "TransferBtn");
    private static final Set<String> DEPOSIT_HANDLERS = Set.of("DepositButton_Click", "DeductionButton_Click", "MasterDeposter_Click",
        "STConfirm_Click", "TransferBtn_Click", "RepayView_CellDoubleClick");
    private static final List<String> DEPOSIT_PAGES = List.of("Transfer", "CardDeduction", "CardConsume", "STransfer", "CreditPay",
        "DepositForMasterCard");

    /** Création de carte : onglets et boutons des cartes clients (seul l'onglet Carte employé reste). */
    private static final List<String> CARD_PAGES = List.of("PersonPage", "PersonGroup", "CreditCard", "GroupChilrdCard");
    private static final Set<String> CARD_SENDERS = Set.of("PersonButton", "GroupButton", "CreditButton", "ChilrenConfirm",
        "PersonInitButton", "GroupIniButton", "CreditIniButton", "ChilrenInit");
    private static final int EMPLOYEE_PAGE = 1;

    /** Consultation : boutons de clôture et d'export, par fenêtre. */
    private static final Map<String, List<String>> READ_ONLY_BUTTONS = Map.of(
        "CardLog", List.of("Export"),
        "OilTransacation", List.of("Export", "ExportPage"),
        "BusinessReportMess", List.of("Export"),
        "ShiftForm", List.of("ShiftClose"),
        "RDaily", List.of("DailyReport"),
        "DailyClose", List.of("DailyReport", "Export"),
        "MonthClose", List.of("MonthReport", "Export"));
    private static final Map<String, Set<String>> READ_ONLY_HANDLERS = Map.of(
        "CardLog", Set.of("Export_Click"),
        "OilTransacation", Set.of("ExportPage_Click"),
        "BusinessReportMess", Set.of("Export_Click"),
        "ShiftForm", Set.of("ShiftClose_Click"),
        "DailyClose", Set.of("DailyReport_Click", "Export_Click"),
        "MonthClose", Set.of("MonthReport_Click", "Export_Click"));

    /** Message d'erreur si le gestionnaire est interdit au profil, sinon null. */
    static String deny(SwForm f, String handler) {
        if (outOfScope(f.formName) || handler == null || handler.isEmpty() || "init".equals(handler) || "$continue".equals(handler)) {
            return null;
        }
        Mode m = current();
        if (m == Mode.COMPLET) {
            return null;
        }
        String sender = f.sender == null ? "" : f.sender;
        boolean no = switch (f.formName) {
            case "Deposit" -> DEPOSIT_HANDLERS.contains(handler) || DEPOSIT_SENDERS.contains(sender);
            case "CardMake" -> CARD_SENDERS.contains(sender) || "PersonInitButton_Click".equals(handler) && !"EmpIniButton".equals(sender);
            // RDaily : le bouton DailyReport (clôture) passe par SearchButton_Click
            case "RDaily" -> m == Mode.CONSULTATION && "DailyReport".equals(sender);
            default -> m == Mode.CONSULTATION && READ_ONLY_HANDLERS.getOrDefault(f.formName, Set.of()).contains(handler);
        };
        if (no) {
            LOG.info("{}.{} ({}) refusé au profil {}", f.formName, handler, sender, m);
            return refused(m);
        }
        return null;
    }

    /** Après chaque événement : boutons et onglets interdits masqués (le code d'origine peut les réafficher). */
    static void apply(SwForm f, boolean load) {
        if (outOfScope(f.formName)) {
            return;
        }
        Mode m = current();
        if (m == Mode.COMPLET) {
            return;
        }
        switch (f.formName) {
            case "Deposit" -> {
                DEPOSIT_PAGES.forEach(n -> f.c(n).Visible(false));
                DEPOSIT_SENDERS.forEach(n -> f.c(n).Visible(false));
                f.c("DepositAmount").ReadOnly(true);
                f.c("DiscountAmount").ReadOnly(true);
                if (f.c("BalanceChange").SelectedIndex() != 0) {
                    f.c("BalanceChange").SelectedIndex(0);
                }
            }
            case "CardMake" -> {
                CARD_PAGES.forEach(n -> f.c(n).Visible(false));
                CARD_SENDERS.forEach(n -> f.c(n).Visible(false));
                if (f.c("CardTool").SelectedIndex() != EMPLOYEE_PAGE) {
                    f.c("CardTool").SelectedIndex(EMPLOYEE_PAGE);
                    if (load) {
                        f.invoke("CardTool_SelectedIndexChanged");
                    }
                }
            }
            default -> {
                if (m == Mode.CONSULTATION) {
                    READ_ONLY_BUTTONS.getOrDefault(f.formName, List.of()).forEach(n -> f.c(n).Visible(false));
                }
            }
        }
    }

    // ------------------------------------------------------------ fenêtre principale

    /** Boutons de la barre d'outils et éléments de menu gérés par TypeSwitch. */
    public static final List<String> MAIN_ITEMS = List.of("OilPrice", "StationPrint", "CardMake", "CardUpDate", "CardLost", "CardChange",
        "Deposit", "TransactionSearch", "EmpManager", "Shift", "DailyReport", "MonthReport", "CardSearch", "ScoreDeal", "DisCount",
        "KeyManagement", "PSAMMake", "BusinessReport", "PasswodLost", "AuthorityManagement", "ShiftCancel", "Equipment",
        "TopSystemSeting", "BaseMessage", "SuperviseItem", "PortItem", "TankItem", "TankVolumeItem", "TankPortItem", "NozItem", "ShiftItem",
        "EmpLoginItem", "ExitItem",
        "TopStationManager", "ShiftReport", "DailyR", "MonthR", "TransactionR", "StockManager", "CardMessage",
        "TopCommunication", "GetOilTransaction", "Relieve",
        "ExtentTool", "AppItem", "RFIDItem", "PriceDisplay", "DisplayPay", "RegisiterValid", "VirtueScore", "CarNoManager", "CarNoVerify",
        "SurveillanceCamera", "OtherSet", "LogSet",
        "TopUserCheck", "UserList");

    /** Changer d'employé et Quitter restent toujours accessibles (menu Paramètres système). */
    private static final List<String> ALWAYS = List.of("TopSystemSeting", "EmpLoginItem", "ExitItem");

    private static final List<String> SAISIE_MAIN = List.of("CardMake", "CardUpDate", "CardLost", "CardChange", "Deposit", "CardSearch",
        "TransactionSearch", "TopStationManager", "TransactionR");

    private static final List<String> CONSULTATION_MAIN = List.of("Deposit", "CardSearch", "TransactionSearch", "Shift", "DailyReport",
        "MonthReport", "BusinessReport", "TopStationManager", "ShiftReport", "DailyR", "MonthR", "TransactionR");

    /**
     * Éléments visibles de la fenêtre principale pour un profil restreint : ceux du profil, limités à ses droits cochés
     * (visible = résultat de TypeSwitch), plus Changer d'employé / Quitter. Null pour un profil complet.
     */
    public static List<String> mainVisible(Mode m, List<String> visible) {
        if (m == Mode.COMPLET) {
            return null;
        }
        List<String> out = new ArrayList<>(ALWAYS);
        for (String n : m == Mode.SAISIE ? SAISIE_MAIN : CONSULTATION_MAIN) {
            if (n.startsWith("Top") || visible.contains(n)) {
                out.add(n);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ appels au Center, exports

    /** Appels qui modifient un solde : interdits aux deux profils. */
    private static final Set<String> MONEY_CALLS = Set.of("BalanceChange", "MasterGroupBalanceChange", "GroupBalanceChange",
        "OnlineDepositChange", "SaveOrTransfer", "CarBalanceUpdate", "DealBillRepay", "BalanceBack");

    /** Consultation : seules les lectures (Get…) sont permises. */
    private static boolean readCall(String name) {
        return name.contains("Get") || "CardScoreLog".equals(name);
    }

    /** Contrôle d'un appel au Center fait depuis une fenêtre (url = …/ServerDeal/Nom). */
    public static void checkCenterCall(String url) {
        if (!Boolean.TRUE.equals(UI.get()) || url == null) {
            return;
        }
        Mode m = current();
        if (m == Mode.COMPLET) {
            return;
        }
        String name = url.substring(url.lastIndexOf('/') + 1);
        if (MONEY_CALLS.contains(name) || m == Mode.CONSULTATION && !readCall(name)) {
            LOG.info("Appel {} refusé au profil {}", name, m);
            throw new com.ltstation.center.sw.web.SwException(refused(m));
        }
    }

    /** Consultation : ni export ni impression. */
    static void checkExport(SwForm f) {
        if (!outOfScope(f.formName) && current() == Mode.CONSULTATION) {
            throw new com.ltstation.center.sw.web.SwException(refused(Mode.CONSULTATION));
        }
    }

    static void enterUi() {
        UI.set(Boolean.TRUE);
    }

    static void leaveUi() {
        UI.remove();
    }

    // ------------------------------------------------------------ profils par défaut

    private static volatile boolean ensured;

    /** Crée les profils « Opérateur de saisie » et « Consultation » s'ils n'existent pas encore (postes déjà installés). */
    public static void ensureProfiles() {
        if (ensured) {
            return;
        }
        try {
            Sugar db = SW.db();
            List<Row> all = db.list("AuthorityMess");
            if (all.isEmpty()) {
                // Station pas encore dotée de profils : rien à compléter
                return;
            }
            boolean saisie = false;
            boolean consultation = false;
            int max = 0;
            for (Row r : all) {
                Mode m = of(r.s("AuthorityContent"));
                saisie |= m == Mode.SAISIE;
                consultation |= m == Mode.CONSULTATION;
                max = Math.max(max, r.i("User_IDMess"));
            }
            if (!saisie) {
                db.insert("AuthorityMess", Row.of("Name", "Opérateur de saisie", "User_IDMess", ++max,
                    "AuthorityContent", "2,4,7,10,13,16,17," + SAISIE + ","));
                LOG.info("Profil « Opérateur de saisie » créé (n° {})", max);
            }
            if (!consultation) {
                db.insert("AuthorityMess", Row.of("Name", "Consultation", "User_IDMess", ++max,
                    "AuthorityContent", "2,8,11,14,15,16,17," + CONSULTATION + ","));
                LOG.info("Profil « Consultation » créé (n° {})", max);
            }
            String allRights = "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,";
            List<Row> now = db.list("AuthorityMess");
            if (now.stream().noneMatch(r -> isGeneral(r.s("AuthorityContent")))) {
                // L'ancien profil « Administrateur » (tous les droits) devient l'administrateur général : ses employés le restent
                Row old = now.stream().filter(r -> of(r.s("AuthorityContent")) == Mode.COMPLET && !isAdmin(r.s("AuthorityContent"))
                    && java.util.Arrays.stream(r.s("AuthorityContent").split(",")).map(String::trim).filter(t -> !t.isEmpty())
                        .collect(java.util.stream.Collectors.toSet()).containsAll(java.util.Arrays.asList(allRights.split(","))))
                    .findFirst().orElse(null);
                if (old != null) {
                    saveProfile(old.get("User_IDMess"), "Administrateur général", old.s("AuthorityContent").replaceAll(",?$", ",") + ADMIN_GENERAL + ",");
                    LOG.info("Profil n° {} devenu « Administrateur général »", old.get("User_IDMess"));
                } else {
                    db.insert("AuthorityMess", Row.of("Name", "Administrateur général", "User_IDMess", ++max,
                        "AuthorityContent", allRights + ADMIN_GENERAL + ","));
                    LOG.info("Profil « Administrateur général » créé (n° {})", max);
                }
            }
            if (now.stream().noneMatch(r -> has(r.s("AuthorityContent"), ADMIN_APPLICATION))) {
                db.insert("AuthorityMess", Row.of("Name", "Administrateur application", "User_IDMess", ++max,
                    "AuthorityContent", allRights + ADMIN_APPLICATION + ","));
                LOG.info("Profil « Administrateur application » créé (n° {})", max);
            }
            ensured = true;
        } catch (RuntimeException e) {
            LOG.warn("Création des profils Opérateur de saisie / Consultation impossible : {}", e.getMessage());
        }
    }
}
