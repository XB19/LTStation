package com.ltstation.center.serverdeal;

import com.ltstation.center.crypto.LegacyAes;
import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Services /ExtendUpdate/* des cartes, appelés par CardMake (ICBoxCard) et les fenêtres Carte de Station.
 *
 * RECONSTRUCTION : le serveur d'origine n'a pas été fourni (seuls les clients existent). Les contrats viennent des
 * classes échangées par les clients ; les règles de EasyCardMakeController.CardMake / CardUpdate et de
 * ServerDeal/BalanceChange. Règles déduites, à valider avec de vraies cartes :
 *  - Card_Type : 0 = IC utilisateur (010001), 5 = IC crédit (010011) ; Mifare : 2 RFID, 3 utilisateur, 4 points,
 *    7 facture, 6 employé, le 9e chiffre du numéro valant 1, 2, 3, 4 et 0 (BaseFunction.GetNormalCardType) ;
 *  - C_Type : 0 et 4 = recharge, 1 = consommation (13), 2 = déduction (12) ;
 *  - GetMacStationValid : stations.station_mac contient le code d'activation saisi dans Active ;
 *  - BaseAuth et IssueAuth = Employees.AuthID (numéros des droits de CardMake) ; validité d'une carte : 5 ans, comme CreateCusTomerCardInfo.
 */
@RestController
@RequestMapping("/ExtendUpdate")
public class ExtendCardController extends ServerDealBase {

    /** Groupe « particuliers » de l'original. */
    private static final int PERSON_GROUP = 100000001;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsSender sms;

    public ExtendCardController(@Qualifier("centerSugar") Sugar db, CenterDeal deal, SmsSender sms) {
        super(db, deal);
        this.sms = sms;
    }

    // ------------------------------------------------------------ poste et connexion

