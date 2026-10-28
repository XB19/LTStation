package com.ltstation.center.sw.normal;

import static com.ltstation.center.legacy.Sugar.p;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.hw.FrxReport;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;

/**
 * ServerDeal.BoxBase : EFD (TRA, Tanzanie : reçus et rapports Z signés au certificat PFX), eTIMS (Kenya, via
 * {@link KenyaDeal}), factures de l'écran de caisse et tickets FastReport (caisse, poste, jour, prix, compteurs).
 */
public final class BoxBase {

    private BoxBase() {
    }

    private static final LegacyLog BaseLog = new LegacyLog("BoxBase");

    public static final KenyaDeal kenyaDeal = new KenyaDeal();
    public static volatile String EFDPFXNumber = "";
    public static volatile String UserName = "";
    public static volatile String Password = "";
    public static volatile String EFDToken = "";
    public static volatile LocalDateTime EFDTokenTime = LocalDateTime.MIN;
    public static volatile EFDMSRESP sp = new EFDMSRESP();
    public static volatile long EFDGC;
    public static volatile StationSet sc = new StationSet();
    public static final Object transactionDealFlag = NormalFunction.transactionDealFlag;
    public static final Object zreportDeal = new Object();
    public static volatile boolean EFDDealRun;
    public static volatile boolean EtimDealRun;

    /** Dossier des certificats PFX de l'EFD (le plus récent est utilisé). */
    public static Path PfxDir = Path.of("station", "OtherFile", "pfx");

    private static Sugar db() {
        return SW.db();
    }

    /** DataBaseDeal.EFDMS.EFDMSRESP : réponse d'enregistrement de l'EFD (entête des reçus). */
    public static class EFDMSRESP {
        public int ACKCODE;
        public String ACKMSG = "";
        public String REGID = "";
        public String SERIAL = "";
        public String UIN = "";
        public long TIN;
        public String VRN = "";
        public String MOBILE = "";
        public String STREET = "";
        public String CITY = "";
        public String ADDRESS = "";
        public String COUNTRY = "";
        public String NAME = "";
        public String RECEIPTCODE = "";
        public String REGION = "";
        public long GC;
        public String TAXOFFICE = "";
        public String USERNAME = "";
        public String PASSWORD = "";
        public String TOKENPATH = "";
    }

    /** DataBaseDeal.EFDMS.StationSet : en-tête des tickets de caisse. */
    public static class StationSet {
        public String Tel = "";
        public String Branch = "";
        public String StationCompany = "";
        public String StationTax = "";
    }

    // ------------------------------------------------------------ XML (XElement)

    /** Élément XML minimal écrit comme XElement.ToString() (indentation de deux espaces). */
    public static final class X {
        final String name;
        final String text;
        final List<X> children = new ArrayList<>();

        public X(String name, Object text) {
            this.name = name;
            this.text = text == null ? null : text instanceof BigDecimal d ? d.toPlainString() : text.toString();
        }

        public X(String name, X... kids) {
            this(name, (Object) null);
            children.addAll(List.of(kids));
        }

        public X add(X x) {
            children.add(x);
            return this;
        }

        private static String esc(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        /** ToString(SaveOptions.DisableFormatting) */
        public String compact() {
            StringBuilder sb = new StringBuilder();
            write(sb, -1);
            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            write(sb, 0);
            return sb.toString();
        }

        private void write(StringBuilder sb, int indent) {
            String pad = indent < 0 ? "" : "  ".repeat(indent);
            sb.append(pad).append('<').append(name).append('>');
            if (children.isEmpty()) {
                sb.append(text == null ? "" : esc(text)).append("</").append(name).append('>');
                return;
            }
            for (X c : children) {
                if (indent >= 0) {
                    sb.append("\r\n");
                }
                c.write(sb, indent < 0 ? -1 : indent + 1);
            }
            if (indent >= 0) {
                sb.append("\r\n").append(pad);
            }
            sb.append("</").append(name).append('>');
        }
    }

    /** Copie des éléments enfants d'un XML vers les champs publics de même nom (XElement.ToEntity). */
    static <T> T ToEntity(org.w3c.dom.Element element, T target) {
        for (Field f : target.getClass().getFields()) {
            org.w3c.dom.NodeList nl = element.getElementsByTagName(f.getName());
            if (nl.getLength() == 0 || nl.item(0).getParentNode() != element) {
                continue;
            }
            String v = nl.item(0).getTextContent();
            try {
                if (f.getType() == int.class) {
                    f.setInt(target, Integer.parseInt(v.trim()));
                } else if (f.getType() == long.class) {
                    f.setLong(target, Long.parseLong(v.trim()));
                } else if (f.getType() == String.class) {
                    f.set(target, v);
                }
            } catch (ReflectiveOperationException | NumberFormatException ignored) {
                // champ ignoré
            }
        }
        return target;
    }

    // ------------------------------------------------------------ certificat et signature

    /** Certificat PFX le plus récent de OtherFile/pfx (mot de passe : réglage EFDPfxPassword). */
    private static KeyStore GetCertificate2() {
        try {
            if (!Files.isDirectory(PfxDir)) {
                throw new IllegalStateException("File Lost");
            }
            Path file;
            try (var s = Files.list(PfxDir)) {
                file = s.max(Comparator.comparing(f -> f.toFile().lastModified())).orElseThrow(() -> new IllegalStateException("File Empty"));
            }
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(file)) {
                ks.load(in, IniInfo.EFDPfxPassword.toCharArray());
            }
            return ks;
        } catch (Exception e) {
            return null;
        }
    }

    private static String alias(KeyStore ks) throws Exception {
        Enumeration<String> a = ks.aliases();
        while (a.hasMoreElements()) {
            String n = a.nextElement();
            if (ks.isKeyEntry(n)) {
                return n;
            }
        }
        return ks.aliases().nextElement();
    }

    /** SignDataShaWithRsa : signature SHA-1 / RSA PKCS#1 en Base64. */
    public static String SignDataShaWithRsa(String data) {
        try {
            KeyStore ks = GetCertificate2();
            if (ks == null) {
                throw new IllegalStateException("pfx Lost");
            }
            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initSign((PrivateKey) ks.getKey(alias(ks), IniInfo.EFDPfxPassword.toCharArray()));
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception ex) {
            BaseLog.writeLog("SignDataShaWithRsa Error:" + ex.getMessage());
            return null;
        }
    }

