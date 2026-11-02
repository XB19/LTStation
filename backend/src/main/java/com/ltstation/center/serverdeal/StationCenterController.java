package com.ltstation.center.serverdeal;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Services /Station/* appelés par le logiciel Station en mode Normal.
 *
 * RECONSTRUCTION : absents du Center fourni (seul le client existe). Contrats repris des classes du client ; choix :
 *  - rapports, alarmes et postes envoyés par la station : conservés tels quels dans station_upload (kind + JSON) ;
 *  - crédit client : tables personal_credit, personal_credit_bill, personal_credit_log (database/schema_extra.sql) ;
 *    aucun écran de Center ne les alimente encore, et les relevés ne sont pas générés ;
 *  - station_settings (name = SetPassword, DayCompletedTime) : mot de passe des réglages et heure de fin de journée ;
 *  - GetVersion : EmpVersion = DataVersions.White, incrémenté à chaque changement de carte employé ;
 *  - TanSanPrint, plaques et fichiers de programme : pas de source dans ce Center, réponses vides.
 */
@RestController
public class StationCenterController extends ServerDealBase {

    private final ExtendCardController cards;

    public StationCenterController(@Qualifier("centerSugar") Sugar db, CenterDeal deal, ExtendCardController cards) {
        super(db, deal);
        this.cards = cards;
    }

    private static boolean filled(String v) {
        return v != null && !v.isEmpty();
    }

    // ------------------------------------------------------------ réglages de la station

    private String setting(int station, String name) {
        Row r = db.queryFirst("SELECT value FROM station_settings WHERE station_id = :s AND name = :n", p("s", station, "n", name));
        return r == null ? null : r.str("value");
    }

    private Row station(Row m) {
        Row st = filled(m.str("Code")) ? db.first("Stations", "{Code} = :c", p("c", m.str("Code")))
            : db.first("Stations", "{StationID} = :s", p("s", m.i("StationID")));
        if (st == null) {
            throw fail("Requête illégale : la station n'existe pas");
        }
        return st;
    }

    private String password(Row m) {
        String pwd = setting(station(m).i("StationID"), "SetPassword");
        if (!filled(pwd)) {
            throw fail("Mot de passe de la station non défini");
        }
        return pwd;
    }