    @RequestMapping("/GetMacStationValid")
    public String GetMacStationValid(@RequestBody(required = false) String body) {
        try {
            Object mac = Wire.parse(reqText(body));
            Row st = db.first("Stations", "{StationMac} = :m", p("m", Conv.toStr(mac)));
            if (st == null) {
                throw fail("Poste non activé");
            }
            return okJson(ent("Stations", st));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** Active : le code d'activation est le chiffrement du code du poste, vérifié ici comme par la fenêtre. */
    @RequestMapping("/CreateStationPreCard")
    public String CreateStationPreCard(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            Row emp = login(m.str("LoginName"), m.str("LoginPassword"));
            Row st = db.first("Stations", "{Name} = :n", p("n", m.str("StationName")));
            if (st == null) {
                st = db.first("Stations", "{StationID} = :s", p("s", emp.i("StationID")));
            }
            if (st == null || st.i("StationID") != emp.i("StationID")) {
                throw fail("Les informations de la station sont erronées");
            }
            LegacyAes.decrypt(m.s("Active"));
            st.put("StationMac", m.s("Active"));
            db.update("Stations", st);
            return okEmpty();
        } catch (Exception ex) {
            return err(ex);
        }
    }

    private Row login(String user, String password) {
        Row emp = db.first("Employees", "{Address} = :u AND {Memo} = :p AND {IsUsing} = true", p("u", user, "p", password));
        if (emp == null) {
            throw fail("L'utilisateur n'existe pas");
        }
        return emp;
    }

    @RequestMapping("/ExtendGetLogin")
    public String ExtendGetLogin(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            Row emp = login(m.str("LoginName"), m.str("Password"));
            Row st = db.first("Stations", "{StationID} = :s", p("s", emp.i("StationID")));
            if (st == null) {
                throw fail("Les informations de la station n'existent pas");
            }
            return okJson(Row.of("EmpID", emp.i("EmployeeID"), "CompanyID", st.i("CompanyID"), "Name", emp.str("Name"),
                "StationID", st.i("StationID"), "BaseAuth", emp.str("AuthID")));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ listes

    private List<Row> stations(int company) {
        return db.list("Stations", "{CompanyID} = :c ORDER BY {StationID}", p("c", company));
    }

    @RequestMapping("/ExtendGetStation")
    public String ExtendGetStation(@RequestBody(required = false) String body) {
        try {
            List<Row> out = new ArrayList<>();
            for (Row s : stations(req(body).i("CompanyID"))) {
                out.add(Row.of("ValueID", s.i("StationID"), "Text", s.str("Name")));
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/ExtendGetEmployee")
    public String ExtendGetEmployee(@RequestBody(required = false) String body) {
        try {
            List<Row> out = new ArrayList<>();
            for (Row s : stations(req(body).i("CompanyID"))) {
                for (Row e : db.list("Employees", "{StationID} = :s AND {IsUsing} = true ORDER BY {EmployeeID}", p("s", s.i("StationID")))) {
                    out.add(Row.of("EmployeeID", e.i("EmployeeID"), "Name", e.str("Name"), "StationID", s.i("StationID"), "StationName", s.str("Name"),
                        "Password", e.str("Memo"), "IssueAuth", e.str("AuthID"), "Phone", e.str("Telephone")));
                }
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/ExtendGetScoreRule")
    public String ExtendGetScoreRule(@RequestBody(required = false) String body) {
        try {
            List<Row> out = new ArrayList<>();
            for (Row r : db.list("ScoreRate", "{CompanyID} = :c ORDER BY {ID}", p("c", req(body).i("CompanyID")))) {
                out.add(Row.of("ID", r.i("ID"), "RuleName", r.str("OilTypeName")));
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ groupes

    private static Row groupOut(Row g) {
        return Row.of("GroupID", g.i("GroupID"), "Name", g.str("Name"), "ShortName", g.str("ShortName"), "Code", g.str("Code"), "Address", g.str("Address"),
            "Telephone", g.str("Telephone"), "Fax", g.str("Fax"), "PostCode", g.str("PostCode"), "Contact", g.str("Contact"),
            "CertificateNo", g.str("CertificateNo"), "Openbank", g.str("Openbank"), "BankAccount", g.str("BankAccount"), "LownFlg", g.str("LownFlg"),
            "Memo", g.str("Memo"), "Email", g.str("Email"), "CompanyID", g.i("CompanyID"), "GroupType", 0);
    }

    @RequestMapping("/ExtendGetGroup")
    public String ExtendGetGroup(@RequestBody(required = false) String body) {
        try {
            List<Row> out = new ArrayList<>();
            for (Row g : db.list("Groups", "{CompanyID} = :c AND {GroupID} <> :p ORDER BY {GroupID}", p("c", req(body).i("CompanyID"), "p", PERSON_GROUP))) {
                out.add(groupOut(g));
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/ExtendGetSingleGroup")
    public String ExtendGetSingleGroup(@RequestBody(required = false) String body) {
        try {
            Row g = db.first("Groups", "{GroupID} = :g", p("g", req(body).i("GroupID")));
            if (g == null) {
                throw fail("Le groupe n'existe pas");
            }
            return okJson(groupOut(g));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/ExtendUpdateGroup")
    public String ExtendUpdateGroup(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            Row g;
            boolean add = "Add".equals(m.str("UpdateType"));
            if (add) {
                Integer max = db.scalar("SELECT max(group_id) FROM groups", null, Integer.class);
                g = Row.of("GroupID", Math.max(max == null ? 0 : max, PERSON_GROUP) + 1, "CompanyID", m.i("CompanyID"));
            } else {
                g = db.first("Groups", "{GroupID} = :g", p("g", m.i("GroupID")));
                if (g == null) {
                    throw fail("Le groupe n'existe pas");
                }
            }
            g.put("Name", m.str("Name"));
            g.put("ShortName", m.str("Name"));
            g.put("Address", m.str("Address"));
            g.put("Telephone", m.str("Telephone"));
            g.put("Email", m.str("Email"));
            g.put("CertificateNo", m.str("CertificateNo"));
            if (add) {
                db.insert("Groups", g);
            } else {
                db.update("Groups", g);
            }
            return okEmpty();
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ lecture d'une carte

    private static long stamp(LocalDateTime dt) {
        return dt == null ? 0 : CenterDeal.convertDateTimeToInt(dt);
    }

    private static String time(LocalDateTime dt) {
        return dt == null ? "" : dt.format(Conv.STD);
    }

    private static int cardType(String asn) {
        if (asn.startsWith("00000000") && asn.length() > 8) {
            return switch (asn.charAt(8)) {
                case '0' -> 6;
                case '1' -> 2;
                case '2' -> 3;
                case '3' -> 4;
                case '4' -> 7;
                default -> 3;
            };
        }
        return asn.startsWith("010011") ? 5 : 0;
    }

    private Row cardDeal(String asn) {
        CenterDeal.CusCard c = deal.getCusCardInfo(asn);
        if (!c.ok()) {
            CenterDeal.EmpCard e = deal.getEmpCardInfo(asn);
            if (!e.ok()) {
                throw fail(c.error());
            }
            Row i = e.cardInfos();
            return Row.of("Card_Type", 6, "GroupID", 0, "Asn", asn, "UserName", e.employees().str("Name"), "Phone", e.employees().str("Telephone"), "SecondPhone", "",
                "Certificate", "", "Car_No", "", "PostCode", "", "Email", "", "Address", "", "Birthday", stamp(e.employees().dt("Birthday")),
                "Password", i.str("CipherFlg"), "Valid_Time", time(i.dt("ValidDatetime")), "Account", e.accounts().str("AccountNo"), "DayLimitTime", 0,
                "DayAmountLimit", 0, "DayVolumeLimit", 0, "MonthVolumeLimit", 0, "MonthAmountLimit", 0, "Balance", e.accounts().dec("Balance"),
                "ScoreBal", 0, "StationAmount", 0, "StationAmountShow", false, "StationID", i.i("StationID"), "ScoreRuleID", 0, "Discount", 100,
                "CardStatue", i.i("CardStatusTypeID"), "VTime", time(i.dt("ValidDatetime")), "LimitGrade", "0", "LimitStation", "");
        }
        Row u = c.customers();
        Row i = c.cardInfos();
        return Row.of("Card_Type", cardType(asn), "GroupID", i.i("GroupID"), "Asn", asn, "UserName", u.str("Name"), "Phone", u.str("Telephone"),
            "SecondPhone", u.str("EmpPhone"), "Certificate", u.str("CertificateNo"), "Car_No", u.str("VehicleNo"), "PostCode", u.str("PostCode"), "Email", u.str("Email"),
            "Address", u.str("Address"), "Birthday", stamp(u.dt("Birthday")), "Password", i.str("CipherFlg"), "Valid_Time", time(i.dt("ValidDatetime")),
            "Account", i.str("AccountNo"), "DayLimitTime", i.i("LimitTime"), "DayAmountLimit", i.dec("LimitAmount"), "DayVolumeLimit", i.dec("LimitVolume"),
            "MonthVolumeLimit", i.dec("LimitVolumeMonthly"), "MonthAmountLimit", i.dec("LimitAmountMonthly"), "Balance", c.accounts().dec("Balance"),
            "ScoreBal", i.dec("Score"), "StationAmount", 0, "StationAmountShow", false, "StationID", i.i("StationID"), "ScoreRuleID", i.i("ClassificationID"),
            "Discount", i.dec("Discount"), "CardStatue", i.i("CardStatusTypeID"), "VTime", time(i.dt("ValidDatetime")),
            "LimitGrade", i.str("LimitOil"), "LimitStation", i.str("UseConfine"));
    }

    @RequestMapping("/GetCardMessage")
    public String GetCardMessage(@RequestBody(required = false) String body) {
        try {
            return okJson(cardDeal(req(body).str("ASN")));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ création, modification, annulation

    /** Prochain numéro à partir du préfixe (20 chiffres, comme GetCardNoMax). */
    private String nextAsn(String table, String prefix) {
        String max = db.scalar("SELECT max(asn) FROM " + table + " WHERE asn LIKE :p AND length(asn) = 20", p("p", prefix + "%"), String.class);
        if (max == null) {
            return prefix + "0".repeat(19 - prefix.length()) + "1";
        }
        return CenterDeal.setStrLength(String.valueOf(Long.parseLong(max) + 1), 20);
    }

    private int nextAccount(int floor) {
        Integer max = db.scalar("SELECT max(customer_account_id) FROM customer_accounts", null, Integer.class);
        return Math.max(max == null ? 0 : max, floor) + 1;
    }

    private static Row account(int id) {
        LocalDateTime now = LocalDateTime.now();
        return Row.of("CustomerAccountID", id, "AccountNo", String.valueOf(id), "Balance", 0.0, "BookBalance", 0.0, "ConsumeDatetime", now, "DepositDatetime", now,
            "LoadUpLimit", 0.0, "MileageLastest", 0.0, "ServiceFee", 0.0, "Status", "0", "TotalAmountDaily", 0.0, "TotalAmountDailyStore", 0.0,
            "TotalAmountMonthly", 0.0, "TotalAmountMonthlyStore", 0.0, "TotalTimesDaily", 0, "TotalTimesDailyStore", 0, "TotalVolumeMonthly", 0.0,
            "TransTime", now, "UpdateDatetime", now, "VolumeLastest", 0.0);
    }

    private static void limits(Row i, Row m) {
        i.put("CipherFlg", m.str("Password"));
        i.put("Discount", m.dec("Discount").doubleValue());
        i.put("GroupID", m.i("GroupID"));
        i.put("LimitTime", m.i("DayLimitTime"));
        i.put("LimitAmount", m.dec("DayAmountLimit").doubleValue());
        i.put("LimitVolume", m.dec("DayVolumeLimit").doubleValue());
        i.put("LimitAmountMonthly", m.dec("MonthAmountLimit").doubleValue());
        i.put("LimitVolumeMonthly", m.dec("MonthVolumeLimit").doubleValue());
        i.put("LimitVehicleNo", m.str("CarNo"));
        i.put("SpecialPrice", m.str("SpecialPrice"));
        i.put("ClassificationID", m.i("RuleID"));
        i.put("LimitOil", m.str("LimitGrade"));
        i.put("UseConfine", m.str("LimitStation"));
        i.put("UpdateDatetime", LocalDateTime.now());
    }

    private static void customer(Row u, Row m) {
        u.put("Name", m.str("UserName"));
        u.put("GroupID", m.i("GroupID"));
        u.put("Address", m.str("Address"));
        u.put("Birthday", m.get("Birthday") == null ? null : CenterDeal.timeStampToDateTime(Conv.toLong(m.get("Birthday"))));
        u.put("CertificateNo", m.str("CNO"));
        u.put("Email", m.str("Email"));
        u.put("PostCode", m.str("PostCode"));
        u.put("Telephone", m.str("Phone"));
        u.put("VehicleNo", m.str("CarNo"));
        u.put("EmpPhone", m.str("SecondPhone"));
    }

    private Row addEmployeeCard(Row m) {
        Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", m.i("CustomerID")));
        if (emp == null) {
            throw fail("L'employé n'existe pas");
        }
        String asn = nextAsn("employee_card_infos", "000000000");
        int id = nextAccount(2010200000);
        LocalDateTime now = LocalDateTime.now();
        db.insert("CustomerAccounts", account(id));
        db.insert("EmployeeCardInfos", Row.of("ASN", asn, "EmployeeID", emp.i("EmployeeID"), "CardTypeID", 4, "CipherFlg", m.str("Password"), "IssueDatetime", now,
            "ValidDatetime", now.plusYears(5), "CustomerAccountID", id, "CardStatusTypeID", 0, "StationID", m.i("StationID"), "SpecialPrice", m.str("SpecialPrice"),
            "OpenAccounEmpName", emp.str("Name"), "OpenStationID", emp.i("StationID"), "CompanyID", m.i("CompanyID")));
        db.insert("AccountServiceLogs", log(asn, id, 1, 0, 0, m));
        bump("White");
        m.put("Asn", asn);
        m.put("CustomerAccountID", id);
        m.put("ValidTime", time(now.plusYears(5)));
        return m;
    }

    private Row log(String asn, int account, int type, double amount, double balance, Row m) {
        Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", m.i("OperEmp")));
        String now = LocalDateTime.now().format(Conv.STD);
        return Row.of("AccountDatetime", now, "AccountNo", String.valueOf(account), "Amount", amount, "ASN", asn, "Balance", balance, "CardServiceLogID", type,
            "CardTypeID", CenterDeal.cardTypeOf(asn), "ChequeNo", "", "CreditCountID", "", "DiscountAmount", BigDecimal.ZERO, "EmployeeID", m.i("OperEmp"), "FeeAmount", 0.0,
            "OperatorName", emp == null ? "" : emp.str("Name"), "StationID", m.i("StationID"), "TerminalNo", "", "TransactionTypeID", type, "TransDatetime", now,
            "PaymentID", 1, "ProcessCode", "");
    }

    private Row addCustomerCard(Row m) {
        int type = m.i("Card_Type");
        String prefix = switch (type) {
            case 0 -> "010001";
            case 5 -> "010011";
            case 2 -> "000000001";
            case 3 -> "000000002";
            case 4 -> "000000003";
            case 7 -> "000000004";
            default -> throw fail("Type de carte inconnu");
        };
        String asn = nextAsn("customer_card_infos", prefix);
        int id = nextAccount(2100000000);
        LocalDateTime now = LocalDateTime.now();
        Row u = Row.of("CustomerID", id, "CertificateTypeID", 1, "IssueStatus", true, "CompanyID", m.i("CompanyID"), "Code", m.str("Code"));
        customer(u, m);
        Row i = Row.of("ASN", asn, "CustomerID", id, "CustomerAccountID", id, "AccountNo", String.valueOf(id), "isCreditCard", type == 5, "CreditLines", 0.0,
            "IssueDatetime", now, "ValidDatetime", now.plusYears(5), "CardStatusTypeID", 0, "Score", 0.0, "StationID", m.i("StationID"), "UsedAmountMonthly", 0.0,
            "PreferentialPrice", 0.0, "isLimitVehicleNo", false, "LimitMileage", 0.0, "MileageTolerance", 0.0, "MediaTypeID", 1, "CompanyID", m.i("CompanyID"));
        limits(i, m);
        db.insert("Customers", u);
        db.insert("CustomerAccounts", account(id));
        db.insert("CustomerCardInfos", i);
        db.insert("AccountServiceLogs", log(asn, id, 1, 0, 0, m));
        m.put("Asn", asn);
        m.put("CustomerID", id);
        m.put("CustomerAccountID", id);
        m.put("ValidTime", time(now.plusYears(5)));
        return m;
    }

    /** Annulation d'une création (écriture de la carte impossible) : refusée dès qu'il y a eu un mouvement. */
    private void deleteCard(Row m) {
        String asn = m.str("Asn");
        if (m.i("Card_Type") == 6) {
            CenterDeal.EmpCard e = deal.getEmpCardInfo(asn);
            if (!e.ok()) {
                throw fail(e.error());
            }
            db.delete("AccountServiceLogs", "{ASN} = :a", p("a", asn));
            db.delete("EmployeeCardInfos", "{ASN} = :a", p("a", asn));
            db.delete("CustomerAccounts", "{CustomerAccountID} = :c", p("c", e.cardInfos().i("CustomerAccountID")));
            bump("White");
            return;
        }
        CenterDeal.CusCard c = deal.getCusCardInfo(asn);
        if (!c.ok()) {
            throw fail(c.error());
        }
        if (c.accounts().d("Balance") != 0 || db.count("AccountServiceLogs", "{ASN} = :a", p("a", asn)) > 1) {
            throw fail("La carte a déjà été utilisée");
        }
        db.delete("AccountServiceLogs", "{ASN} = :a", p("a", asn));
        db.delete("CustomerCardInfos", "{ASN} = :a", p("a", asn));
        db.delete("CustomerAccounts", "{CustomerAccountID} = :c", p("c", c.cardInfos().i("CustomerAccountID")));
        db.delete("Customers", "{CustomerID} = :c", p("c", c.cardInfos().i("CustomerID")));
    }

    private void updateCard(Row m) {
        String asn = m.str("Asn");
        if (m.i("Card_Type") == 6) {
            CenterDeal.EmpCard e = deal.getEmpCardInfo(asn);
            if (!e.ok()) {
                throw fail(e.error());
            }
            e.cardInfos().put("EmployeeID", m.i("CustomerID"));
            e.cardInfos().put("CipherFlg", m.str("Password"));
            e.cardInfos().put("SpecialPrice", m.str("SpecialPrice"));
            db.update("EmployeeCardInfos", e.cardInfos());
            bump("White");
            return;
        }
        CenterDeal.CusCard c = deal.getCusCardInfo(asn);
        if (!c.ok()) {
            throw fail(c.error());
        }
        customer(c.customers(), m);
        db.update("Customers", c.customers());
        limits(c.cardInfos(), m);
        db.update("CustomerCardInfos", c.cardInfos());
    }

    @RequestMapping("/CardUpdate")
    public String CardUpdate(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row m = req(body);
                String result = switch (m.str("Update_Type")) {
                    case "Add" -> okJson(m.i("Card_Type") == 6 ? addEmployeeCard(m) : addCustomerCard(m));
                    case "Update" -> {
                        updateCard(m);
                        yield okEmpty();
                    }
                    case "Delete" -> {
                        deleteCard(m);
                        yield okEmpty();
                    }
                    default -> throw fail("Opération inconnue");
                };
                tx.commit();
                return result;
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    // ------------------------------------------------------------ cycle de vie d'une carte

    private void bump(String col) {
        Row dv = db.first("DataVersions");
        if (dv != null) {
            dv.put(col, dv.i(col) + 1);
            db.update("DataVersions", dv);
        }
    }

    /** Code à 6 chiffres envoyé au téléphone de la carte et renvoyé dans Valid_Time : le logiciel compare la saisie. */
    @RequestMapping("/CheckLockNumber")
    public String CheckLockNumber(@RequestBody(required = false) String body) {
        try {
            Row card = cardDeal(req(body).str("Asn"));
            String code = String.format("%06d", RANDOM.nextInt(1000000));
            sms.send(card.str("Phone"), "Your validation code is " + code);
            card.put("Valid_Time", code);
            return okJson(card);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/CardPasswordUpdte")
    public String CardPasswordUpdte(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            String asn = m.str("Asn");
            CenterDeal.CusCard c = deal.getCusCardInfo(asn);
            if (c.ok()) {
                c.cardInfos().put("CipherFlg", m.str("Password"));
                c.cardInfos().put("UpdateDatetime", LocalDateTime.now());
                db.update("CustomerCardInfos", c.cardInfos());
                return okEmpty();
            }
            CenterDeal.EmpCard e = deal.getEmpCardInfo(asn);
            if (!e.ok()) {
                throw fail(c.error());
            }
            e.cardInfos().put("CipherFlg", m.str("Password"));
            db.update("EmployeeCardInfos", e.cardInfos());
            return okEmpty();
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** Statut demandé : 0 = en service, autre = bloquée (liste noire), comme ServerDeal/CardLost. */
    void setStatue(String asn, int statue) {
        CenterDeal.CusCard c = deal.getCusCardInfo(asn);
        if (!c.ok()) {
            throw fail(c.error());
        }
        c.cardInfos().put("CardStatusTypeID", statue);
        c.cardInfos().put("UpdateDatetime", LocalDateTime.now());
        db.update("CustomerCardInfos", c.cardInfos());
        db.delete("BasicBlackLists", "{ASN} = :a", p("a", asn));
        if (statue != 0) {
            db.insert("BasicBlackLists", Row.of("ASN", asn, "Time", LocalDateTime.now()));
        }
        deal.addAccountLog(c.accounts().str("AccountNo"), 0.0, asn, c.accounts().d("Balance"), statue != 0 ? 3 : 5, BigDecimal.ZERO, 0.0, null, 1,
            LocalDateTime.now().format(Conv.STD), c.cardInfos().i("StationID"));
        bump("BasicBlack");
    }

    @RequestMapping("/CardStatueUpdate")
    public String CardStatueUpdate(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row m = req(body);
                setStatue(m.str("Asn"), m.i("CardStatue"));
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    /** Nouveau numéro sur le même compte ; l'ancienne carte passe au statut 3 et en liste noire, comme ServerDeal/CardChange. */
    String replace(String asn) {
        CenterDeal.CusCard c = deal.getCusCardInfo(asn);
        if (!c.ok()) {
            throw fail(c.error());
        }
        Row info = c.cardInfos();
        if (info.i("CardStatusTypeID") == 3) {
            throw fail("Replaced");
        }
        info.put("CardStatusTypeID", 3);
        info.put("UpdateDatetime", LocalDateTime.now());
        db.update("CustomerCardInfos", info);
        db.delete("BasicBlackLists", "{ASN} = :a", p("a", asn));
        db.insert("BasicBlackLists", Row.of("ASN", asn, "Time", LocalDateTime.now()));
        String prefix = asn.startsWith("00000000") ? asn.substring(0, 9) : asn.substring(0, 6);
        String next = nextAsn("customer_card_infos", prefix);
        info.put("ASN", next);
        info.put("CardStatusTypeID", 0);
        db.insert("CustomerCardInfos", info);
        deal.addAccountLog(c.accounts().str("AccountNo"), 0.0, next, c.accounts().d("Balance"), 8, BigDecimal.ZERO, 0.0, null, 1,
            LocalDateTime.now().format(Conv.STD), info.i("StationID"));
        bump("BasicBlack");
        return next;
    }

    /** Écriture de la nouvelle carte impossible : retour à l'ancienne. */
    void replaceBack(String oldAsn, String newAsn) {
        Row old = db.first("CustomerCardInfos", "{ASN} = :a", p("a", oldAsn));
        if (old == null) {
            throw fail("Informations utilisateur introuvables");
        }
        db.delete("AccountServiceLogs", "{ASN} = :a", p("a", newAsn));
        db.delete("CustomerCardInfos", "{ASN} = :a", p("a", newAsn));
        old.put("CardStatusTypeID", 0);
        old.put("UpdateDatetime", LocalDateTime.now());
        db.update("CustomerCardInfos", old);
        db.delete("BasicBlackLists", "{ASN} = :a", p("a", oldAsn));
        bump("BasicBlack");
    }

    @RequestMapping("/CardReplaceCard")
    public String CardReplaceCard(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                String res = okJson(Row.of("Asn", replace(req(body).str("Asn"))));
                tx.commit();
                return res;
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/CardReplaceBack")
    public String CardReplaceBack(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row m = req(body);
                replaceBack(m.str("OldAsn"), m.str("NewAsn"));
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/MessageSend")
    public String MessageSend(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            sms.send(cardDeal(m.str("Asn")).str("Phone"), m.str("Content"));
            return okEmpty();
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/ExtendGetEmployeeCard")
    public String ExtendGetEmployeeCard(@RequestBody(required = false) String body) {
        try {
            return okJson(db.list("EmployeeCardInfos", "{StationID} = :s ORDER BY {ASN}", p("s", req(body).i("StationID"))));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/ExtendGetSingleEmployeeCard")
    public String ExtendGetSingleEmployeeCard(@RequestBody(required = false) String body) {
        try {
            Row i = db.first("EmployeeCardInfos", "{ASN} = :a", p("a", req(body).str("Asn")));
            if (i == null) {
                throw fail("Carte introuvable");
            }
            return okJson(i);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** EmployeeID 0 = création ; le téléphone sert d'identifiant de connexion (la fenêtre n'en demande pas d'autre). */
    @RequestMapping("/ExtendEmployeeUpdate")
    public String ExtendEmployeeUpdate(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            Row e;
            boolean add = m.i("EmployeeID") == 0;
            if (add) {
                Integer inner = db.scalar("SELECT max(code_inner) FROM employees WHERE station_id = :s", p("s", m.i("StationID")), Integer.class);
                Integer id = db.scalar("SELECT max(employee_id) FROM employees", null, Integer.class);
                int next = (id == null ? 0 : id) + 1;
                e = Row.of("EmployeeNo", "E" + next, "Code", m.str("Name"), "StationID", m.i("StationID"), "CertificateTypeID", 1, "CertifyKey", "", "GenderID", 1,
                    "Birthday", LocalDateTime.now(), "PostCode", "1", "CodeInner", (inner == null ? 0 : inner) + 1, "Password", "", "Score", BigDecimal.ZERO, "IsUsing", true,
                    "CenterLogin", false, "OpenID", "", "GzhAuth", "", "RoleID", 0, "PcMenuAuth", "");
            } else {
                e = db.first("Employees", "{EmployeeID} = :e", p("e", m.i("EmployeeID")));
                if (e == null) {
                    throw fail("L'employé n'existe pas");
                }
            }
            e.put("Name", m.str("Name"));
            e.put("Telephone", m.str("Phone"));
            e.put("Memo", m.str("Password"));
            e.put("AuthID", m.str("IssueAuth"));
            if (add) {
                e.put("Address", m.str("Phone"));
                db.insert("Employees", e);
            } else {
                db.update("Employees", e);
            }
            return okEmpty();
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ solde

    /** Annulation d'une recharge dont l'écriture sur la carte a échoué : déduction du même montant. */
    @RequestMapping("/CardBalanceReturn")
    public String CardBalanceReturn(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row m = req(body);
                m.put("C_Type", 2);
                String res = balance(m);
                tx.commit();
                return res;
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/CardBalanceChange")
    public String CardBalanceChange(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                String res = balance(req(body));
                tx.commit();
                return res;
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    private String balance(Row m) {
        String asn = m.str("Asn");
        CenterDeal.CusCard c = deal.getCusCardInfo(asn);
        if (!c.ok()) {
            throw fail(c.error());
        }
        if (c.cardInfos().i("CardStatusTypeID") != 0) {
            throw fail("La carte n'est pas utilisable");
        }
        int kind = m.i("C_Type");
        boolean credit = kind == 0 || kind == 4;
        int type = credit ? 2 : kind == 1 ? 13 : 12;
        BigDecimal amount = m.dec("Amount");
        double delta = (credit ? amount.add(m.dec("DisAmount")) : amount.negate()).doubleValue();
        Row acc = c.accounts();
        double balance = acc.d("Balance") + delta;
        LocalDateTime now = LocalDateTime.now();
        Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", m.i("EmpID")));
        Row l = Row.of("AccountDatetime", now.format(Conv.STD), "AccountNo", acc.get("AccountNo"), "Amount", credit ? amount.doubleValue() : amount.negate().doubleValue(),
            "ASN", asn, "Balance", balance, "CardTypeID", CenterDeal.cardTypeOf(asn), "ChequeNo", m.str("Memo"), "CreditCountID", "", "DiscountAmount", m.dec("DisAmount"),
            "EmployeeID", m.i("EmpID"), "FeeAmount", 0.0, "OperatorName", emp == null ? "" : emp.str("Name"), "PaymentID", 1, "ProcessCode", "", "StationID", m.i("StationID"),
            "TerminalNo", "", "TransactionTypeID", type, "TransDatetime", now.format(Conv.STD));
        long id = Conv.toLong(db.insert("AccountServiceLogs", l));
        acc.put("Balance", balance);
        acc.put("UpdateDatetime", now);
        acc.put("TransTime", now);
        acc.put(credit ? "DepositDatetime" : "ConsumeDatetime", now);
        db.update("CustomerAccounts", acc);
        return okJson(Row.of("AccountLogID", id, "Date", now.format(DAY), "Time", now.format(TIME)));
    }
}