    /** ReadPFXNumber : numéro de série du certificat (hexadécimal, comme X509Certificate2.SerialNumber). */
    public static String ReadPFXNumber() {
        try {
            KeyStore ks = GetCertificate2();
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias(ks));
            return HexFormat.of().withUpperCase().formatHex(cert.getSerialNumber().toByteArray());
        } catch (Exception ex) {
            BaseLog.writeLog("ReadPFXNumber:" + ex.getMessage());
            return "";
        }
    }

    // ------------------------------------------------------------ HTTP

    /** L'original accepte tous les certificats du serveur de la TRA. */
    private static final HttpClient TRUST_ALL;

    static {
        HttpClient c;
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] {new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, null);
            c = HttpClient.newBuilder().sslContext(ctx).connectTimeout(Duration.ofSeconds(15)).build();
        } catch (Exception e) {
            c = HttpClient.newHttpClient();
        }
        TRUST_ALL = c;
    }

    /** XMLHttp : &lt;EFDMS&gt;élément + EFDMSSIGNATURE&lt;/EFDMS&gt; envoyé ; renvoie la réponse ou null (erreur dans Error[0]). */
    public static String XMLHttp(String url, X element, String[] Error, Map<String, String> header) {
        Error[0] = "";
        try {
            String text = new X("EFDMS", element, new X("EFDMSSIGNATURE", SignDataShaWithRsa(element.compact()))).toString();
            BaseLog.writeLog("Send:" + text);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url.trim())).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/xml;charset=utf-8").POST(HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8));
            if (header != null) {
                header.forEach(b::header);
            }
            HttpResponse<String> r = TRUST_ALL.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() >= 400) {
                throw new IllegalStateException("The remote server returned an error: (" + r.statusCode() + ")");
            }
            return r.body();
        } catch (Exception ex) {
            Error[0] = ex.getMessage();
            return null;
        }
    }

    /** Jeton OAuth de la TRA (renouvelé après 50 minutes). */
    public static String GetEFDToken() {
        try {
            if (EFDTokenTime.isAfter(LocalDateTime.now())) {
                return EFDToken;
            }
            Map<String, String> dics = new LinkedHashMap<>();
            dics.put("Username", UserName);
            dics.put("Password", Password);
            dics.put("grant_type", "password");
            String body = dics.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
            HttpResponse<String> r = TRUST_ALL.send(HttpRequest.newBuilder(URI.create("https://vfdtest.tra.go.tz/vfdtoken"))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            EFDToken = Wire.parseRow(r.body()).str("access_token");
            EFDTokenTime = LocalDateTime.now().plusMinutes(50);
            return EFDToken;
        } catch (Exception e) {
            return EFDToken;
        }
    }

    private static Map<String, String> efdHeader(String routingKey) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Routing-Key", routingKey);
        h.put("Cert-Serial", EFDPFXNumber);
        h.put("Authorization", "bearer " + GetEFDToken());
        return h;
    }

    /**
     * NormalMainForm.EFDRegistration : enregistrement de l'EFD auprès de la TRA (TIN et clé de Supervision type 3) ;
     * la réponse remplit l'en-tête des reçus (sp) et l'identifiant du jeton.
     */
    public static boolean EFDRegistration() {
        try {
            Row sup = db().first("Supervision", "{SupervisionType} = 3", null);
            Map<String, String> header = new LinkedHashMap<>();
            header.put("Cert-Serial", EFDPFXNumber);
            header.put("Client", "webapi");
            X reg = new X("REGDATA", new X("TIN", sup.str("UserName")), new X("CERTKEY", sup.str("Password")));
            BaseLog.writeLog("EFD Registration Send:" + reg);
            String[] error = {""};
            String out = XMLHttp("https://vfdtest.tra.go.tz/api/vfdregreq", reg, error, header);
            BaseLog.writeLog("EFD Registration Receive:" + out);
            BaseLog.writeLog("EFD Registration Receive Error:" + error[0]);
            if (out == null) {
                return false;
            }
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            org.w3c.dom.Element root = f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(out.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
            Node first = root.getFirstChild();
            while (first != null && !(first instanceof org.w3c.dom.Element)) {
                first = first.getNextSibling();
            }
            EFDMSRESP resp = ToEntity((org.w3c.dom.Element) first, new EFDMSRESP());
            UserName = resp.USERNAME;
            Password = resp.PASSWORD;
            EFDGC = resp.GC;
            resp.UIN = resp.UIN.replace("-", "_");
            Row cfg = db().first("StationConfig");
            if (cfg != null) {
                resp.NAME = cfg.str("StationName");
                resp.CITY = cfg.str("StationAddress");
            }
            sp = resp;
            return true;
        } catch (Exception ex) {
            BaseLog.writeLog("EFD Registration Error:" + ex.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------ EFD : reçus et rapports Z

    private static BigDecimal r(BigDecimal d, int n) {
        return Conv.netRound(d, n);
    }

    /** DealTransactionSp : chaque transaction non déclarée reçoit ses compteurs DC / GC, est envoyée à la TRA et imprimée. */
    public static void DealTransactionSp() {
        while (EFDDealRun) {
            try {
                synchronized (transactionDealFlag) {
                    Row t = db().first("NormalTransaction", "COALESCE({ApiSend}, false) = false ORDER BY {CreateTime}", null);
                    if (t != null) {
                        LocalDateTime created = BaseFunction.StampToDateTime(t.str("CreateTime"));
                        if (t.l("DC") == 0L) {
                            long s = BaseFunction.ConvertDateTimeToInt(created.toLocalDate().atStartOfDay());
                            long e = BaseFunction.ConvertDateTimeToInt(created.toLocalDate().plusDays(1).atStartOfDay());
                            Long max = db().scalar(db().sql("NormalTransaction", "select MAX({DC}) from [NormalTransaction] where {CreateTime} >= :s"
                                + " and {CreateTime} <= :e and {DC} <> 0"), p("s", s, "e", e), Long.class);
                            t.put("DC", (max == null ? 0 : max) + 1);
                        }
                        if (t.l("GC") == 0L) {
                            Long max = db().scalar(db().sql("NormalTransaction", "select MAX({GC}) from [NormalTransaction] where {GC} <> 0"), null, Long.class);
                            long gc = (max == null ? 0 : max) + 1;
                            if (gc <= EFDGC) {
                                gc = EFDGC;
                            }
                            t.put("GC", gc);
                        }
                        if (EFDTransactionSedn(t)) {
                            t.put("ApiSend", true);
                        }
                        db().update("NormalTransaction", t);
                        EFDTransactionPrint(sp.RECEIPTCODE + t.l("GC"), created.toLocalDate().toString(), created.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            t.str("GC"), t.str("DC"), created.format(DateTimeFormatter.BASIC_ISO_DATE), t.str("NozID"), t.str("GradeName"),
                            t.dec("Price").toPlainString(), t.dec("Volume").toPlainString(), t.dec("Amount").toPlainString(), "0", "N/A", "NIL", "N/A");
                    }
                }
                synchronized (zreportDeal) {
                    Row z = db().first("EFDZReport", "{ApiSend} = false ORDER BY {CreateTime}", null);
                    if (z != null && EFDZReportSend(z)) {
                        z.put("ApiSend", true);
                        db().update("EFDZReport", z);
                        LocalDateTime created = BaseFunction.StampToDateTime(z.str("CreateTime"));
                        String[] error = {""};
                        boolean ok = EFDReportPrint(created.toLocalDate().toString(), "23:59:59", z.str("ZNUMBER"),
                            created.toLocalDate().minusDays(1) + " 23:59:59", z.dec("DAILYTOTALAMOUNT").toPlainString(), z.str("FirstReceipt"),
                            z.str("LastReceipt"), z.str("TICKETSFISCAL"), z.dec("GROSS").toPlainString(), z.dec("UNLEADED").toPlainString(),
                            z.dec("UNLEADEDAmount").toPlainString(), z.dec("DIESEL").toPlainString(), z.dec("DIESELAmount").toPlainString(),
                            z.dec("KEROSENE").toPlainString(), z.dec("KEROSENEAmount").toPlainString(), error);
                        BaseLog.writeLog("EFD Report deal:" + (ok ? "True" : "False") + " EFD Report Error:" + error[0]);
                    }
                }
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                BaseLog.writeLog("EFD Thread Error:" + ex.getMessage());
            }
        }
    }

    private static boolean EFDTransactionSedn(Row t) {
        try {
            LocalDateTime created = BaseFunction.StampToDateTime(t.str("CreateTime"));
            BigDecimal amount = r(t.dec("Amount"), IniInfo.Mradio);
            X rct = new X("RCT",
                new X("DATE", created.toLocalDate().toString()),
                new X("TIME", created.format(DateTimeFormatter.ofPattern("HH:mm:ss"))),
                new X("TIN", sp.TIN),
                new X("REGID", sp.REGID),
                new X("EFDSERIAL", sp.SERIAL),
                new X("CUSTIDTYPE", 6),
                new X("CUSTID", "NIL"),
                new X("CUSTNAME", sp.USERNAME),
                new X("MOBILENUM", 716731205L),
                new X("RCTNUM", t.l("GC")),
                new X("DC", t.l("DC")),
                new X("GC", t.l("GC")),
                new X("ZNUM", created.format(DateTimeFormatter.BASIC_ISO_DATE)),
                new X("RCTVNUM", sp.RECEIPTCODE + t.l("GC")),
                new X("ITEMS", new X("ITEM", new X("ID", t.i("ID")), new X("DESC", t.str("GradeName")), new X("QTY", r(t.dec("Volume"), IniInfo.Vradio)),
                    new X("TAXCODE", 5), new X("AMT", amount))),
                new X("TOTALS", new X("TOTALTAXEXCL", amount), new X("TOTALTAXINCL", amount), new X("DISCOUNT", 0)),
                new X("PAYMENTS", new X("PMTTYPE", "CASH"), new X("PMTAMOUNT", amount)),
                new X("VATTOTALS", new X("VATRATE", "0"), new X("NETTAMOUNT", amount), new X("TAXAMOUNT", 0)));
            BaseLog.writeLog("EFD Send");
            String[] error = {""};
            String out = XMLHttp("https://vfdtest.tra.go.tz/api/efdmsrctinfo ", rct, error, efdHeader("vfdrct"));
            BaseLog.writeLog("EFD Receive:" + (out == null ? "" : out));
            return out != null;
        } catch (RuntimeException ex) {
            BaseLog.writeLog("EFD Error:" + ex);
            return false;
        }
    }

    /** DealEFDZReport : rapport Z de chaque journée écoulée (et de la journée en cours après 23:59). */
    public static void DealEFDZReport() {
        while (EFDDealRun) {
            try {
                Integer max = db().scalar(db().sql("EFDZReport", "select MAX({ZNUMBER}) from [EFDZReport]"), null, Integer.class);
                int today = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
                int num = max == null || max == 0 ? today
                    : Integer.parseInt(LocalDate.parse(String.valueOf(max), DateTimeFormatter.BASIC_ISO_DATE).plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE));
                if (today > num) {
                    CreateEFDZReport(LocalDate.parse(String.valueOf(num), DateTimeFormatter.BASIC_ISO_DATE));
                } else if (LocalDateTime.now().isAfter(LocalDate.now().atTime(23, 59))) {
                    CreateEFDZReport(LocalDate.now());
                }
            } catch (RuntimeException ex) {
                BaseLog.writeLog("EFD Report Error:" + ex.getMessage());
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static boolean CreateEFDZReport(LocalDate day) {
        try {
            synchronized (zreportDeal) {
                int znumber = Integer.parseInt(day.format(DateTimeFormatter.BASIC_ISO_DATE));
                if (db().any("EFDZReport", "{ZNUMBER} = :z", p("z", znumber))) {
                    throw new IllegalStateException("Daily already done");
                }
                Row last = db().first("EFDZReport", "ORDER BY {ID} DESC", null);
                BigDecimal gross = last == null ? BigDecimal.ZERO : last.dec("GROSS");
                long s = BaseFunction.ConvertDateTimeToInt(day.atStartOfDay());
                long e = BaseFunction.ConvertDateTimeToInt(day.atTime(23, 59));
                String range = "{CreateTime} >= :s AND {CreateTime} <= :e";
                Map<String, Object> ps = p("s", s, "e", e);
                Row tot = db().queryFirst(db().sql("NormalTransaction", "select COALESCE(SUM({Amount}), 0) AS A, COUNT(*) AS N from [NormalTransaction] where "
                    + range), ps);
                Row first = db().first("NormalTransaction", range + " ORDER BY {CreateTime}", ps);
                Row lastT = db().first("NormalTransaction", range + " ORDER BY {CreateTime} DESC", ps);
                BigDecimal[] v = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                for (Row g : db().query(db().sql("NormalTransaction", "select {GradeName} AS GradeName, SUM({Amount}) AS SumAmount, SUM({Volume}) AS SumVolume"
                    + " from [NormalTransaction] where " + range + " group by {GradeName}"), ps)) {
                    String name = g.str("GradeName");
                    if (name == null) {
                        continue;
                    }
                    int k = switch (name.toUpperCase().trim()) {
                        case "UNLEADED" -> 0;
                        case "DIESEL" -> 2;
                        case "KEROSENE" -> 4;
                        default -> -1;
                    };
                    if (k >= 0) {
                        v[k] = g.dec("SumVolume");
                        v[k + 1] = g.dec("SumAmount");
                    }
                }
                db().insert("EFDZReport", Row.of("ZNUMBER", znumber, "DAILYTOTALAMOUNT", tot.dec("A"), "GROSS", gross.add(tot.dec("A")),
                    "TICKETSFISCAL", tot.i("N"), "StartTime", s, "EndTime", e, "CreateTime", BaseFunction.ConvertDateTimeToInt(), "ApiSend", false,
                    "FirstReceipt", first == null ? day.atStartOfDay().format(Conv.STD) : BaseFunction.StampToDateTime(first.str("CreateTime")).format(Conv.STD),
                    "LastReceipt", lastT == null ? day.atTime(23, 59).format(Conv.STD) : BaseFunction.StampToDateTime(lastT.str("CreateTime")).format(Conv.STD),
                    "UNLEADED", v[0], "UNLEADEDAmount", v[1], "DIESEL", v[2], "DIESELAmount", v[3], "KEROSENE", v[4], "KEROSENEAmount", v[5]));
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean EFDZReportSend(Row report) {
        try {
            BigDecimal total = report.dec("DAILYTOTALAMOUNT");
            X z = new X("ZREPORT",
                new X("DATE", BaseFunction.StampToDateTime(report.str("CreateTime")).toLocalDate().toString()),
                new X("TIME", "23:59:00"),
                new X("HEADER", new X("LINE", sp.NAME), new X("LINE", sp.STREET), new X("LINE", "MOBILE:" + sp.MOBILE), new X("LINE", sp.CITY)),
                new X("VRN", sp.VRN),
                new X("TIN", sp.TIN),
                new X("TAXOFFICE", sp.TAXOFFICE),
                new X("REGID", sp.REGID),
                new X("ZNUMBER", report.i("ZNUMBER")),
                new X("EFDSERIAL", sp.SERIAL),
                new X("REGISTRATIONDATE", "2024-04-29"),
                new X("USER", sp.UIN),
                new X("SIMIMSI", "WEBAPI"),
                new X("TOTALS", new X("DAILYTOTALAMOUNT", total), new X("GROSS", report.dec("GROSS")), new X("CORRECTIONS", "0.00"),
                    new X("DISCOUNTS", "0.00"), new X("SURCHARGES", "0.00"), new X("TICKETSVOID", 0), new X("TICKETSVOIDTOTAL", "0.00"),
                    new X("TICKETSFISCAL", report.i("TICKETSFISCAL")), new X("TICKETSNONFISCAL", 0)));
            X vat = new X("VATTOTALS");
            for (String rate : new String[] {"A-18.00", "B-0.00", "C-0.00", "D-0.00", "E-0.00"}) {
                vat.add(new X("VATRATE", rate)).add(new X("NETTAMOUNT", rate.startsWith("E") ? total.toPlainString() : "0.00")).add(new X("TAXAMOUNT", "0.00"));
            }
            z.add(vat);
            X pay = new X("PAYMENTS");
            for (String type : new String[] {"CASH", "CHEQUE", "CCARD", "EMONEY", "INVOICE"}) {
                pay.add(new X("PMTTYPE", type)).add(new X("PMTAMOUNT", "CASH".equals(type) ? total.toPlainString() : "0.00"));
            }
            z.add(pay);
            z.add(new X("CHANGES", new X("VATCHANGENUM", "0"), new X("HEADCHANGENUM", "0")));
            z.add(new X("ERRORS", ""));
            z.add(new X("FWVERSION", "3.0"));
            z.add(new X("FWCHECKSUM", "WEBAPI"));
            BaseLog.writeLog("ZREPORT Send");
            String[] error = {""};
            String out = XMLHttp("https://vfdtest.tra.go.tz/api/efdmszreport", z, error, efdHeader("vfdzreport"));
            BaseLog.writeLog("ZREPORT Receive:" + (out == null ? "" : out));
            BaseLog.writeLog("ZREPORT Error:" + error[0]);
            return out != null;
        } catch (RuntimeException ex) {
            BaseLog.writeLog("ZREPORT Ex:" + ex.getMessage());
            return false;
        }
    }

    /** Reçu EFD (EFDTransaction.frx) ; un client TIN en attente sur ce pistolet (TanSanCustomerPrint) y est repris. */
    public static void EFDTransactionPrint(String RCTVNUM, String transactionDate, String transactiontime, String GC, String DC, String ZNumber,
        String NozID, String Product, String Price, String Volume, String Amount, String Discount, String CustomerName, String CustomerIDType,
        String CustID) {
        try {
            FrxReport report = new FrxReport().Load("EFDTransaction.frx");
            Row tin = db().first("TanSanCustomerPrint", "{NozID} = :n AND {IsPrint} = false ORDER BY {CreateTime} DESC", p("n", Conv.toInt(NozID)));
            if (tin != null) {
                CustomerName = tin.str("CustomerName");
                CustID = tin.str("CustomerTIN");
                CustomerIDType = "TIN";
                tin.put("IsPrint", true);
                db().update("TanSanCustomerPrint", tin);
            }
            report.SetParameterValue("StationName", sp.NAME).SetParameterValue("Tin", sp.TIN).SetParameterValue("VRN", sp.VRN)
                .SetParameterValue("SERIAL", sp.SERIAL).SetParameterValue("UIN", sp.UIN).SetParameterValue("TAXOFFICE", sp.TAXOFFICE)
                .SetParameterValue("Location", sp.CITY).SetParameterValue("CustName", CustomerName).SetParameterValue("CustomerIDType", CustomerIDType)
                .SetParameterValue("CustID", CustID).SetParameterValue("RECEIPTNUMBER", GC).SetParameterValue("ZNO", DC + "/" + ZNumber)
                .SetParameterValue("RTDATE", transactionDate).SetParameterValue("TIME", transactiontime).SetParameterValue("NozID", NozID)
                .SetParameterValue("Product", Product == null ? "" : Product.trim())
                .SetParameterValue("Price", r(new BigDecimal(Price), IniInfo.Pradio)).SetParameterValue("Volume", r(new BigDecimal(Volume), IniInfo.Vradio))
                .SetParameterValue("CASH", r(new BigDecimal(Amount), IniInfo.Mradio)).SetParameterValue("Discount", Discount)
                .SetParameterValue("RCTVNUM", RCTVNUM)
                .SetParameterValue("BarImage", "https://virtual.tra.go.tz/efdmsrctverify/" + RCTVNUM + "_" + transactiontime.replace(":", ""));
            report.Print();
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** ToString("N0") : séparateurs de milliers, sans décimale. */
    private static String n0(String v, int digits) {
        return String.format(java.util.Locale.US, "%,.0f", r(new BigDecimal(v), digits));
    }

    private static String n2(String v, int digits) {
        return String.format(java.util.Locale.US, "%,.2f", r(new BigDecimal(v), digits));
    }

    /** Rapport Z EFD (EFDZReport.frx). */
    public static boolean EFDReportPrint(String RTDATE, String TIME, String ZREPORTNUMBER, String RealTime, String MONEY, String FIRSTRECEIPT,
        String LASTRECEIPT, String RECEIPTISSUED, String GROSSEX, String UNLEADED, String UNLEADEDAmount, String DIESEL, String DIESELAmount,
        String KEROSENE, String KEROSENEAmount, String[] Error) {
        Error[0] = "";
        try {
            FrxReport report = new FrxReport().Load("EFDZReport.frx");
            LocalDateTime dt = LocalDate.parse(ZREPORTNUMBER.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE).atTime(23, 59, 59);
            BaseLog.writeLog("SP:" + Wire.json(Map.of("NAME", sp.NAME, "TIN", sp.TIN, "VRN", sp.VRN, "SERIAL", sp.SERIAL, "UIN", sp.UIN)));
            BaseLog.writeLog("UIN:" + sp.UIN);
            String money = n0(MONEY, IniInfo.Mradio);
            report.SetParameterValue("StationName", sp.NAME).SetParameterValue("Tin", sp.TIN).SetParameterValue("VRN", sp.VRN)
                .SetParameterValue("SERIAL", sp.SERIAL).SetParameterValue("UIN", sp.UIN.replace("-", "_")).SetParameterValue("TAXOFFICE", sp.TAXOFFICE)
                .SetParameterValue("Location", sp.CITY).SetParameterValue("RTDATE", LocalDate.now().toString())
                .SetParameterValue("TIME", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))).SetParameterValue("ZREPORTNUMBER", ZREPORTNUMBER)
                .SetParameterValue("RealTime", dt.minusDays(1).format(Conv.STD)).SetParameterValue("DISCOUNTS", "0").SetParameterValue("DISCOUNTSAmount", "0")
                .SetParameterValue("MARKUPG", "0").SetParameterValue("MARKUPGAmount", "0").SetParameterValue("MONEY", money)
                .SetParameterValue("FIRSTRECEIPT", FIRSTRECEIPT).SetParameterValue("LASTRECEIPT", LASTRECEIPT).SetParameterValue("RECEIPTISSUED", RECEIPTISSUED)
                .SetParameterValue("UNLEADED", n2(UNLEADED, IniInfo.Vradio)).SetParameterValue("UNLEADEDAmount", n0(UNLEADEDAmount, IniInfo.Mradio))
                .SetParameterValue("DIESEL", n2(DIESEL, IniInfo.Vradio)).SetParameterValue("DIESELAmount", n0(DIESELAmount, IniInfo.Mradio))
                .SetParameterValue("KEROSENE", n2(KEROSENE, IniInfo.Vradio)).SetParameterValue("KEROSENEAmount", n0(KEROSENEAmount, IniInfo.Mradio))
                .SetParameterValue("AmountTotal", money).SetParameterValue("CASH", money).SetParameterValue("CHEQUE", "").SetParameterValue("CREDITCARD", "")
                .SetParameterValue("EMONEY", "").SetParameterValue("INVOICE", "").SetParameterValue("PAYMENTSTOTAL", money)
                .SetParameterValue("TAXETURNOVER", money).SetParameterValue("TURNOVEREX", money).SetParameterValue("TURNOVERTOTAL", money)
                .SetParameterValue("GROSSEX", n0(GROSSEX, IniInfo.Vradio));
            for (String k : new String[] {"TAXATURNOVER", "TAXANETSUM", "TAXATAX", "TAXBTURNOVER", "TAXBNETSUM", "TAXBTAX", "TAXCTURNOVER", "TAXCNETSUM",
                "TAXCTAX", "TAXDTURNOVER", "TAXDNETSUM", "TAXDTAX", "TURNOVERALL", "NETSUMALL", "TAXALL", "TURNOVERSR", "GROSSA", "NETA", "GROSSB", "NETB",
                "GROSSC", "NETC", "GROSSSR"}) {
                report.SetParameterValue(k, "0.00");
            }
            report.Print();
            return true;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }

    // ------------------------------------------------------------ écran de caisse

    /** InsertBillOrder : facture (BillOrderSummary) avec remises et coupons, montant réel et poste des boîtiers. */
    public static boolean InsertBillOrder(String BillOrderNumber, BigDecimal SumAmount, int BillPayMent, String CardNumber, List<BigDecimal> Discount,
        List<BigDecimal> Coupon, int PersonalCreditID, String[] Error) {
        Error[0] = "";
        try {
            BigDecimal d = Discount.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal c = Coupon.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            db().insert("BillOrderSummary", Row.of("BillOrderNumber", BillOrderNumber, "SumAmount", SumAmount, "BillPayMent", BillPayMent,
                "CardNumber", CardNumber, "Discount", Wire.json(Discount), "Coupon", Wire.json(Coupon), "RealAmount", SumAmount.subtract(d).subtract(c),
                "CreateTime", BaseFunction.ConvertDateTimeToInt(), "BoxShift", IniInfo.BoxShift, "PersonCreditID", PersonalCreditID));
            return true;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }

    private static String s(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal d) {
            return d.toPlainString();
        }
        if (v instanceof Double || v instanceof Float) {
            return Conv.num(((Number) v).doubleValue());
        }
        return v.toString();
    }

    private static List<BigDecimal> decimals(String json) {
        List<BigDecimal> out = new ArrayList<>();
        Object o = json == null || json.isEmpty() ? null : Wire.parse(json);
        if (o instanceof List<?> l) {
            for (Object x : l) {
                out.add(Conv.toDecimal(x));
            }
        }
        return out;
    }

    /** Ticket de caisse (CashReceipt.frx) d'une facture : carburant, produits, remises, coupons, total, TVA. */
    public static FrxReport BoxCashPreviewPrint(String BillOrder, String TranNo, String POSID, String Time, String OldOrder, String TotalAmount,
        String[] Error) {
        Error[0] = "";
        try {
            FrxReport report = new FrxReport().Load("CashReceipt.frx");
            DataTable dt = new DataTable().addColumn("GradeM").addColumn("Amount");
            for (Row item : db().list("BillOrder", "{BillOrderNumber} = :b", p("b", BillOrder))) {
                dt.addRow("P:" + s(item.get("POSID")) + " " + item.str("GradeName") + " " + s(item.get("Volume")) + "L " + s(item.get("Price")) + "B/L",
                    s(item.get("Amount")) + " T" + s(item.get("TankNo")));
            }
            for (Row item : db().list("ProductLog", "{BillOrderNumber} = :b", p("b", BillOrder))) {
                dt.addRow(item.str("Name") + " / " + s(item.get("Quantity")) + " / " + s(item.get("Price")), s(item.get("Amount")));
            }
            Row summary = db().first("BillOrderSummary", "{BillOrderNumber} = :b", p("b", BillOrder));
            for (BigDecimal d : decimals(summary.str("Discount"))) {
                dt.addRow("Discount", d);
            }
            for (BigDecimal c : decimals(summary.str("Coupon"))) {
                dt.addRow("Coupon", c);
            }
            BigDecimal real = summary.dec("RealAmount");
            dt.addRow("Total", real);
            if (IniInfo.vatRun) {
                BigDecimal excl = real.multiply(new BigDecimal("0.9345794392523364")).setScale(2, RoundingMode.HALF_EVEN);
                dt.addRow("exclusive", excl);
                dt.addRow("ภาษี", real.subtract(excl));
            }
            report.RegisterData("Table1", dt).SetDataSource("Data1", "Table1");
            String pay = switch (summary.i("BillPayMent")) {
                case 0 -> "Cash";
                case 1 -> "CreditCard";
                case 2 -> "FleetCard";
                case 3 -> "QRCode";
                case 4 -> "BankTransfer";
                case 6 -> "PersonalCredit";
                case 7 -> "TestNozzle";
                case 8 -> "InternalUse";
                default -> "";
            };
            report.SetParameterValue("CashPay", pay).SetParameterValue("CashNumber", summary.str("CardNumber")).SetParameterValue("CashAmount", real)
                .SetParameterValue("CashCompanyName", sc.StationCompany).SetParameterValue("CashOrder", BillOrder)
                .SetParameterValue("CashAddTel", sp.CITY + " Tel." + sc.Tel).SetParameterValue("CashTax", sc.StationTax)
                .SetParameterValue("CashBranch", sc.Branch).SetParameterValue("CashTranNo", BaseFunction.SetStrLen(TranNo, 9))
                .SetParameterValue("CashPos", POSID).SetParameterValue("CashTime", Time).SetParameterValue("CashOldOrder", OldOrder);
            return report;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return null;
        }
    }

    /** Tables communes des rapports de poste / jour / mois (BoxShiftReceiptRun / BoxDayReceiptRun). */
    private static void ShiftTables(FrxReport report, Row list, List<Integer> shifts) {
        BigDecimal[] sums = new BigDecimal[10];
        java.util.Arrays.fill(sums, BigDecimal.ZERO);
        DataTable noz = new DataTable().addColumn("NozRunID").addColumn("GradeName").addColumn("StartVolume").addColumn("EndVolume").addColumn("Volume")
            .addColumn("Amount");
        noz.addRow("ID", "สินค้า", "เริ่มต้น", "สิ้นสุด", "ปริมาณ", "มูลค่า");
        for (Row i : Wire.parseRows(empty(list.str("NozTable")))) {
            noz.addRow(s(i.get("NozID")), s(i.get("Product")), s(i.get("Start")), s(i.get("End")), s(i.get("Volume")), s(i.get("Amount")));
            sums[0] = sums[0].add(i.dec("Amount"));
            sums[1] = sums[1].add(i.dec("Volume"));
        }
        DataTable test = new DataTable().addColumn("NozID").addColumn("GradeName").addColumn("Volume").addColumn("Amount");
        test.addRow("ID", "สินค้า", "ปริมาณ", "มูลค่า");
        for (Row i : Wire.parseRows(empty(list.str("TestTable")))) {
            test.addRow(s(i.get("NozID")), s(i.get("Product")), s(i.get("Volume")), s(i.get("Amount")));
            sums[2] = sums[2].add(i.dec("Amount"));
            sums[3] = sums[3].add(i.dec("Volume"));
        }
        DataTable sale = new DataTable().addColumn("ProductName").addColumn("Volume").addColumn("Amount");
        sale.addRow("ชื่อสินค้า", "จำนวน", "มูลค่า");
        int count = 0;
        if (!shifts.isEmpty()) {
            for (Row g : db().query(db().sql("ProductLog", "select {ProductRealID} AS ProductRealID, SUM({Amount}) AS Amount, SUM({Quantity}) AS Quantity"
                + " from [ProductLog] where {ShiftNumber} IN (:s) group by {ProductRealID}"), p("s", shifts))) {
                Row product = db().first("ProductList", "{ID} = :i", p("i", g.get("ProductRealID")));
                sale.addRow(product == null ? "" : product.str("Name"), s(g.get("Quantity")), s(g.get("Amount")));
                count += g.i("Quantity");
                sums[4] = sums[4].add(g.dec("Amount"));
            }
        }
        DataTable refund = new DataTable().addColumn("ProductName").addColumn("Volume").addColumn("Amount");
        refund.addRow("ชื่อสินค้า", "จำนวน", "มูลค่า");
        DataTable payment = new DataTable().addColumn("PayType").addColumn("Volume").addColumn("Amount");
        payment.addRow("ประเภท", "จำนวน", "มูลค่า");
        for (Row i : Wire.parseRows(empty(list.str("PayMentSumTable")))) {
            payment.addRow(s(i.get("PayMentMethod")), s(i.get("Quantity")), s(i.get("Amount")));
            sums[5] = sums[5].add(i.dec("Quantity"));
            sums[6] = sums[6].add(i.dec("Amount"));
        }
        DataTable discount = new DataTable().addColumn("Discount").addColumn("Quantity").addColumn("Amount");
        discount.addRow("ประเภท", "จำนวน", "มูลค่า");
        for (Row i : Wire.parseRows(empty(list.str("DiscountSumTable")))) {
            discount.addRow(s(i.get("DiscountType")), s(i.get("Quantity")), s(i.get("Amount")));
            sums[7] = sums[7].add(i.dec("Quantity"));
            sums[8] = sums[8].add(i.dec("Amount"));
        }
        DataTable submit = new DataTable().addColumn("SubmitType").addColumn("Quantity").addColumn("Amount");
        submit.addRow("ประเภท", "จำนวน", "มูลค่า");
        DataTable abnormal = new DataTable().addColumn("itemType").addColumn("Quantity").addColumn("Amount");
        abnormal.addRow("ประเภท", "จำนวน", "มูลค่า");
        report.RegisterData("NozTable", noz).RegisterData("TestTable", test).RegisterData("ProductSaleTable", sale)
            .RegisterData("ProductRefundTable", refund).RegisterData("PayMentSumTable", payment).RegisterData("DiscountSumTable", discount)
            .RegisterData("MoneySubmitTable", submit).RegisterData("AbnormalTable", abnormal);
        report.SetParameterValue("CompanyName", sc.StationCompany).SetParameterValue("BranchValue", sc.Branch)
            .SetParameterValue("ShiftPrintDate", LocalDateTime.now().format(Conv.STD)).SetParameterValue("ShiftPosNumber", 1)
            .SetParameterValue("ShiftCasher", IniInfo.LoginName)
            .SetParameterValue("ShiftOpenTime", BaseFunction.StampToDateTime(list.str("StartTime")).format(Conv.STD))
            .SetParameterValue("ShiftCloseTime", BaseFunction.StampToDateTime(list.str("EndTime")).format(Conv.STD))
            .SetParameterValue("NozSumAmount", sums[0]).SetParameterValue("NozSumVolume", sums[1]).SetParameterValue("TestNozSumAmount", sums[2])
            .SetParameterValue("TestNozSumVolume", sums[3]).SetParameterValue("NozRemainAmount", sums[0].subtract(sums[2]))
            .SetParameterValue("NozRemainVolume", sums[1].subtract(sums[3])).SetParameterValue("ProductCount", count)
            .SetParameterValue("ProductAmount", sums[4]).SetParameterValue("PayMentCount", sums[5]).SetParameterValue("PayMentAmount", sums[6])
            .SetParameterValue("DiscountTime", sums[7]).SetParameterValue("DiscountAmount", sums[8]);
    }

    private static String empty(String json) {
        return json == null || json.isEmpty() ? "[]" : json;
    }

    /** Rapport de clôture d'un poste des boîtiers (BoxShiftReceiptRun.frx). */
    public static FrxReport BoxShiftPrint(int BoxShiftID, String[] Error) {
        Error[0] = "";
        try {
            Row shift = db().first("BoxShiftList", "{ID} = :i", p("i", BoxShiftID));
            if (shift == null) {
                throw new IllegalStateException("BoxShift Lost");
            }
            FrxReport report = new FrxReport().Load("BoxShiftReceiptRun.frx");
            ShiftTables(report, shift, List.of(shift.i("BoxShiftID")));
            report.SetParameterValue("ShiftNumber", shift.get("BoxShiftID"))
                .SetParameterValue("ShiftDayWork", BaseFunction.StampToDateTime(shift.str("EndTime")).toLocalDate().toString());
            return report;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return null;
        }
    }

    /** Rapport de journée (DayMonth = 0, BoxDayList) ou de mois (BoxMonthList) des boîtiers (BoxDayReceiptRun.frx). */
    public static FrxReport BoxDayPrint(int BoxDayID, int DayMonth, String[] Error) {
        Error[0] = "";
        try {
            Row list;
            List<Integer> shifts;
            String day;
            if (DayMonth == 0) {
                Row b = db().first("BoxDayList", "{ID} = :i", p("i", BoxDayID));
                if (b == null) {
                    throw new IllegalStateException("BoxDay Lost");
                }
                list = b;
                day = b.str("BoxDay");
                shifts = db().list("BoxShiftList", "{BoxDay} = :d", p("d", day)).stream().map(t -> t.i("BoxShiftID")).toList();
            } else {
                Row m = db().first("BoxMonthList", "{ID} = :i", p("i", BoxDayID));
                if (m == null) {
                    throw new IllegalStateException("BoxMonth Lost");
                }
                list = m;
                day = m.str("BoxMonth");
                String month = day.replace("-", "").trim();
                shifts = db().list("BoxShiftList", "{BoxShiftID} >= :f AND {BoxShiftID} <= :l",
                    p("f", Integer.parseInt(month + "0000"), "l", Integer.parseInt(month + "9999"))).stream().map(t -> t.i("BoxShiftID")).toList();
            }
            FrxReport report = new FrxReport().Load("BoxDayReceiptRun.frx");
            ShiftTables(report, list, shifts);
            report.SetParameterValue("ShiftDayWork", day);
            return report;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return null;
        }
    }

    private static FrxReport priceReport(String file, DataTable dt) {
        return new FrxReport().Load(file).RegisterData("Table1", dt).SetDataSource("Data1", "Table1").SetParameterValue("StationCodeParam", IniInfo.Code)
            .SetParameterValue("StationNameParam", IniInfo.StationName).SetParameterValue("PrintParam", LocalDateTime.now().format(Conv.STD))
            .SetParameterValue("EmployeeParam", IniInfo.LoginName);
    }

    /** Ticket de changement de prix (PriceReceipt.frx). */
    public static FrxReport BoxPricePrint(List<Row> list, String[] Error) {
        Error[0] = "";
        try {
            DataTable dt = new DataTable().addColumn("GradeM").addColumn("OldPrice").addColumn("NewPrice");
            long last = 0;
            for (Row item : list) {
                dt.addRow(BaseFunction.GetNormalGradeByID(item.i("GradeID")), s(item.get("OldPrice")), s(item.get("Price")));
                last = Math.max(last, item.l("UpdateTime"));
            }
            return priceReport("PriceReceipt.frx", dt).SetParameterValue("ChangeTimeParam", BaseFunction.StampToDateTime(String.valueOf(last)).format(Conv.STD));
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return null;
        }
    }

    /** Ticket des prix en vigueur (CurPriceReceipt.frx). */
    public static FrxReport BoxCurrentPricePrint(List<Row> list, String[] Error) {
        Error[0] = "";
        try {
            DataTable dt = new DataTable().addColumn("GradeM").addColumn("NewPrice");
            for (Row item : list) {
                dt.addRow(BaseFunction.GetNormalGradeByID(item.i("GradeID")), s(item.get("Price")));
            }
            return priceReport("CurPriceReceipt.frx", dt);
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return null;
        }
    }

    /** Ticket des index des pistolets (MeterReceipt.frx) : dernier compteur de chaque pistolet. */
    public static FrxReport BoxNozPrint(String[] Error) {
        Error[0] = "";
        try {
            DataTable dt = new DataTable().addColumn("PumpID").addColumn("NozID").addColumn("GradeName").addColumn("VolumeTotal");
            for (Row item : db().query(db().sql("select a.PumpID, a.NozID, a.GradeID, b.VolumeTotal from (select {NormalNoz.PumpID} AS PumpID,"
                + " {NormalNoz.NozID} AS NozID, {NormalNoz.GradeID} AS GradeID from [NormalNoz]) a left join (SELECT NozID, VolumeTotal FROM"
                + " (SELECT {NormalTransaction.NozID} AS NozID, {NormalTransaction.VolumeTotal} AS VolumeTotal, ROW_NUMBER() OVER (PARTITION BY"
                + " {NormalTransaction.NozID} ORDER BY {NormalTransaction.CreateTime} DESC) AS rn FROM [NormalTransaction]) AS sub WHERE rn = 1) b"
                + " on a.NozID = b.NozID"), null)) {
                dt.addRow(s(item.get("PumpID")), s(item.get("NozID")), BaseFunction.GetNormalGradeByID(item.i("GradeID")),
                    item.get("VolumeTotal") == null ? "0" : s(item.get("VolumeTotal")));
            }
            return priceReport("MeterReceipt.frx", dt);
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return null;
        }
    }

    public static String GetCashPayMent(int billPayMent) {
        return switch (billPayMent) {
            case 0 -> "Cash";
            case 1 -> "Credit";
            case 2 -> "FleetCard";
            case 3 -> "QRCode";
            case 4 -> "BankTransfer";
            case 5 -> "PresonCredit";
            case 7 -> "Testnozzle";
            default -> "";
        };
    }

    /** BahtText : montant en toutes lettres (thaï). */
    public static String BahtText(BigDecimal number, String[] Error) {
        Error[0] = "";
        try {
            boolean negative = number.signum() < 0;
            number = number.abs();
            if (number.signum() == 0) {
                return "ศูนย์บาทถ้วน";
            }
            BigDecimal num = number.setScale(2, RoundingMode.HALF_UP);
            long baht = num.longValue();
            int satang = num.subtract(BigDecimal.valueOf(baht)).movePointRight(2).intValue();
            String text = ConvertIntegerToThaiText(baht) + "บาท" + (satang <= 0 ? "ถ้วน" : ConvertIntegerToThaiText(satang) + "สตางค์");
            return negative ? "ลบ" + text : text;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return "";
        }
    }

    private static String ConvertIntegerToThaiText(long number) {
        if (number == 0L) {
            return "ศูนย์";
        }
        if (number >= 1000000) {
            String text = ConvertIntegerToThaiText(number / 1000000) + "ล้าน";
            if (number % 1000000 > 0) {
                text += ConvertIntegerToThaiText(number % 1000000);
            }
            return text;
        }
        String[] digits = {"ศูนย์", "หนึ่ง", "สอง", "สาม", "สี่", "ห้า", "หก", "เจ็ด", "แปด", "เก้า"};
        String[] units = {"แสน", "หมื่น", "พัน", "ร้อย", "สิบ", ""};
        String d6 = String.format("%06d", number);
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < 6; i++) {
            int n = d6.charAt(i) - '0';
            if (n == 0) {
                continue;
            }
            any = true;
            if (i == 4) {
                sb.append(n == 1 ? "สิบ" : n == 2 ? "ยี่สิบ" : digits[n] + "สิบ");
            } else if (i == 5) {
                // comme l'original : « เอ็ด » dès qu'un chiffre précède (y compris pour 1 seul)
                sb.append(n == 1 && any ? "เอ็ด" : digits[n]);
            } else {
                sb.append(digits[n]).append(units[i]);
            }
        }
        return sb.toString();
    }

    /** UpdateProductLog : mouvement d'un produit de la boutique (vente : ProductOperate 3, rattachée au poste). */
    public static boolean UpdateProductLog(int RealID, String ProductID, BigDecimal Amount, String BillOrderNumber, String CardNumber, String RealName,
        int FinalQuantity, BigDecimal Price, int ProductOperate, int Quantity, String[] Error) {
        Error[0] = "";
        try {
            Row log = Row.of("ProductRealID", RealID, "ProductID", ProductID, "Amount", Amount, "BillOrderNumber", BillOrderNumber, "CardNumber", CardNumber,
                "CreateTime", BaseFunction.ConvertDateTimeToInt(), "Name", RealName, "Operater", IniInfo.LoginName, "StationID", IniInfo.StationID,
                "CompanyID", IniInfo.CompanyID, "FinalQuantity", FinalQuantity, "Price", Price, "ProductOperate", ProductOperate, "Quantity", Quantity);
            if (ProductOperate == 3) {
                log.put("ShiftNumber", IniInfo.BoxShift);
            }
            db().insert("ProductLog", log);
            return true;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }

    public static boolean AddCardLogTemp(String CardNumber, int Operater, BigDecimal Amount, String[] Error) {
        Error[0] = "";
        try {
            db().insert("OilSalerTemp", Row.of("CardNumber", CardNumber, "Amount", Amount, "Operater", Operater, "UpdateTime", LocalDateTime.now()));
            return true;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }
}