    /** Fenêtre StationPassword de la caisse : le mot de passe est renvoyé dans le champ Code. */
    @RequestMapping("/ServerDeal/GetStationSetPassword")
    public String GetStationSetPasswordDeal(@RequestBody(required = false) String body) {
        try {
            return okJson(Row.of("Code", password(req(body))));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** BaseFunction.GetStationSetPassword : le mot de passe est le message lui-même. */
    @RequestMapping("/Station/GetStationSetPassword")
    public String GetStationSetPassword(@RequestBody(required = false) String body) {
        try {
            return okText(password(req(body)));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** Sans heure de fin de journée connue, refus : la station garde ses réglages. */
    @RequestMapping("/Station/StationUpdateMessage")
    public String StationUpdateMessage(@RequestBody(required = false) String body) {
        try {
            Row st = station(req(body));
            String day = setting(st.i("StationID"), "DayCompletedTime");
            if (!filled(day)) {
                throw fail("Aucune mise à jour");
            }
            return okJson(Row.of("StationID", st.i("StationID"), "StationCode", st.str("Code"), "StationName", st.str("Name"), "DayTime", Conv.toInt(day)));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/Station/GetVersion")
    public String GetVersion(@RequestBody(required = false) String body) {
        try {
            Row st = station(req(body));
            Row dv = db.first("DataVersions");
            return okJson(Row.of("ID", 0, "StationID", st.i("StationID"), "StationCode", st.str("Code"), "EmpVersion", dv == null ? 0 : dv.i("White"), "CarLicense", 0, "SVersion", 0));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/Station/GetNormalEmpCardInfo")
    public String GetNormalEmpCardInfo(@RequestBody(required = false) String body) {
        try {
            List<Row> out = new ArrayList<>();
            for (Row i : db.list("EmployeeCardInfos", "{StationID} = :s", p("s", station(req(body)).i("StationID")))) {
                out.add(Row.of("ASN", i.str("ASN"), "EmployeeID", i.i("EmployeeID"), "CardTypeID", i.i("CardTypeID"), "CipherFlg", i.str("CipherFlg"),
                    "IssueDatetime", i.get("IssueDatetime"), "ValidDatetime", i.get("ValidDatetime"), "Memo1", i.str("Memo1"), "CustomerAccountID", i.i("CustomerAccountID"),
                    "CardStatusTypeID", i.i("CardStatusTypeID"), "StationID", i.i("StationID"), "SpecialPrice", i.str("SpecialPrice"), "PosCanUse", true));
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/Station/GetNormalCarLicense")
    public String GetNormalCarLicense(@RequestBody(required = false) String body) {
        return okJson(List.of());
    }

    @RequestMapping("/Station/GetTanSanPrint")
    public String GetTanSanPrint(@RequestBody(required = false) String body) {
        return okJson(List.of());
    }

    /** Refus : les mises à jour de programme ne sont pas distribuées par ce serveur. */
    @RequestMapping("/Station/GetDowloadFile")
    public String GetDowloadFile(@RequestBody(required = false) String body) {
        return err(fail("Aucun fichier disponible"));
    }

    // ------------------------------------------------------------ données envoyées par la station

    private void archive(String kind, Object station, Object company, String json) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("s", station);
        q.put("c", company);
        q.put("k", kind);
        q.put("p", json);
        db.exec("INSERT INTO station_upload (station_id, company_id, kind, payload) VALUES (:s, :c, :k, :p)", q);
    }

    private String archive(String kind, String body) {
        try {
            String json = reqText(body);
            Object parsed = Wire.parse(json);
            Row first = parsed instanceof List<?> l ? (l.isEmpty() ? new Row() : Wire.parseRows(json).get(0)) : Wire.parseRow(json);
            archive(kind, first.get("StationID"), first.get("CompanyID"), json);
            return okEmpty();
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/Station/UploadShiftStatue")
    public String UploadShiftStatue(@RequestBody(required = false) String body) {
        return archive("ShiftStatue", body);
    }

    @RequestMapping("/Station/UploadHandShiftReport")
    public String UploadHandShiftReport(@RequestBody(required = false) String body) {
        return archive("HandShiftReport", body);
    }

    @RequestMapping("/Station/UploadNormalDailyNozReport")
    public String UploadNormalDailyNozReport(@RequestBody(required = false) String body) {
        return archive("NormalDailyNoz", body);
    }

    @RequestMapping("/Station/UploadNormalDailyEnergySummaryReport")
    public String UploadNormalDailyEnergySummaryReport(@RequestBody(required = false) String body) {
        return archive("NormalDailyEnergy", body);
    }

    @RequestMapping("/Station/UploadNormalDailyStockSummaryReport")
    public String UploadNormalDailyStockSummaryReport(@RequestBody(required = false) String body) {
        return archive("NormalDailyStock", body);
    }

    @RequestMapping("/Station/UploadNormalDailySummaryReport")
    public String UploadNormalDailySummaryReport(@RequestBody(required = false) String body) {
        return archive("NormalDailySummary", body);
    }

    @RequestMapping("/Station/UploadAlarm")
    public String UploadAlarm(@RequestBody(required = false) String body) {
        return archive("Alarm", body);
    }

    // ------------------------------------------------------------ cartes

    /** CardType : 0 = cartes employé, 1 = autres cartes (liste de la fenêtre). */
    @RequestMapping("/Station/GetCardMessage")
    public String GetCardMessage(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            Map<String, Object> q = new LinkedHashMap<>();
            StringBuilder w = new StringBuilder("1 = 1");
            String[][] terms = m.i("CardType") == 0
                ? new String[][] {{"UserName", "u.name"}, {"Phone", "u.telephone"}, {"CNO", "u.certify_key"}}
                : new String[][] {{"UserName", "u.name"}, {"Phone", "u.telephone"}, {"CNO", "u.certificate_no"}};
            for (String[] t : terms) {
                if (filled(m.str(t[0]))) {
                    w.append(" AND ").append(t[1]).append(" = :").append(t[0]);
                    q.put(t[0], m.str(t[0]));
                }
            }
            List<Row> out = new ArrayList<>();
            if (m.i("CardType") == 0) {
                for (Row r : db.query("SELECT i.asn, u.employee_id AS id, u.name, u.telephone, u.certify_key AS cno, i.card_status_type_id AS statue, a.balance "
                    + "FROM employee_card_infos i JOIN employees u ON u.employee_id = i.employee_id LEFT JOIN customer_accounts a ON a.customer_account_id = i.customer_account_id "
                    + "WHERE " + w, q)) {
                    out.add(ret(r));
                }
            } else {
                for (Row r : db.query("SELECT i.asn, u.customer_id AS id, u.name, u.telephone, u.certificate_no AS cno, i.card_status_type_id AS statue, a.balance "
                    + "FROM customer_card_infos i JOIN customers u ON u.customer_id = i.customer_id LEFT JOIN customer_accounts a ON a.customer_account_id = i.customer_account_id "
                    + "WHERE " + w, q)) {
                    out.add(ret(r));
                }
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    private static Row ret(Row r) {
        return Row.of("CustomerID", Conv.toLong(r.get("id")), "Asn", r.str("asn"), "UserName", r.str("name"), "Phone", r.str("telephone"), "CNO", r.str("cno"),
            "CardStatue", r.i("statue"), "Balance", r.dec("balance"));
    }

    /** ChangeType : 1 = perte, 2 = remplacement (renvoie la nouvelle carte), 3 = annulation du remplacement (Asn = nouvelle carte). */
    @RequestMapping("/Station/UpdateCardStatue")
    public String UpdateCardStatue(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row m = req(body);
                String asn = m.str("Asn");
                String res;
                switch (m.i("ChangeType")) {
                    case 1 -> {
                        cards.setStatue(asn, 1);
                        res = okEmpty();
                    }
                    case 2 -> res = okJson(Row.of("ASN", cards.replace(asn)));
                    case 3 -> {
                        Row card = db.first("CustomerCardInfos", "{ASN} = :a", p("a", asn));
                        Row old = card == null ? null : db.first("CustomerCardInfos", "{CustomerID} = :c AND {CardStatusTypeID} = 3 ORDER BY {UpdateDatetime} DESC", p("c", card.i("CustomerID")));
                        if (old == null) {
                            throw fail("Ancienne carte introuvable");
                        }
                        cards.replaceBack(old.str("ASN"), asn);
                        res = okEmpty();
                    }
                    default -> throw fail("Opération inconnue");
                }
                tx.commit();
                return res;
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    /** Vente boutique payée par carte : SelectIndex 0 = solde, 1 = points ; la station a déjà contrôlé le disponible. */
    @RequestMapping("/Station/DealStationSaler")
    public String DealStationSaler(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                String json = reqText(body);
                Row m = Wire.parseRow(json);
                String asn = m.str("CardNumber");
                CenterDeal.CusCard c = deal.getCusCardInfo(asn);
                if (!c.ok()) {
                    throw fail(c.error());
                }
                if (c.cardInfos().i("CardStatusTypeID") != 0) {
                    throw fail("La carte n'est pas utilisable");
                }
                if (m.i("SelectIndex") == 1) {
                    c.cardInfos().put("Score", c.cardInfos().dec("Score").subtract(m.dec("Amount")));
                    c.cardInfos().put("UpdateDatetime", LocalDateTime.now());
                    db.update("CustomerCardInfos", c.cardInfos());
                } else {
                    double balance = c.accounts().d("Balance") - m.dec("Amount").doubleValue();
                    c.accounts().put("Balance", balance);
                    c.accounts().put("UpdateDatetime", LocalDateTime.now());
                    db.update("CustomerAccounts", c.accounts());
                    deal.addAccountLog(c.accounts().str("AccountNo"), -m.dec("Amount").doubleValue(), asn, balance, 13, BigDecimal.ZERO, 0.0, null, 1,
                        LocalDateTime.now().format(Conv.STD), m.i("StationID"));
                }
                archive("StationSaler", m.get("StationID"), m.get("CompanyID"), json);
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    // ------------------------------------------------------------ crédit client

    @RequestMapping("/Station/DowloadPersonalCredit")
    public String DowloadPersonalCredit(@RequestBody(required = false) String body) {
        try {
            List<Row> out = new ArrayList<>();
            for (Row r : db.query("SELECT * FROM personal_credit WHERE station_id = :s ORDER BY id", p("s", req(body).i("StationID")))) {
                out.add(Row.of("ID", r.i("id"), "AccountName", r.str("account_name"), "AccountPassword", r.str("account_password"), "CustomerGroup", r.str("customer_group"),
                    "FirstAddress", r.str("first_address"), "SecondAddress", r.str("second_address"), "ThirdAddress", r.str("third_address"), "Phone", r.str("phone"),
                    "FaxNumber", r.str("fax_number"), "Email", r.str("email"), "TaxIDNumber", r.str("tax_id_number"), "CreateTime", Conv.toLong(r.get("create_time")),
                    "UpdateTime", Conv.toLong(r.get("update_time")), "StationID", r.i("station_id"), "CompanyID", r.i("company_id"), "CardNumber", r.str("card_number"),
                    "CarLicense", r.str("car_license"), "CreditLimit", r.dec("credit_limit"), "UseAmount", r.dec("use_amount"), "BillDate", r.i("bill_date")));
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** Une ligne déjà reçue (même station, même numéro) n'est pas comptée deux fois. */
    @RequestMapping("/Station/UploadPersonalCreditLog")
    public String UploadPersonalCreditLog(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                for (Row l : reqList(body)) {
                    int added = db.exec("INSERT INTO personal_credit_log (source_id, person_credit_id, card_number, amount, create_time, station_id, company_id) "
                        + "VALUES (:i, :p, :n, :a, :t, :s, :c) ON CONFLICT (station_id, source_id) DO NOTHING",
                        p("i", l.i("ID"), "p", l.i("PersonCreditID"), "n", l.str("CardNumber"), "a", l.dec("Amount"), "t", Conv.toLong(l.get("CreateTime")), "s", l.i("StationID"), "c", l.i("CompanyID")));
                    if (added > 0) {
                        db.exec("UPDATE personal_credit SET use_amount = use_amount + :a, update_time = :t WHERE id = :p",
                            p("a", l.dec("Amount"), "t", CenterDeal.now(), "p", l.i("PersonCreditID")));
                    }
                }
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    /** TotalCount : nombre de pages, 20 relevés par page. */
    @RequestMapping("/Station/GetPersonCreditBill")
    public String GetPersonCreditBill(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            int page = Math.max(m.i("PageIndex"), 1);
            Map<String, Object> q = p("p", m.i("PersonalCreditID"), "o", (long) (page - 1) * 20);
            Long total = db.scalar("SELECT count(*) FROM personal_credit_bill WHERE personal_credit_id = :p", q, Long.class);
            List<Row> out = new ArrayList<>();
            for (Row r : db.query("SELECT * FROM personal_credit_bill WHERE personal_credit_id = :p ORDER BY id DESC LIMIT 20 OFFSET :o", q)) {
                out.add(Row.of("ID", r.i("id"), "PersonalCreditID", r.i("personal_credit_id"), "AccountName", r.str("account_name"), "SumAmount", r.dec("sum_amount"),
                    "StationID", r.i("station_id"), "CompanyID", r.i("company_id"), "BillDate", r.i("bill_date"), "CreateTime", Conv.toLong(r.get("create_time")),
                    "Dowload", r.i("dowload"), "Pay", r.i("pay")));
            }
            return okJson(out, (int) ((total == null ? 0 : total + 19) / 20));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/Station/DealPersonalCreditBill")
    public String DealPersonalCreditBill(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row m = req(body);
                Row bill = db.queryFirst("SELECT sum_amount, pay FROM personal_credit_bill WHERE id = :i AND personal_credit_id = :p", p("i", m.i("ID"), "p", m.i("PersonalCreditID")));
                if (bill == null) {
                    throw fail("Relevé introuvable");
                }
                if (bill.i("pay") != 0) {
                    throw fail("Bill Already Paid");
                }
                db.exec("UPDATE personal_credit_bill SET pay = 1 WHERE id = :i", p("i", m.i("ID")));
                db.exec("UPDATE personal_credit SET use_amount = use_amount - :a, update_time = :t WHERE id = :p",
                    p("a", bill.dec("sum_amount"), "t", CenterDeal.now(), "p", m.i("PersonalCreditID")));
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }
}
