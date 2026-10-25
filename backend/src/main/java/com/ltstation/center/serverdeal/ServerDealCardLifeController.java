package com.ltstation.center.serverdeal;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ServerDealController d'origine (5/…) : perte, remplacement et restitution des cartes,
 * numéro maximal, journal d'état, versions et prix des carburants.
 */
@RestController
@RequestMapping(value = "/ServerDeal", produces = ServerDealBase.TEXT)
public class ServerDealCardLifeController extends ServerDealBase {

    /** Message .NET d'une NullReferenceException (comportement d'origine sur données manquantes). */
    private static final String NULL_REF = "Object reference not set to an instance of an object.";

    public ServerDealCardLifeController(@Qualifier("centerSugar") Sugar db, CenterDeal deal) {
        super(db, deal);
    }

    private static String nowStr() {
        return LocalDateTime.now().format(Conv.STD);
    }

    private Row station(Row modal) {
        Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("Code")));
        if (st == null) {
            throw fail("Requête illégale");
        }
        return st;
    }

    private void bump(String... cols) {
        Row dv = db.first("DataVersions");
        if (dv == null) {
            return;
        }
        for (String c : cols) {
            dv.put(c, dv.i(c) + 1);
        }
        db.update("DataVersions", dv);
    }

    /** Recherche des cartes d'un titulaire (CardLostGetMess / CardChangeGetMess) pour un état donné. */
    private List<String> findCards(Row modal, int status, boolean byPrefix) {
        String[] a = modal.s("Mess").split("\\|", -1);
        String type = a[0];
        String cno = a[1];
        String name = a[2];
        String phone = a[3];
        Map<String, Object> ps = p("cno", cno, "n", name, "ph", phone, "ct", modal.i("Start") + 1, "st", status);
        switch (type) {
            case "6", "2", "0": {
                StringBuilder w = new StringBuilder("1=1");
                if (!cno.isEmpty()) {
                    w.append(" AND certificate_type_id = :ct AND certificate_no = :cno");
                }
                if (!name.isEmpty()) {
                    w.append(" AND name = :n");
                }
                if (!phone.isEmpty()) {
                    w.append(" AND telephone = :ph");
                }
                String prefix = byPrefix ? (type.equals("6") ? " AND left(asn, 6) = '010013'"
                    : type.equals("2") ? " AND left(asn, 6) = '010011'" : " AND left(asn, 6) = '010001'") : "";
                return db.jdbc().queryForList("SELECT asn FROM customer_card_infos WHERE card_status_type_id = :st" + prefix
                    + " AND customer_id IN (SELECT customer_id FROM customers WHERE " + w + ")", ps, String.class);
            }
            case "1": {
                StringBuilder w = new StringBuilder("1=1");
                if (!name.isEmpty()) {
                    w.append(" AND name = :n");
                }
                if (!phone.isEmpty()) {
                    w.append(" AND telephone = :ph");
                }
                return db.jdbc().queryForList("SELECT asn FROM employee_card_infos WHERE card_status_type_id = :st "
                    + "AND employee_id IN (SELECT employee_id FROM employees WHERE " + w + ")", ps, String.class);
            }
            case "3", "4", "5": {
                StringBuilder w = new StringBuilder("1=1");
                if (!cno.isEmpty()) {
                    w.append(" AND certificate_no = :cno");
                }
                if (!name.isEmpty()) {
                    w.append(" AND name = :n");
                }
                if (!phone.isEmpty()) {
                    w.append(" AND telephone = :ph");
                }
                String prefix = byPrefix ? (type.equals("3") ? " AND left(asn, 6) = '010002'"
                    : type.equals("4") ? " AND left(asn, 6) = '010012'" : " AND left(asn, 6) = '010014'") : "";
                return db.jdbc().queryForList("SELECT asn FROM group_card_infos WHERE card_status_type_id = :st" + prefix
                    + " AND group_id IN (SELECT group_id FROM groups WHERE " + w + ")", ps, String.class);
            }
            default:
                return new ArrayList<>();
        }
    }

    @RequestMapping("/CardLostGetMess")
    public String CardLostGetMess(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            station(modal);
            return okJson(findCards(modal, 0, true));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/CardChangeGetMess")
    public String CardChangeGetMess(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            station(modal);
            return okJson(findCards(modal, 1, false));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/CardLostGetMessEmployee")
    public String CardLostGetMessEmployee(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = station(modal);
            List<Row> rows = db.query("SELECT ep.employee_id, ep.name, ep.code_inner, ep.station_id, info.asn, info.issue_datetime "
                + "FROM employees ep JOIN employee_card_infos info ON ep.employee_id = info.employee_id "
                + "WHERE ep.station_id = :s AND info.card_status_type_id = 0 ORDER BY info.asn", p("s", st.i("StationID")));
            List<Row> out = new ArrayList<>();
            for (Row r : rows) {
                out.add(deal.dto().make("EmpRetrun", "EmployeeID", r.i("employee_id"), "Name", r.get("name"),
                    "CodeInner", r.i("code_inner"), "StationID", r.i("station_id"), "ASN", r.get("asn"),
                    "IssTime", Conv.toDateTime(r.get("issue_datetime")).format(Conv.STD)));
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/EmployeeCardLost")
    public String EmployeeCardLost(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("Code")));
                String[] a = modal.s("Mess").split("\\|", -1);
                String asn = a[0];
                String empId = a[1];
                if (st == null) {
                    throw fail("Requête illégale");
                }
                Row info = db.first("EmployeeCardInfos", "{ASN} = :a", p("a", asn));
                if (info != null) {
                    info.put("CardStatusTypeID", 1);
                    db.update("EmployeeCardInfos", info);
                    bump("Employee", "White");
                }
                Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", Integer.parseInt(empId)));
                if (info == null) {
                    throw fail(NULL_REF);
                }
                deal.addAccountLog(String.valueOf(info.i("CustomerAccountID")), 0.0, asn, 0.0, 3, BigDecimal.ZERO, 0.0, emp, 1, nowStr(),
                    st.i("StationID"));
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/CardLost")
    public String CardLost(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("Code")));
                Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", modal.i("Start")));
                if (st == null) {
                    throw fail("Requête illégale");
                }
                String asn = modal.s("Mess");
                switch (asn.substring(0, 6)) {
                    case "010008", "010001", "010011", "010013": {
                        CenterDeal.CusCard c = deal.getCusCardInfo(asn);
                        if (!c.ok()) {
                            throw fail(c.error());
                        }
                        if (c.cardInfos().i("CardStatusTypeID") != 0) {
                            throw fail("Cette carte n'est déjà plus en service");
                        }
                        db.insert("BasicBlackLists", Row.of("ASN", c.cardInfos().get("ASN"), "Time", LocalDateTime.now()));
                        c.cardInfos().put("CardStatusTypeID", 1);
                        c.cardInfos().put("UpdateDatetime", LocalDateTime.now());
                        db.update("CustomerCardInfos", c.cardInfos());
                        deal.addAccountLog(c.accounts().s("AccountNo"), 0.0, asn, c.accounts().d("Balance"), 3, BigDecimal.ZERO, 0.0, emp, 1,
                            nowStr(), emp.i("StationID"));
                        break;
                    }
                    case "010002", "010012", "010014": {
                        CenterDeal.GroupCard g = deal.getGroupCardInfo(asn);
                        if (!g.ok()) {
                            throw fail(g.error());
                        }
                        if (g.cardInfos().i("CardStatusTypeID") != 0) {
                            throw fail("Cette carte n'est déjà plus en service");
                        }
                        db.insert("BasicBlackLists", Row.of("ASN", g.cardInfos().get("ASN"), "Time", LocalDateTime.now()));
                        g.cardInfos().put("CardStatusTypeID", 1);
                        g.cardInfos().put("UpdateDate", LocalDateTime.now());
                        db.update("GroupCardInfos", g.cardInfos());
                        break;
                    }
                    case "010004": {
                        CenterDeal.EmpCard e = deal.getEmpCardInfo(asn);
                        if (!e.ok()) {
                            throw fail(e.error());
                        }
                        if (e.cardInfos().i("CardStatusTypeID") != 0) {
                            throw fail("Cette carte n'est déjà plus en service");
                        }
                        db.insert("BasicBlackLists", Row.of("ASN", e.cardInfos().get("ASN"), "Time", LocalDateTime.now()));
                        e.cardInfos().put("CardStatusTypeID", 1);
                        db.update("EmployeeCardInfos", e.cardInfos());
                        deal.addAccountLog(e.accounts().s("AccountNo"), 0.0, asn, e.accounts().d("Balance"), 3, BigDecimal.ZERO, 0.0, emp, 1,
                            nowStr(), emp.i("StationID"));
                        break;
                    }
                    default:
                        break;
                }
                bump("BasicBlack");
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    /** Dernier solde inscrit au journal pour une carte (0 si aucun, comme First() sur double). */
    private BigDecimal lastLogBalance(String asn) {
        Double b = db.scalar("SELECT balance FROM account_service_logs WHERE asn = :a ORDER BY trans_datetime DESC LIMIT 1",
            p("a", asn), Double.class);
        return Conv.toDecimal(b == null ? 0.0 : b);
    }

    @RequestMapping("/CardChangeGetAsnMess")
    public String CardChangeGetAsnMess(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Informations non valides");
            }
            String asn = modal.s("Mess");
            Row m = deal.dto().make("CardChangeGetMessage");
            m.put("Result", false);
            switch (asn.substring(0, 6)) {
                case "010001", "010011", "010013": {
                    CenterDeal.CusCard c = deal.getCusCardInfo(asn);
                    if (!c.ok()) {
                        throw fail(c.error());
                    }
                    Row info = c.cardInfos();
                    fill(m, info.s("ASN"), c.customers().get("Name"), Conv.toStr(c.customers().get("CustomerID")),
                        c.accounts().dec("Balance"), info.i("StationID"), info.get("IssueDatetime"), info.get("ValidDatetime"));
                    break;
                }
                case "010002", "010012", "010014": {
                    CenterDeal.GroupCard g = deal.getGroupCardInfo(asn);
                    if (!g.ok()) {
                        throw fail(g.error());
                    }
                    Row info = g.cardInfos();
                    fill(m, info.s("ASN"), g.groups().get("Name"), Conv.toStr(g.groups().get("GroupID")),
                        g.accounts().dec("Balance"), info.i("StationID"), info.get("IssueDatetime"), info.get("ValidDatetime"));
                    break;
                }
                case "010004": {
                    CenterDeal.EmpCard e = deal.getEmpCardInfo(asn);
                    if (!e.ok()) {
                        throw fail(e.error());
                    }
                    Row info = e.cardInfos();
                    fill(m, info.s("ASN"), e.employees().get("Name"), Conv.toStr(e.employees().get("EmployeeID")),
                        e.accounts().dec("Balance"), info.i("StationID"), info.get("IssueDatetime"), info.get("ValidDatetime"));
                    break;
                }
                default:
                    break;
            }
            return okJson(m);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    private void fill(Row m, String asn, Object name, String id, BigDecimal balance, int stationId, Object issue, Object valid) {
        m.put("Asn", asn);
        m.put("UserName", name);
        m.put("UserID", id);
        m.put("Balance", balance);
        m.put("TotalAmount", lastLogBalance(asn));
        m.put("Result", true);
        m.put("StationID", stationId);
        m.put("StationName", deal.getStationName(stationId));
        m.put("IssueDateTime", Conv.toDateTime(issue).format(Conv.STD));
        m.put("ValidDateTime", Conv.toDateTime(valid).format(Conv.STD));
    }

    /** GetCusCardInfoV2 : refuse le remplacement si une autre carte active a la même pièce d'identité. */
    private CenterDeal.CusCard cusCardV2(String asn) {
        Row info = db.first("CustomerCardInfos", "{ASN} = :a", p("a", asn));
        if (info == null) {
            throw fail("Informations utilisateur introuvables");
        }
        Row cus = db.first("Customers", "{CustomerID} = :c", p("c", info.i("CustomerID")));
        if (cus == null) {
            throw fail("Utilisateur introuvable");
        }
        Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :c", p("c", info.i("CustomerAccountID")));
        if (acc == null) {
            throw fail("Compte utilisateur introuvable");
        }
        for (Row other : db.list("Customers", "{CertificateNo} = :n", p("n", cus.str("CertificateNo")))) {
            if (db.any("CustomerCardInfos", "{CustomerID} = :c AND {CardStatusTypeID} = 0", p("c", other.i("CustomerID")))) {
                throw fail("Une autre carte non déclarée perdue existe avec le même numéro de pièce d'identité");
            }
        }
        return new CenterDeal.CusCard(true, cus, info, acc, "");
    }

    @RequestMapping("/CardChange")
    public String CardChange(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("Code")));
                if (st == null) {
                    throw fail("Requête illégale");
                }
                Row result = deal.dto().make("Modal.CustomerCardInfos");
                String[] a = modal.s("Mess").split("\\|", -1);
                String oldAsn = a[0];
                String newAsn = CenterDeal.setStrLength(a[1], 20);
                int empId = Conv.toInt(a[2]);
                Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", empId));
                if (emp == null) {
                    throw fail("Les informations de l'opérateur n'existent pas");
                }
                String prefix = oldAsn.substring(0, 6);
                switch (prefix) {
                    case "010001", "010011", "010013": {
                        CenterDeal.CusCard c = cusCardV2(oldAsn);
                        Row info = c.cardInfos();
                        if (info.i("CardStatusTypeID") == 0) {
                            throw fail("Cette carte n'a pas encore été déclarée perdue");
                        }
                        info.put("CardStatusTypeID", 3);
                        info.put("UpdateDatetime", LocalDateTime.now());
                        db.update("CustomerCardInfos", info);
                        info.put("ASN", newAsn);
                        info.put("CardStatusTypeID", 0);
                        info.put("UpdateDatetime", LocalDateTime.now());
                        if (prefix.equals("010013")) {
                            c.accounts().put("Balance", 0.0);
                        }
                        deal.addAccountLog(c.accounts().s("AccountNo"), 0.0, newAsn, c.accounts().d("Balance"), 8, BigDecimal.ZERO, 0.0, emp, 1,
                            nowStr(), emp.i("StationID"));
                        db.insert("CustomerCardInfos", info);
                        result = ent("CustomerCardInfos", info);
                        break;
                    }
                    case "010004": {
                        CenterDeal.EmpCard e = deal.getEmpCardInfo(oldAsn);
                        if (!e.ok()) {
                            throw fail(e.error());
                        }
                        Row info = e.cardInfos();
                        if (info.i("CardStatusTypeID") == 0) {
                            throw fail("Cette carte n'a pas encore été déclarée perdue");
                        }
                        info.put("CardStatusTypeID", 3);
                        db.update("EmployeeCardInfos", info);
                        info.put("ASN", newAsn);
                        info.put("CardStatusTypeID", 0);
                        db.insert("EmployeeCardInfos", info);
                        deal.addAccountLog(e.accounts().s("AccountNo"), 0.0, newAsn, e.accounts().d("Balance"), 8, BigDecimal.ZERO, 0.0, emp, 1,
                            nowStr(), emp.i("StationID"));
                        result.put("ASN", newAsn);
                        result.put("IssueDatetime", info.get("IssueDatetime"));
                        result.put("ValidDatetime", info.get("ValidDatetime"));
                        result.put("CustomerID", e.employees().i("EmployeeID"));
                        result.put("CipherFlg", info.get("CipherFlg"));
                        bump("White");
                        break;
                    }
                    case "010002", "010012", "010014": {
                        CenterDeal.GroupCard g = deal.getGroupCardInfo(oldAsn);
                        if (!g.ok()) {
                            throw fail(g.error());
                        }
                        Row info = g.cardInfos();
                        if (info.i("CardStatusTypeID") == 0) {
                            throw fail("Cette carte n'a pas encore été déclarée perdue");
                        }
                        info.put("CardStatusTypeID", 3);
                        info.put("UpdateDate", LocalDateTime.now());
                        db.update("GroupCardInfos", info);
                        info.put("ASN", newAsn);
                        info.put("CardStatusTypeID", 0);
                        info.put("UpdateDate", LocalDateTime.now());
                        db.insert("GroupCardInfos", info);
                        deal.addAccountLog(g.accounts().s("AccountNo"), 0.0, newAsn, g.accounts().d("Balance"), 8, BigDecimal.ZERO, 0.0, emp, 1,
                            nowStr(), emp.i("StationID"));
                        result.put("ASN", newAsn);
                        result.put("IssueDatetime", info.get("IssueDatetime"));
                        result.put("ValidDatetime", info.get("ValidDatetime"));
                        result.put("GroupID", g.groups().i("GroupID"));
                        break;
                    }
                    default:
                        break;
                }
                String res = okJson(result);
                tx.commit();
                return res;
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/CardReturn")
    public String CardReturn(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                    throw fail("Requête illégale");
                }
                String[] a = modal.s("Mess").split("\\|", -1);
                String oldAsn = a[0];
                String newAsn = CenterDeal.setStrLength(a[1], 20);
                switch (oldAsn.substring(0, 6)) {
                    case "010001", "010011", "010013": {
                        db.delete("CustomerCardInfos", "{ASN} = :a", p("a", newAsn));
                        Row info = db.first("CustomerCardInfos", "{ASN} = :a", p("a", oldAsn));
                        if (info == null) {
                            throw fail(NULL_REF);
                        }
                        info.put("CardStatusTypeID", 1);
                        info.put("UpdateDatetime", LocalDateTime.now());
                        db.update("CustomerCardInfos", info);
                        break;
                    }
                    case "010002", "010012", "010014": {
                        db.delete("GroupCardInfos", "{ASN} = :a", p("a", newAsn));
                        Row info = db.first("GroupCardInfos", "{ASN} = :a", p("a", oldAsn));
                        if (info == null) {
                            throw fail(NULL_REF);
                        }
                        info.put("CardStatusTypeID", 1);
                        info.put("UpdateDate", LocalDateTime.now());
                        db.update("GroupCardInfos", info);
                        break;
                    }
                    case "010004": {
                        // L'original supprime la nouvelle carte puis la relit : la lecture échoue toujours.
                        db.delete("EmployeeCardInfos", "{ASN} = :a", p("a", newAsn));
                        Row info = db.first("EmployeeCardInfos", "{ASN} = :a", p("a", newAsn));
                        if (info == null) {
                            throw fail(NULL_REF);
                        }
                        db.deleteEntity("EmployeeCardInfos", info);
                        break;
                    }
                    default:
                        break;
                }
                Row l = db.first("AccountServiceLogs", "{ASN} = :a", p("a", newAsn));
                if (l == null) {
                    throw fail(NULL_REF);
                }
                db.deleteEntity("AccountServiceLogs", l);
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/GetAsnMax")
    public String GetAsnMax(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            String head = modal.s("Mess").substring(0, 6);
            String max = switch (head) {
                case "010001", "010011", "010013" ->
                    db.scalar("SELECT max(asn) FROM customer_card_infos WHERE left(asn, 6) = :h", p("h", head), String.class);
                case "010004" -> db.scalar("SELECT max(asn) FROM employee_card_infos", null, String.class);
                case "010002", "010012", "010014" ->
                    db.scalar("SELECT max(asn) FROM group_card_infos WHERE left(asn, 6) = :h", p("h", head), String.class);
                default -> "";
            };
            return okJson(max);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/CardStatueLog")
    public String CardStatueLog(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("Code")));
                if (st == null) {
                    throw fail("Requête illégale");
                }
                String[] a = modal.s("Mess").split("\\|", -1);
                String oldAsn = a[0];
                int employeeId = Conv.toInt(a[2]);
                Row info = db.first("CustomerCardInfos", "{ASN} = :a", p("a", oldAsn));
                if (info == null) {
                    throw fail("L'utilisateur n'existe pas");
                }
                info.put("CardStatusTypeID", 3);
                db.update("CustomerCardInfos", info);
                Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :c", p("c", info.i("CustomerAccountID")));
                db.insert("AccountServiceLogs", Row.of("TransactionTypeID", 7, "ASN", info.get("ASN"), "CardTypeID", 1,
                    "AccountNo", info.get("AccountNo"), "ProcessCode", "", "CardServiceLogID", 1, "Amount", 0.0, "FeeAmount", 0.0,
                    "DiscountAmount", BigDecimal.ZERO, "AccountDatetime", nowStr(), "PaymentID", 1, "StationID", st.i("StationID"),
                    "EmployeeID", employeeId, "TransDatetime", nowStr(), "Balance", acc.d("Balance"), "TerminalNo", ""));
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/UpDateVersion")
    public String UpDateVersion(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                    throw fail("Requête illégale");
                }
                bump("GradeSpec", "PPU", "MachineCfg");
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/UpOilPrice")
    public String UpOilPrice(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                if (!db.any("Stations", "{StationID} = :s", p("s", modal.i("StationID")))) {
                    throw fail("Requête illégale");
                }
                Row gra = db.first("GradeSpecifics", "{Code} = :c", p("c", modal.str("Code")));
                if (gra == null) {
                    throw fail(NULL_REF);
                }
                Row price = db.first("PriceInfos", "{StationID} = :s AND {GradeSpecificID} = :g",
                    p("s", modal.i("StationID"), "g", gra.i("GradeSpecificID")));
                if (price == null) {
                    db.insert("PriceInfos", Row.of("GradeSpecificID", gra.i("GradeSpecificID"), "NewPPU", modal.d("NewPPU"),
                        "StationID", modal.i("StationID"), "ValidateTime", modal.dt("ValidateTime"), "CurrentPPU", modal.d("CurrentPPU"),
                        "CurrentDensity", modal.get("CurrentDensity"), "Newdensity", modal.d("Newdensity")));
                } else {
                    price.put("CurrentPPU", price.get("NewPPU"));
                    price.put("CurrentDensity", price.get("Newdensity"));
                    price.put("NewPPU", modal.d("NewPPU"));
                    price.put("Newdensity", modal.d("Newdensity"));
                    price.put("ValidateTime", modal.dt("ValidateTime"));
                    db.update("PriceInfos", price);
                }
                bump("PPU", "GradeSpec", "MachineCfg");
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }
}
