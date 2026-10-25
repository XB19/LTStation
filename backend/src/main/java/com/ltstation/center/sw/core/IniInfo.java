package com.ltstation.center.sw.core;

import com.ltstation.center.legacy.Conv;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * IniInfo du logiciel Station : réglages de StationSoftware.exe.Config (appSettings) et état
 * global de la session (employé connecté, station, poste courant…).
 *
 * <p>Les réglages sont lus dans le fichier {@link #FILE} (équivalent de l'exe.config d'origine),
 * créé avec les valeurs d'origine s'il est absent ; {@link #setConfigValue} les modifie comme
 * BaseFunction.SetConfigValue.
 */
public final class IniInfo {

    /** Fichier de réglages (hors git : il peut contenir des clés de services tiers). */
    public static volatile Path FILE = Path.of("station", "StationSoftware.properties");

    /** Valeurs d'origine de StationSoftware.exe.Config (les clés de services tiers sont laissées vides). */
    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("SystemType", "0");
        DEFAULTS.put("NormalType", "0");
        DEFAULTS.put("MoneyFormat", "2");
        DEFAULTS.put("VolumeFormat", "2");
        DEFAULTS.put("PriceFormat", "2");
        DEFAULTS.put("ReportType", "0");
        DEFAULTS.put("Center", "http://localhost:8080/");
        DEFAULTS.put("TransactionType", "1");
        DEFAULTS.put("SupervisionType", "0");
        DEFAULTS.put("DigitsType", "1");
        DEFAULTS.put("NonLevel", "false");
        DEFAULTS.put("ScoreDeal", "0");
        DEFAULTS.put("CreditPayAdvance", "1");
        DEFAULTS.put("SettleTime", "00:00:00");
        DEFAULTS.put("AuthShift", "0");
        DEFAULTS.put("USN", "");
        DEFAULTS.put("UNITID", "");
        DEFAULTS.put("ServerIP", "127.0.0.1");
        DEFAULTS.put("Port", "7777");
        DEFAULTS.put("ExtendUrl", "");
        DEFAULTS.put("SmsClientId", "");
        DEFAULTS.put("SmsClientSecret", "");
        DEFAULTS.put("SmsToken", "");
        DEFAULTS.put("CardConstKey", "");
        DEFAULTS.put("CardConstTempKey", "");
        DEFAULTS.put("clientKey", "");
        DEFAULTS.put("clientSecret", "");
        DEFAULTS.put("uid", "");
        DEFAULTS.put("FoRanUrl", "");
        DEFAULTS.put("urlIP", "");
        DEFAULTS.put("Camera", "0");
        DEFAULTS.put("SendMessage", "1");
        DEFAULTS.put("Speed", "10");
        DEFAULTS.put("OverCount", "5");
        DEFAULTS.put("NozRunTime", "1000");
        DEFAULTS.put("NormalNeedDuty", "0");
        DEFAULTS.put("HandReportType", "1");
        DEFAULTS.put("HandReportTitle", "");
        DEFAULTS.put("WifiMode", "0");
        DEFAULTS.put("LogWrite", "0");
        DEFAULTS.put("Auto", "0");
        DEFAULTS.put("HandShiftPassword", "1");
        DEFAULTS.put("Language", "fr-FR");
    }

    private static final Properties PROPS = new Properties();

    // ------------------------------------------------------------ réglages (appSettings)
    public static String MoneyFormat = "0.00";
    public static String VolumeFormat = "0.00";
    public static String PriceFormat = "0.00";
    public static String Center = "";
    public static int SupervisionType;
    public static int Port;
    public static String USN = "";
    public static String UNITID = "";
    public static String ServerIP = "";
    public static int TransactionType;
    public static int SystemType;
    public static int NormalType;
    public static int DigitsType;
    public static boolean NonLevel;
    public static int ScoreDeal;
    public static String SettleTime = "08:00:00";
    public static boolean AuthShift;
    public static boolean CreditPayAdvance;
    public static int ReportType;
    public static String ExtendUrlRequest = "";
    public static String platform = "0000";
    public static String FORanIP = "";
    public static int CameraSet = -1;
    public static int SendMessage = -1;
    public static int SpeedRun = -1;
    public static int OverCount = 5;
    public static int NozRunTime = 1000;
    public static int NormalNeedDuty;
    public static int HandReportType;
    public static String HandReportTitle = "";
    public static int WifiMode;
    public static int Auto;
    public static int HandShiftPassword;
    public static String FoRanUrl = "";
    /** Service SMS des notifications de transaction (Nozzle.SendNote) : identifiants hors du code. */
    public static String SmsClientId = "";
    public static String SmsClientSecret = "";
    public static String SmsToken = "";
    public static String clientKey = "";
    public static String clientSecret = "";
    public static String uid = "";

    // ------------------------------------------------------------ état de la session
    public static volatile boolean ThreadStop;
    public static String Auth = "";
    public static int MFormat;
    public static int VFormat;
    public static int PFormat;
    public static int Mradio = 1;
    public static int Vradio = 1;
    public static int Pradio = 1;
    public static int IsOnDuty;
    public static int BoxShift;
    public static int Delay = 80;
    public static String FoRanToken = "";
    public static long FoRanOutTime;
    public static boolean VIDDO;
    public static int logwrite;
    public static boolean cyDowloadFlag;
    public static int TimeZone;
    public static boolean vatRun;
    public static String Code = "";
    public static int LoginID;
    public static String LoginName = "";
    public static String LoginPassword = "";
    public static int StationID;
    public static String StationName = "";
    public static String StationAddress = "";
    public static int LoginType;
    public static int IdentifyMode;
    public static int DayCompletedTime;
    public static String token = "";
    public static LocalDateTime TokenTime = Conv.MIN_DATE;
    public static int SpecialInt;
    public static int CompanyID;
    public static String Language = "fr-FR";
    public static List<Object> tankShow;
    public static String CurrentShift = "";
    public static String BoxCode = "";

    private IniInfo() {
    }

    /** Valeur brute d'un réglage (ConfigurationManager.AppSettings[key]). */
    public static synchronized String setting(String key) {
        return PROPS.getProperty(key, DEFAULTS.get(key));
    }

    /** Charge le fichier de réglages (le crée avec les valeurs d'origine s'il n'existe pas). */
    public static synchronized void load() {
        PROPS.clear();
        try {
            if (Files.exists(FILE)) {
                try (InputStream in = Files.newInputStream(FILE)) {
                    PROPS.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            boolean changed = false;
            for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
                if (!PROPS.containsKey(e.getKey())) {
                    PROPS.setProperty(e.getKey(), e.getValue());
                    changed = true;
                }
            }
            if (changed) {
                save();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        apply();
    }

    private static void save() throws IOException {
        Files.createDirectories(FILE.toAbsolutePath().getParent());
        try (OutputStream out = Files.newOutputStream(FILE)) {
            Properties sorted = new Properties() {
                @Override
                public synchronized java.util.Enumeration<Object> keys() {
                    return java.util.Collections.enumeration(new java.util.TreeSet<>(super.keySet()));
                }
            };
            sorted.putAll(PROPS);
            sorted.store(new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8),
                "Réglages du logiciel Station (StationSoftware.exe.Config d'origine)");
        }
    }

    /** BaseFunction.SetConfigValue : modifie un réglage et l'enregistre. */
    public static synchronized boolean setConfigValue(String key, String value) {
        try {
            PROPS.setProperty(key, value == null ? "" : value);
            save();
            apply();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static synchronized Map<String, String> settings() {
        Map<String, String> m = new LinkedHashMap<>();
        PROPS.stringPropertyNames().stream().sorted().forEach(k -> m.put(k, PROPS.getProperty(k)));
        return m;
    }

    private static int i(String k, int def) {
        String v = setting(k);
        try {
            return v == null || v.isBlank() ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Valeurs initialisées comme les champs statiques de IniInfo et SetIniInfo (Program.cs). */
    private static void apply() {
        Center = setting("Center");
        SupervisionType = i("SupervisionType", 0);
        Port = i("Port", 0);
        USN = setting("USN");
        UNITID = setting("UNITID");
        ServerIP = setting("ServerIP");
        TransactionType = i("TransactionType", 0);
        SystemType = i("SystemType", 0);
        NormalType = i("NormalType", 0);
        DigitsType = i("DigitsType", 0);
        NonLevel = Boolean.parseBoolean(setting("NonLevel"));
        ScoreDeal = i("ScoreDeal", 0);
        SettleTime = setting("SettleTime") == null ? "08:00:00" : setting("SettleTime");
        AuthShift = i("AuthShift", 0) != 0;
        CreditPayAdvance = i("CreditPayAdvance", 0) != 0;
        ReportType = i("ReportType", 0);
        ExtendUrlRequest = setting("ExtendUrl") == null ? "" : setting("ExtendUrl");
        FORanIP = setting("urlIP") == null ? "" : setting("urlIP");
        CameraSet = i("Camera", -1);
        SendMessage = i("SendMessage", -1);
        SpeedRun = i("Speed", -1);
        // L'original lit « Speed » pour OverCount quand la clé existe : comportement conservé.
        OverCount = setting("OverCount") == null ? 5 : i("Speed", 5);
        NozRunTime = i("NozRunTime", 1000);
        NormalNeedDuty = i("NormalNeedDuty", 0);
        HandReportType = i("HandReportType", 0);
        HandReportTitle = setting("HandReportTitle") == null ? "" : setting("HandReportTitle");
        WifiMode = i("WifiMode", 0);
        Auto = i("Auto", 0);
        HandShiftPassword = i("HandShiftPassword", 0);
        FoRanUrl = setting("FoRanUrl");
        SmsClientId = setting("SmsClientId");
        SmsClientSecret = setting("SmsClientSecret");
        SmsToken = setting("SmsToken");
        BaseFunction.CONST_KEY = setting("CardConstKey");
        BaseFunction.CONST_TEMPKEY = setting("CardConstTempKey");
        clientKey = setting("clientKey");
        clientSecret = setting("clientSecret");
        uid = setting("uid");
        Language = setting("Language") == null ? "fr-FR" : setting("Language");
        Mradio = i("MoneyFormat", 2);
        int[] mf = formatOf(Mradio);
        MFormat = mf[0];
        MoneyFormat = pattern(mf[1]);
        Vradio = i("VolumeFormat", 2);
        int[] vf = formatOf(Vradio);
        VFormat = vf[0];
        VolumeFormat = pattern(vf[1]);
        Pradio = i("PriceFormat", 2);
        int[] pf = formatOf(Pradio);
        PFormat = pf[0];
        PriceFormat = pattern(pf[1]);
        MultiLanguage.languageChange(Language);
    }

    /** Facteur et nombre de décimales pour un réglage 0..3 (2 par défaut). */
    private static int[] formatOf(int radio) {
        return switch (radio) {
            case 0 -> new int[] {1, 0};
            case 1 -> new int[] {10, 1};
            case 3 -> new int[] {1000, 3};
            default -> new int[] {100, 2};
        };
    }

    private static String pattern(int decimals) {
        return decimals == 0 ? "0" : "0." + "0".repeat(decimals);
    }
}
