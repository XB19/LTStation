package com.ltstation.center.serverdeal;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ServerDealController d'origine (4/…) : clés de cartes, code PIN, recharges / débits,
 * virements entre cartes et groupes, annulations et remboursements de factures de crédit.
 */
@RestController
@RequestMapping(value = "/ServerDeal", produces = ServerDealBase.TEXT)
public class ServerDealBalanceController extends ServerDealBase {

    private final boolean secretSame;

    public ServerDealBalanceController(@Qualifier("centerSugar") Sugar db, CenterDeal deal,
                                       @Value("${app.discount-per-company:true}") boolean secretSame) {
        super(db, deal);
        this.secretSame = secretSame;
    }

    private static String nowStr() {
        return LocalDateTime.now().format(Conv.STD);
    }

    private void requireStation(Row modal) {
        if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
            throw fail("Requête illégale");
        }
    }

    // ------------------------------------------------------------ clés et code PIN

    @RequestMapping("/GetSecretKey")
    public String GetSecretKey(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            int company = modal.i("CompanyID");
            if (!secretSame) {
                company = 0;
            } else if ("1000072".equals(modal.str("Code"))) {
                company = 49;
            }
            return okJson(ent("Data", db.list("Data", "{CompanyID} = :c ORDER BY {ID}", p("c", company))));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/UpdateSecretKey")
    public String UpdateSecretKey(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("Code")));
                int company = secretSame ? modal.i("CompanyID") : 0;
                if (st == null) {
                    throw fail("Requête illégale : la station n'existe pas");
                }
                Object parsed = Wire.parse(com.ltstation.center.crypto.LegacyAes.decrypt(modal.s("Mess")));
                List<?> list = parsed instanceof List<?> l ? l : List.of();
                for (int i = 0; i < list.size(); i++) {
                    int index = i > 19 ? i + 1 : i;
                    Row data = db.first("Data", "{ID} = :id AND {CompanyID} = :c", p("id", index, "c", company));
                    boolean add = data == null;
                    if (add) {
                        data = new Row();
                    }
                    data.put("ID", index);
                    data.put("D_Type", String.valueOf(index));
                    data.put("D_Ver", "01");
                    data.put("D_Data", Conv.toStr(list.get(i)));
                    data.put("CompanyID", company);
                    if (add) {
                        db.insert("Data", data);
                    } else {
                        db.update("Data", data);
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

    @RequestMapping("/ReloadPwd")
    public String ReloadPwd(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            Row r = Wire.parseRow(modal.s("Mess"));
            String asn = r.s("ASN");
            switch (asn.substring(0, 6)) {
                case "010001", "010011", "010013": {
                    CenterDeal.CusCard c = deal.getCusCardInfo(asn);
                    if (!c.ok()) {
                        throw fail("L'utilisateur n'existe pas");
                    }
                    String old = c.cardInfos().str("CipherFlg");
                    c.cardInfos().put("CipherFlg", r.get("CipherFlg"));
                    c.cardInfos().put("UpdateDatetime", LocalDateTime.now());
                    db.update("CustomerCardInfos", c.cardInfos());
                    return ret(true, old, 0);
                }
                case "010004": {
                    CenterDeal.EmpCard e = deal.getEmpCardInfo(asn);
                    if (!e.ok()) {
                        throw fail("L'employé n'existe pas");
                    }
                    String old = e.cardInfos().str("CipherFlg");
                    e.cardInfos().put("CipherFlg", r.get("CipherFlg"));
                    db.update("EmployeeCardInfos", e.cardInfos());
                    return ret(true, old, 0);
                }
                case "010002", "010012", "010014": {
                    CenterDeal.GroupCard g = deal.getGroupCardInfo(asn);
                    if (!g.ok()) {
                        throw fail("Le groupe n'existe pas");
                    }
                    String old = g.cardInfos().str("CipherFlg");
                    g.cardInfos().put("CipherFlg", r.get("CipherFlg"));
                    db.update("GroupCardInfos", g.cardInfos());
                    return ret(true, old, 0);
                }
                default:
                    throw fail("Requête illégale");
            }
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ recharges / débits

    /** Convert.ToDecimal("-" + Amount) : échoue comme .NET si le montant est déjà négatif. */
    private static BigDecimal negate(BigDecimal a) {
        if (a.signum() < 0) {
            throw fail("Le format de la chaîne d'entrée est incorrect.");
        }
        return a.negate();
    }

    private static int transType(Row modal) {
        if (modal.b("flag")) {
            return 2;
        }
        String n = modal.s("Name");
        return n.equals("ConsumeButton") ? 13 : (n.equals("DeductionButton") ? 12 : 11);
    }

    @RequestMapping("/BalanceChange")
    public String BalanceChange(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                String input = reqText(body);
                log.writeLog(input);
                Row modal = new Row(Wire.parseRow(input));
                Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("Code")));
                if (st == null) {
                    throw fail("Requête illégale");
                }
                String asn = modal.s("Asn");
                String prefix = asn.substring(0, 6);
                Row accounts = null;
                boolean flag = false;
                if (prefix.equals("010001")) {
                    CenterDeal.CusCard c = deal.getCusCardInfo(asn);
                    if (!c.ok()) {
                        throw fail(c.error());
                    }
                    flag = true;
                    accounts = c.accounts();
                    c.cardInfos().put("Score", c.cardInfos().dec("Score").add(modal.dec("UserScore")));
                    c.cardInfos().put("UpdateDatetime", LocalDateTime.now());
                    db.update("CustomerCardInfos", c.cardInfos());
                }
                if (prefix.equals("010004") || prefix.equals("010006")) {
                    CenterDeal.EmpCard e = deal.getEmpCardInfo(asn);
                    flag = e.ok() && e.cardInfos().i("CardStatusTypeID") == 0;
                    accounts = e.accounts();
                }
                if (prefix.equals("010014") || prefix.equals("010002")) {
                    int type = transType(modal);
                    if (!modal.b("flag")) {
                        modal.put("Amount", negate(modal.dec("Amount")));
                        modal.put("DisAmount", BigDecimal.ZERO);
                    }
                    CenterDeal.GroupCard g = deal.getGroupCardInfo(asn);
                    Row ga = g.accounts();
                    LocalDateTime dt = modal.dt("DT");
                    if (db.any("AccountServiceLogs", "{ASN} = :a AND " + safeTs("trans_datetime") + " = :dt", p("a", asn, "dt", dt))) {
                        throw fail("Requête en double");
                    }
                    double delta = modal.dec("Amount").add(modal.dec("DisAmount")).doubleValue();
                    Row asl = Row.of("AccountDatetime", modal.get("DT"), "AccountNo", ga.get("AccountNo"),
                        "Amount", modal.d("Amount"), "ASN", asn, "Balance", ga.d("Balance") + delta, "EmployeeID", modal.i("EmpID"),
                        "FeeAmount", 0.0, "DiscountAmount", modal.dec("DisAmount"), "PaymentID", modal.i("PayType") + 1,
                        "ProcessCode", "", "StationID", st.i("StationID"), "TerminalNo", "", "TransactionTypeID", type,
                        "TransDatetime", modal.get("DT"), "CardTypeID", prefix.equals("010002") ? 2 : 14);
                    Object id = db.insert("AccountServiceLogs", asl);
                    ga.put("Balance", ga.d("Balance") + delta);
                    ga.put("UpdateDatetime", dt);
                    ga.put("DepositDatetime", dt);
                    db.update("GroupAccounts", ga);
                    tx.commit();
                    return okText(Conv.toStr(id));
                }
                if (prefix.equals("010011")) {
                    CenterDeal.CusCard c = deal.getCusCardInfo(asn);
                    flag = c.ok();
                    accounts = c.accounts();
                }
                if (!flag) {
                    throw fail("Requête illégale");
                }
                if (db.any("AccountServiceLogs", "{ASN} = :a AND {TransDatetime} = :dt", p("a", asn, "dt", modal.dt("DT")))) {
                    throw fail("Requête en double");
                }
                int cardType = switch (prefix) {
                    case "010001" -> 1;
                    case "010004" -> 4;
                    case "010011" -> 11;
                    case "010012" -> 12;
                    default -> 0;
                };
                int type = transType(modal);
                if (!modal.b("flag")) {
                    modal.put("Amount", negate(modal.dec("Amount")));
                    modal.put("DisAmount", BigDecimal.ZERO);
                }
                double delta = modal.dec("Amount").add(modal.dec("DisAmount")).doubleValue();
                Row asl = Row.of("AccountDatetime", modal.get("DT"), "AccountNo", accounts.get("AccountNo"),
                    "Amount", modal.d("Amount"), "ASN", asn, "Balance", accounts.d("Balance") + delta, "EmployeeID", modal.i("EmpID"),
                    "FeeAmount", 0.0, "DiscountAmount", modal.dec("DisAmount"), "PaymentID", modal.i("PayType") + 1,
                    "ProcessCode", "", "StationID", st.i("StationID"), "TerminalNo", "", "TransactionTypeID", type,
                    "TransDatetime", nowStr(), "CardTypeID", cardType);
                Object id = db.insert("AccountServiceLogs", asl);
                LocalDateTime now = LocalDateTime.now();
                accounts.put("Balance", accounts.d("Balance") + delta);
                accounts.put("UpdateDatetime", now);
                accounts.put("DepositDatetime", now);
                accounts.put("ConsumeDatetime", now);
                accounts.put("TransTime", now);
                db.update("CustomerAccounts", accounts);
                tx.commit();
                return okText(Conv.toStr(id));
            } catch (Exception ex) {
                tx.rollback();
                log.writeLog(ex.getMessage());
                return err(ex);
            }
        }
    }

    /** Convert.ToDateTime(colonne) : les dates du journal sont déjà des horodatages dans la base Center. */
    private static String safeTs(String col) {
        return col;
    }

    // ------------------------------------------------------------ virements (SaveTransfer)

    @RequestMapping("/SaveOrTransfer")
    public String SaveOrTransfer(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", modal.i("EmpID")));
                BigDecimal amount = modal.dec("Amount");
                double amount2 = Conv.round(amount.doubleValue(), 2);
                switch (modal.i("PayType")) {
                    case 10: {
                        CenterDeal.GroupCard g = deal.getGroupCardInfo(modal.s("Asn"));
                        if (!g.ok()) {
                            throw fail(g.error());
                        }
                        CenterDeal.CusCard c = deal.getCusCardInfo(modal.s("SToAsn"));
                        if (!c.ok()) {
                            throw fail(c.error());
                        }
                        Row ga = g.accounts();
                        ga.put("Balance", ga.d("Balance") - amount2);
                        ga.put("UpdateDatetime", LocalDateTime.now());
                        db.update("GroupAccounts", ga);
                        db.insert("SaveTransfer", Row.of("Amount", amount, "Asn", modal.get("Asn"), "CreateTime", nowStr(),
                            "SaveToAsn", modal.get("SToAsn"), "STType", modal.i("PayType"), "GroupID", g.groups().i("GroupID"),
                            "IsUsing", false));
                        deal.addAccountLog(ga.s("AccountNo"), amount2, modal.s("Asn"), ga.d("Balance"), 10, BigDecimal.ZERO, 0.0, emp, 1,
                            nowStr(), emp.i("StationID"));
                        break;
                    }
                    case 15: {
                        CenterDeal.CusCard c = deal.getCusCardInfo(modal.s("Asn"));
                        if (!c.ok()) {
                            throw fail(c.error());
                        }
                        Row tr = db.first("SaveTransfer", "{IsUsing} = false AND {SaveToAsn} = :a", p("a", modal.s("Asn")));
                        if (tr != null) {
                            Row acc = c.accounts();
                            acc.put("Balance", acc.d("Balance") + tr.d("Amount"));
                            tr.put("IsUsing", true);
                            db.update("CustomerAccounts", acc);
                            db.update("SaveTransfer", tr);
                            deal.addAccountLog(c.cardInfos().s("AccountNo"), amount2, modal.s("Asn"), acc.d("Balance"), 16,
                                BigDecimal.ZERO, 0.0, emp, 1, nowStr(), emp.i("StationID"));
                        }
                        break;
                    }
                    case 25: {
                        CenterDeal.GroupCard g = deal.getGroupCardInfo(modal.s("Asn"));
                        if (!g.ok()) {
                            throw fail(g.error());
                        }
                        Row tr = db.first("SaveTransfer", "{IsUsing} = false AND {SaveToAsn} = :a", p("a", modal.s("Asn")));
                        if (tr != null) {
                            Row ga = g.accounts();
                            ga.put("Balance", ga.d("Balance") + tr.d("Amount"));
                            tr.put("IsUsing", true);
                            db.update("GroupAccounts", ga);
                            db.update("SaveTransfer", tr);
                            deal.addAccountLog(ga.s("AccountNo"), amount2, modal.s("Asn"), ga.d("Balance"), 19, BigDecimal.ZERO, 0.0, emp, 1,
                                nowStr(), emp.i("StationID"));
                        }
                        break;
                    }
                    case 20: {
                        CenterDeal.GroupCard g = deal.getGroupCardInfo(modal.s("SToAsn"));
                        if (!g.ok()) {
                            throw fail(g.error());
                        }
                        CenterDeal.CusCard c = deal.getCusCardInfo(modal.s("Asn"));
                        if (!c.ok()) {
                            throw fail(c.error());
                        }
                        Row acc = c.accounts();
                        acc.put("Balance", acc.d("Balance") - amount.doubleValue());
                        db.update("CustomerAccounts", acc);
                        db.insert("SaveTransfer", Row.of("Amount", amount, "Asn", modal.get("Asn"), "CreateTime", nowStr(),
                            "SaveToAsn", modal.get("SToAsn"), "STType", modal.i("PayType"), "GroupID", g.groups().i("GroupID"),
                            "IsUsing", false));
                        deal.addAccountLog(c.cardInfos().s("AccountNo"), amount2, modal.s("Asn"), acc.d("Balance"), 18,
                            BigDecimal.ZERO, 0.0, emp, 1, nowStr(), emp.i("StationID"));
                        break;
                    }
                    default:
                        break;
                }
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return ret(false, ex.getMessage(), 0);
            }
        }
    }

    @RequestMapping("/SaveOrTransferErrorBack")
    public String SaveOrTransferErrorBack(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                BigDecimal amount = modal.dec("Amount");
                double amount2 = Conv.round(amount.doubleValue(), 2);
                int pay = modal.i("PayType");
                switch (pay) {
                    case 10: {
                        CenterDeal.GroupCard g = deal.getGroupCardInfo(modal.s("Asn"));
                        if (!g.ok()) {
                            throw fail(g.error());
                        }
                        Row tr = db.first("SaveTransfer", "{Asn} = :a AND {SaveToAsn} = :to AND {Amount} = :am AND {IsUsing} = false "
                            + "AND {STType} = :t ORDER BY {CreateTime} DESC", p("a", modal.s("Asn"), "to", modal.s("SToAsn"), "am", amount, "t", pay));
                        if (tr == null) {
                            throw fail("Virement correspondant introuvable : annulation impossible");
                        }
                        Row ga = g.accounts();
                        ga.put("Balance", ga.d("Balance") + amount2);
                        ga.put("UpdateDatetime", LocalDateTime.now());
                        db.update("GroupAccounts", ga);
                        db.deleteEntity("SaveTransfer", tr);
                        deleteLog(ga.s("AccountNo"), amount2, 10, modal.s("Asn"));
                        break;
                    }
                    case 15: {
                        CenterDeal.CusCard c = deal.getCusCardInfo(modal.s("Asn"));
                        if (!c.ok()) {
                            throw fail(c.error());
                        }
                        Row tr = db.first("SaveTransfer", "{SaveToAsn} = :a AND {Amount} = :am AND {IsUsing} = true AND {STType} = :t "
                            + "ORDER BY {CreateTime} DESC", p("a", modal.s("Asn"), "am", amount, "t", pay));
                        if (tr == null) {
                            throw fail("Virement correspondant introuvable : annulation impossible");
                        }
                        Row acc = c.accounts();
                        acc.put("Balance", acc.d("Balance") - amount.doubleValue());
                        db.update("CustomerAccounts", acc);
                        tr.put("IsUsing", false);
                        db.update("SaveTransfer", tr);
                        deleteLog(acc.s("AccountNo"), amount2, 16, modal.s("Asn"));
                        break;
                    }
                    case 25: {
                        CenterDeal.GroupCard g = deal.getGroupCardInfo(modal.s("Asn"));
                        if (!g.ok()) {
                            throw fail(g.error());
                        }
                        Row tr = db.first("SaveTransfer", "{SaveToAsn} = :a AND {Amount} = :am AND {IsUsing} = true AND {STType} = :t "
                            + "ORDER BY {CreateTime} DESC", p("a", modal.s("Asn"), "am", amount, "t", pay));
                        if (tr == null) {
                            throw fail("Virement correspondant introuvable : annulation impossible");
                        }
                        Row ga = g.accounts();
                        ga.put("Balance", ga.d("Balance") - amount.doubleValue());
                        db.update("GroupAccounts", ga);
                        tr.put("IsUsing", false);
                        db.update("SaveTransfer", tr);
                        deleteLog(ga.s("AccountNo"), amount2, 19, modal.s("Asn"));
                        break;
                    }
                    case 20: {
                        CenterDeal.CusCard c = deal.getCusCardInfo(modal.s("Asn"));
                        if (!c.ok()) {
                            throw fail(c.error());
                        }
                        Row tr = db.first("SaveTransfer", "{Asn} = :a AND {SaveToAsn} = :to AND {Amount} = :am AND {IsUsing} = false "
                            + "AND {STType} = :t ORDER BY {CreateTime} DESC", p("a", modal.s("Asn"), "to", modal.s("SToAsn"), "am", amount, "t", pay));
                        if (tr == null) {
                            throw fail("Virement correspondant introuvable : annulation impossible");
                        }
                        Row acc = c.accounts();
                        acc.put("Balance", acc.d("Balance") + amount.doubleValue());
                        db.update("CustomerAccounts", acc);
                        db.deleteEntity("SaveTransfer", tr);
                        deleteLog(acc.s("AccountNo"), amount2, 18, modal.s("Asn"));
                        break;
                    }
                    default:
                        throw fail("Type de paiement non pris en charge pour l'annulation");
                }
                tx.commit();
                return ret(true, "Annulation effectuée", 0);
            } catch (Exception ex) {
                tx.rollback();
                return ret(false, ex.getMessage(), 0);
            }
        }
    }

    private void deleteLog(String accountNo, double amount, int type, String asn) {
        Row l = db.first("AccountServiceLogs", "{AccountNo} = :n AND {Amount} = :a AND {TransactionTypeID} = :t AND {ASN} = :asn "
            + "ORDER BY {AccountDatetime} DESC", p("n", accountNo, "a", amount, "t", type, "asn", asn));
        if (l == null) {
            throw fail("Journal de l'opération d'origine introuvable : suppression impossible");
        }
        db.deleteEntity("AccountServiceLogs", l);
    }

    @RequestMapping("/GetTransferInfo")
    public String GetTransferInfo(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            return okJson(ent("SaveTransfer", db.list("SaveTransfer", "{Asn} = :a AND {IsUsing} = false", p("a", modal.str("Mess")))));
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    private String transBack(String body, boolean cancel) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal2 = req(body);
                Row modal = modal2.row("TargetTransfer");
                Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", modal2.i("EmpID")));
                Row tr = db.first("SaveTransfer", "{Amount} = :am AND {Asn} = :a AND {SaveToAsn} = :to AND {STType} = :t "
                    + "AND {GroupID} = :g AND {IsUsing} = :u", p("am", modal.dec("Amount"), "a", modal.s("Asn"),
                    "to", modal.s("SaveToAsn"), "t", modal.i("STType"), "g", modal.i("GroupID"), "u", cancel || modal.b("IsUsing")));
                tr.put("IsUsing", !cancel);
                CenterDeal.GroupCard g = deal.getGroupCardInfo(modal.s("Asn"));
                if (!g.ok()) {
                    throw fail(g.error());
                }
                double amount2 = Conv.round(modal.d("Amount"), 2);
                Row ga = g.accounts();
                ga.put("Balance", ga.d("Balance") + (cancel ? -amount2 : amount2));
                ga.put("UpdateDatetime", LocalDateTime.now());
                db.update("SaveTransfer", tr);
                db.update("GroupAccounts", ga);
                deal.addAccountLog(ga.s("AccountNo"), amount2, modal.s("Asn"), ga.d("Balance"), cancel ? 19 : 17, BigDecimal.ZERO, 0.0,
                    emp, 1, nowStr(), emp.i("StationID"));
                tx.commit();
                return ret(true, null, 0);
            } catch (Exception ex) {
                tx.rollback();
                return ret(false, ex.getMessage(), 0);
            }
        }
    }

    @RequestMapping("/TransBack")
    public String TransBack(@RequestBody(required = false) String body) {
        return transBack(body, false);
    }

    @RequestMapping("/TransBackCancel")
    public String TransBackCancel(@RequestBody(required = false) String body) {
        return transBack(body, true);
    }

    @RequestMapping("/GetSTranhistory")
    public String GetSTranhistory(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            requireStation(modal);
            String[] a = modal.s("Mess").split("\\|", -1);
            String ano = a[0];
            boolean flag = true;
            LocalDateTime dts = null;
            LocalDateTime dte = null;
            try {
                dts = Conv.toDateTime(a[1]);
                dte = Conv.toDateTime(a[2]);
            } catch (RuntimeException e) {
                flag = false;
            }
            List<Row> list;
            int total = 0;
            if (!flag) {
                list = db.list("SaveTransfer", "{IsUsing} = true AND {Asn} = :a", p("a", ano));
            } else {
                Sugar.Page pg = db.page("SaveTransfer", "{IsUsing} = true AND {Asn} = :a AND " + safeTs("create_time")
                    + " >= :s AND " + safeTs("create_time") + " <= :e", p("a", ano, "s", dts, "e", dte), null, modal.i("Start"), 50);
                list = pg.rows();
                total = pg.total();
            }
            if (list.isEmpty()) {
                throw fail("Aucun enregistrement");
            }
            return okJson(ent("SaveTransfer", list), total);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCustomerName")
    public String GetCustomerName(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            requireStation(modal);
            String name = modal.s("Mess").split("\\|", -1)[0];
            List<String> out = new ArrayList<>();
            out.addAll(db.jdbc().queryForList("SELECT asn FROM customer_card_infos WHERE customer_id IN "
                + "(SELECT customer_id FROM customers WHERE name = :n)", p("n", name), String.class));
            out.addAll(db.jdbc().queryForList("SELECT asn FROM employee_card_infos WHERE employee_id IN "
                + "(SELECT employee_id FROM employees WHERE name = :n)", p("n", name), String.class));
            out.addAll(db.jdbc().queryForList("SELECT asn FROM group_card_infos WHERE group_id IN "
                + "(SELECT group_id FROM groups WHERE name = :n)", p("n", name), String.class));
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetSTranMess")
    public String GetSTranMess(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            requireStation(modal);
            Row tr = db.first("SaveTransfer", "{IsUsing} = false AND {SaveToAsn} = :a", p("a", modal.str("Mess")));
            if (tr == null) {
                throw fail("Aucun enregistrement");
            }
            return okJson(ent("SaveTransfer", tr));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/MasterGroupBalanceChange")
    public String MasterGroupBalanceChange(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("Code")));
                if (st == null) {
                    throw fail("Requête illégale");
                }
                String asn = modal.s("Asn");
                String prefix = asn.substring(0, 6);
                String accountNo;
                double before;
                double delta = Conv.round(modal.dec("Amount").add(modal.dec("DisAmount")).doubleValue(), 3);
                LocalDateTime now = LocalDateTime.now();
                if (prefix.equals("010011") || !prefix.equals("010014")) {
                    CenterDeal.CusCard c = deal.getCusCardInfo(asn);
                    if (!c.ok()) {
                        throw fail(c.error());
                    }
                    accountNo = c.cardInfos().s("AccountNo");
                    before = c.accounts().d("Balance");
                    c.accounts().put("Balance", before + delta);
                    c.accounts().put("UpdateDatetime", now);
                    c.accounts().put("DepositDatetime", now);
                    db.update("CustomerAccounts", c.accounts());
                } else {
                    CenterDeal.GroupCard g = deal.getGroupCardInfo(asn);
                    if (!g.ok()) {
                        throw fail(g.error());
                    }
                    accountNo = g.accounts().s("AccountNo");
                    before = g.accounts().d("Balance");
                    g.accounts().put("Balance", before + delta);
                    g.accounts().put("UpdateDatetime", now);
                    g.accounts().put("DepositDatetime", now);
                    db.update("GroupAccounts", g.accounts());
                }
                db.insert("AccountServiceLogs", Row.of("AccountDatetime", modal.get("DT"), "AccountNo", accountNo,
                    "Amount", modal.d("Amount"), "ASN", asn, "Balance", before + modal.dec("Amount").add(modal.dec("DisAmount")).doubleValue(),
                    "EmployeeID", modal.i("EmpID"), "FeeAmount", modal.d("DisAmount"), "PaymentID", modal.i("PayType") + 1,
                    "ProcessCode", "", "StationID", st.i("StationID"), "TerminalNo", "No Card", "TransactionTypeID", 2,
                    "TransDatetime", modal.get("DT"), "CardTypeID", 0));
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/BalanceBack")
    public String BalanceBack(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                requireStation(modal);
                Row l = db.first("AccountServiceLogs", "{ID} = :id", p("id", Conv.toInt(modal.str("Mess"))));
                if (l == null) {
                    throw fail("Requête illégale");
                }
                Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :id", p("id", Conv.toInt(l.str("AccountNo"))));
                if (acc == null) {
                    throw fail("Erreur interne");
                }
                acc.put("Balance", acc.d("Balance") - (l.d("Amount") + l.d("FeeAmount")));
                db.update("CustomerAccounts", acc);
                db.deleteEntity("AccountServiceLogs", l);
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    // ------------------------------------------------------------ factures de crédit

    @RequestMapping("/GetCreditPay")
    public String GetCreditPay(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            requireStation(modal);
            Sugar.Page pg = db.page("CreditBill", null, null, "{Statue}, {CreateDate} DESC", modal.i("Start"), 10);
            List<Row> out = new ArrayList<>();
            for (Row b : pg.rows()) {
                out.add(deal.dto().make("CreateBillShow", "ID", b.i("ID"), "GroupID", b.i("GroupID"),
                    "GroupName", deal.getGroupName(b.i("GroupID")), "Amount", b.dec("Amount"), "RepayAmount", b.dec("RepayAmount"),
                    "Statue", b.i("Statue"), "BillDate", b.get("BillDate"), "Asn", b.get("Asn")));
            }
            return okJson(out, pg.totalPage());
        } catch (Exception ex) {
            return "[]";
        }
    }

    @RequestMapping("/GetBillRepay")
    public String GetBillRepay(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            requireStation(modal);
            Row b = db.first("CreditBill", "{ID} = :id", p("id", Conv.toInt(modal.str("Mess"))));
            if (b == null) {
                throw fail("Error");
            }
            return okJson(deal.dto().make("RepayBillDetail", "ID", b.i("ID"), "GroupName", deal.getGroupName(b.i("GroupID")),
                "Amount", b.dec("Amount"), "Asn", b.get("Asn"), "BillDate", b.get("BillDate"), "RepayAmount", b.dec("RepayAmount"),
                "Statue", b.i("Statue")));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/DealBillRepay")
    public String DealBillRepay(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                requireStation(modal);
                Row bill = db.first("CreditBill", "{ID} = :id", p("id", modal.i("Start")));
                if (bill == null) {
                    throw fail("La facture n'existe pas");
                }
                CenterDeal.GroupCard g = deal.getGroupCardInfo(bill.s("Asn"));
                if (!g.ok()) {
                    throw fail(g.error());
                }
                BigDecimal result;
                try {
                    result = new BigDecimal(modal.s("Mess").trim());
                } catch (NumberFormatException e) {
                    result = null;
                }
                if (result == null || result.compareTo(bill.dec("Amount")) > 0) {
                    throw fail("Montant erroné");
                }
                LocalDateTime now = LocalDateTime.now();
                g.accounts().put("Balance", g.accounts().d("Balance") + Conv.round(result.doubleValue(), 3));
                g.accounts().put("UpdateDatetime", now);
                g.cardInfos().put("UpdateDate", now);
                db.update("GroupAccounts", g.accounts());
                db.update("GroupCardInfos", g.cardInfos());
                List<Row> items = db.list("CreateBillData", "{CreateBillID} = :b", p("b", bill.i("ID")));
                BigDecimal remaining = bill.dec("Amount").subtract(bill.dec("RepayAmount"));
                if (result.compareTo(remaining) < 0) {
                    for (Row item : items) {
                        CenterDeal.CusCard c = deal.getCusCardInfo(item.s("Asn"));
                        if (c.ok()) {
                            BigDecimal num = item.dec("Amount").subtract(item.dec("RepayAmount"))
                                .multiply(result.divide(remaining, 20, RoundingMode.HALF_EVEN));
                            c.accounts().put("Balance", c.accounts().d("Balance") + Conv.round(num.doubleValue(), 3));
                            c.accounts().put("UpdateDatetime", now);
                            item.put("Statue", 1);
                            item.put("RepayAmount", item.dec("RepayAmount").add(num.setScale(3, RoundingMode.HALF_EVEN)));
                            item.put("UpdateTime", nowStr());
                            db.update("CustomerAccounts", c.accounts());
                            db.update("CreateBillData", item);
                        }
                    }
                    bill.put("RepayAmount", bill.dec("RepayAmount").add(result));
                    bill.put("Statue", 1);
                    bill.put("UpdateDate", nowStr());
                    db.update("CreditBill", bill);
                } else {
                    bill.put("RepayAmount", bill.dec("Amount"));
                    bill.put("Statue", 2);
                    bill.put("UpdateDate", nowStr());
                    db.update("CreditBill", bill);
                    for (Row item : items) {
                        CenterDeal.CusCard c = deal.getCusCardInfo(item.s("Asn"));
                        if (c.ok()) {
                            c.accounts().put("Balance", c.accounts().d("Balance")
                                + Conv.round(item.dec("Amount").subtract(item.dec("RepayAmount")).doubleValue(), 3));
                            c.accounts().put("UpdateDatetime", now);
                            item.put("Statue", 2);
                            item.put("RepayAmount", item.dec("Amount"));
                            item.put("UpdateTime", nowStr());
                            db.update("CustomerAccounts", c.accounts());
                            db.update("CreateBillData", item);
                        }
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
}
