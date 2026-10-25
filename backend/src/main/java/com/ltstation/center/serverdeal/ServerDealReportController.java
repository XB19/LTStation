package com.ltstation.center.serverdeal;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ServerDealController d'origine (6/…) : journaux des cartes, synthèse d'activité, recherche
 * paginée des cartes, rapports de soldes (utilisateurs, employés, dépôts) et relevés de comptes.
 */
@RestController
@RequestMapping("/ServerDeal")
public class ServerDealReportController extends ServerDealBase {

    private static final String TS = "trans_datetime";

    public ServerDealReportController(@Qualifier("centerSugar") Sugar db, CenterDeal deal) {
        super(db, deal);
    }

    private void requireStation(Row modal) {
        if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
            throw fail("Requête illégale");
        }
    }

    private static double r3(double d) {
        return Conv.round(d, 3);
    }

    private static BigDecimal r3(BigDecimal d) {
        return d.setScale(3, RoundingMode.HALF_EVEN);
    }

    /** Comptes concernés par un numéro de carte et/ou une immatriculation (GetCardLog / GetCardLogTotal). */
    private List<String> accounts(String ano, String carNo, String[] name, boolean addAlways) {
        List<String> list = new ArrayList<>();
        if (!ano.isEmpty()) {
            switch (ano.substring(0, 6)) {
                case "010004": {
                    Row emp = db.first("EmployeeCardInfos", "{ASN} = :a", p("a", ano));
                    if (emp != null) {
                        ano = String.valueOf(emp.i("CustomerAccountID"));
                        Row e = db.first("Employees", "{EmployeeID} = :e", p("e", emp.i("EmployeeID")));
                        if (e != null) {
                            name[0] = e.s("Name");
                        }
                        list.add(ano);
                    } else if (addAlways) {
                        list.add(ano);
                    }
                    break;
                }
                case "010013", "010011", "010001": {
                    Row info = db.first("CustomerCardInfos", "{ASN} = :a", p("a", ano));
                    if (info != null) {
                        ano = String.valueOf(info.i("CustomerAccountID"));
                        Row c = db.first("Customers", "{CustomerID} = :c", p("c", info.i("CustomerID")));
                        if (c != null) {
                            name[0] = c.s("Name");
                        }
                        list.add(ano);
                    } else if (addAlways) {
                        list.add(ano);
                    }
                    break;
                }
                case "010014", "010012", "010002": {
                    Row info = db.first("GroupCardInfos", "{ASN} = :a", p("a", ano));
                    if (info == null) {
                        throw fail("L'utilisateur n'existe pas");
                    }
                    ano = String.valueOf(info.i("GroupAccountID"));
                    Row g = db.first("Groups", "{GroupID} = :g", p("g", info.i("GroupID")));
                    if (g != null) {
                        name[0] = g.s("Name");
                    }
                    list.addAll(db.jdbc().queryForList("SELECT account_no FROM customer_card_infos WHERE group_id = :g",
                        p("g", g == null ? -1 : g.i("GroupID")), String.class));
                    list.add(ano);
                    break;
                }
                default:
                    break;
            }
        }
        if (!carNo.isEmpty()) {
            for (Integer id : db.jdbc().queryForList("SELECT customer_id FROM customers WHERE vehicle_no = :v", p("v", carNo),
                Integer.class)) {
                list.add(String.valueOf(id));
            }
        }
        return list;
    }

    private static List<String> nonEmpty(List<String> l) {
        return l.isEmpty() ? java.util.Collections.singletonList(null) : l;
    }

    @RequestMapping("/GetCardLog")
    public String GetCardLog(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            requireStation(modal);
            String[] a = modal.s("Mess").split("\\|", -1);
            List<String> list = accounts(a[0], a[3], new String[] {""}, false);
            boolean flag = true;
            LocalDateTime dts = LocalDateTime.now().minusMonths(1);
            LocalDateTime dte = LocalDateTime.now();
            try {
                dts = Conv.toDateTime(a[1]);
                dte = Conv.toDateTime(a[2]);
            } catch (RuntimeException e) {
                flag = false;
            }
            int type = modal.i("TransactionTypeID");
            String base = "{AccountNo} IN (:acc)" + (type > 0 ? " AND {TransactionTypeID} = :t" : "");
            Map<String, Object> ps = p("acc", nonEmpty(list), "t", type, "s", dts, "e", dte);
            if (type == 10 || type == 18) {
                List<Row> transfers = new ArrayList<>();
                for (Row l : db.list("AccountServiceLogs", base + " ORDER BY {TransDatetime}", ps)) {
                    transfers.addAll(db.list("SaveTransfer", "{Asn} = :a AND {CreateTime} = :c",
                        p("a", l.str("ASN"), "c", l.get("TransDatetime"))));
                }
                final LocalDateTime fs = dts;
                final LocalDateTime fe = dte;
                List<Row> filtered = transfers.stream().filter(t -> {
                    LocalDateTime c = Conv.toDateTime(t.get("CreateTime"));
                    return !c.isBefore(fs) && !c.isAfter(fe);
                }).toList();
                int count = filtered.size();
                if (flag) {
                    int start = modal.i("Start");
                    List<Row> pageRows = filtered.stream().skip(Math.max(0, (long) (start - 1) * 50)).limit(50).toList();
                    return okJson(ent("SaveTransfer", new ArrayList<>(pageRows)), count);
                }
                return okJson(ent("SaveTransfer", transfers), count);
            }
            List<Row> logs;
            int total = 0;
            if (!flag) {
                logs = db.list("AccountServiceLogs", base + " ORDER BY {TransDatetime}", ps);
            } else {
                Sugar.Page pg = db.page("AccountServiceLogs", base + " AND " + TS + " >= :s AND " + TS + " <= :e", ps,
                    "{TransDatetime}", modal.i("Start"), 50);
                logs = pg.rows();
                total = pg.total();
            }
            for (Row l : logs) {
                l.put("ProcessCode", deal.getICName(l.s("ASN")));
            }
            return okJson(ent("AccountServiceLogs", logs), total);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCardLogTotal")
    public String GetCardLogTotal(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            String[] a = modal.s("Mess").split("\\|", -1);
            requireStation(modal);
            String[] name = {""};
            List<String> list = accounts(a[0], a[3], name, true);
            LocalDateTime dts = Conv.toDateTime(a[1]);
            LocalDateTime dte = Conv.toDateTime(a[2]);
            List<Row> rows = db.query("SELECT transaction_type_id, sum(amount) AS amount, sum(fee_amount) AS fee_amount, "
                + "sum(discount_amount) AS discount_amount FROM account_service_logs WHERE account_no IN (:acc) AND " + TS + " >= :s AND "
                + TS + " <= :e GROUP BY account_no, transaction_type_id", p("acc", nonEmpty(list), "s", dts, "e", dte));
            List<Row> out = new ArrayList<>();
            for (Row r : rows) {
                out.add(Row.of("AName", name[0], "TransactionTypeID", r.i("transaction_type_id"), "Amount", Conv.toDouble(r.get("amount")),
                    "FeeAmount", Conv.toDouble(r.get("fee_amount")), "DiscountAmount", Conv.toDecimal(r.get("discount_amount"))));
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/BussinessGetMess")
    public String BussinessGetMess(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            requireStation(modal);
            List<Integer> stations = modal.i("CompanyID") != 0
                ? db.jdbc().queryForList("SELECT station_id FROM stations WHERE company_id = :c", p("c", modal.i("CompanyID")), Integer.class)
                : db.jdbc().queryForList("SELECT station_id FROM stations", Map.of(), Integer.class);
            StringBuilder w = new StringBuilder("station_id IN (:st) AND " + TS + " >= :s AND " + TS + " <= :e");
            String prefix = switch (modal.i("SelectIndex")) {
                case 1 -> "010004";
                case 2 -> "010001";
                case 3 -> "010012";
                case 4 -> "010002";
                case 5 -> "010014";
                default -> null;
            };
            if (prefix != null) {
                w.append(" AND left(asn, 6) = '").append(prefix).append("'");
            }
            Integer tt = switch (modal.i("OSelectIndex")) {
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 11;
                default -> null;
            };
            if (tt != null) {
                w.append(" AND transaction_type_id = ").append(tt);
            }
            Map<String, Object> ps = p("st", stations.isEmpty() ? List.of(-2147483648) : stations, "s", modal.dt("Dts"), "e", modal.dt("DtE"));
            List<Row> sums = db.query("SELECT transaction_type_id, sum(amount) AS sa, sum(fee_amount) AS sv, sum(discount_amount) AS sd "
                + "FROM account_service_logs WHERE " + w + " GROUP BY transaction_type_id", ps);
            Sugar.Page pg = db.page("AccountServiceLogs", w.toString(), ps, "{TransDatetime}", modal.i("PageIndex"), 50);
            Row out = deal.dto().make("BussinessMessList");
            for (Row s : sums) {
                BigDecimal sa = Conv.toDecimal(s.get("sa"));
                switch (s.i("transaction_type_id")) {
                    case 2 -> {
                        out.put("totalSave", r3(sa));
                        out.put("totalDisAmount", r3(Conv.toDecimal(s.get("sv")).add(Conv.toDecimal(s.get("sd")))));
                    }
                    case 11 -> {
                        out.put("totalAmount", r3(sa));
                        out.put("totalVolume", r3(Conv.toDecimal(s.get("sv"))));
                    }
                    case 12 -> out.put("totalUnSave", r3(sa));
                    case 13 -> out.put("totalNotFuelAmount", r3(sa));
                    default -> {
                    }
                }
            }
            out.put("totalNumber", pg.total());
            out.put("totalPage", pg.totalPage());
            out.put("list", ent("AccountServiceLogs", pg.rows()));
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCardMessagePage")
    public String GetCardMessagePage(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row out = deal.dto().make("CardGotList");
            List<Row> data = new ArrayList<>();
            String name = modal.s("Name");
            String phone = modal.s("Phone");
            Map<String, Object> ps = p("n", name, "ph", phone);
            switch (modal.i("CardType")) {
                case 1: {
                    List<Row> emps = db.list("Employees", filter(name, phone, "name", "telephone"), ps);
                    Sugar.Page pg = db.page("EmployeeCardInfos", "{EmployeeID} IN (:ids)", p("ids", ids(emps, "EmployeeID")), null,
                        modal.i("PageIndex"), 20);
                    for (Row info : pg.rows()) {
                        Row e = find(emps, "EmployeeID", info.i("EmployeeID"));
                        Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :a", p("a", info.i("CustomerAccountID")));
                        data.add(deal.dto().make("CardGotReturn", "Name", e != null ? e.get("Name") : "-1",
                            "Phone", e != null ? e.get("Telephone") : "-1", "CustomerID", e != null ? e.i("EmployeeID") : -1, "CarNo", "",
                            "Balance", acc != null ? acc.dec("Balance") : BigDecimal.valueOf(-1), "Asn", info.get("ASN")));
                    }
                    out.put("TotalCount", pg.total());
                    out.put("TotalPage", pg.totalPage());
                    break;
                }
                case 2: {
                    List<Row> cus = db.list("Customers", filter(name, phone, "name", "telephone"), ps);
                    Sugar.Page pg = db.page("CustomerCardInfos", "{CustomerID} IN (:ids)", p("ids", ids(cus, "CustomerID")), null,
                        modal.i("PageIndex"), 20);
                    for (Row info : pg.rows()) {
                        Row c = find(cus, "CustomerID", info.i("CustomerID"));
                        Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :a", p("a", info.i("CustomerAccountID")));
                        data.add(deal.dto().make("CardGotReturn", "Name", c != null ? c.get("Name") : "-1",
                            "Phone", c != null ? c.get("Telephone") : "-1", "CustomerID", c != null ? c.i("CustomerID") : -1,
                            "CarNo", c != null ? c.get("VehicleNo") : null,
                            "Balance", acc != null ? acc.dec("Balance") : BigDecimal.valueOf(-1), "Asn", info.get("ASN")));
                    }
                    out.put("TotalCount", pg.total());
                    out.put("TotalPage", pg.totalPage());
                    break;
                }
                case 3: {
                    StringBuilder w = new StringBuilder("1=1");
                    if (!name.isEmpty()) {
                        w.append(" AND (name = :n OR short_name = :n)");
                    }
                    if (!phone.isEmpty()) {
                        w.append(" AND telephone = :ph");
                    }
                    List<Row> groups = db.list("Groups", w.toString(), ps);
                    Sugar.Page pg = db.page("GroupCardInfos", "{GroupID} IN (:ids)", p("ids", ids(groups, "GroupID")), null,
                        modal.i("PageIndex"), 20);
                    for (Row info : pg.rows()) {
                        Row g = find(groups, "GroupID", info.i("GroupID"));
                        Row acc = db.first("GroupAccounts", "{GroupAccountID} = :a", p("a", info.i("GroupAccountID")));
                        data.add(deal.dto().make("CardGotReturn", "Name", g != null ? g.get("Name") : "-1",
                            "Phone", g != null ? g.get("Telephone") : "-1", "CustomerID", g != null ? g.i("GroupID") : -1, "CarNo", "",
                            "Balance", acc != null ? acc.dec("Balance") : BigDecimal.valueOf(-1), "Asn", info.get("ASN")));
                    }
                    out.put("TotalCount", pg.total());
                    out.put("TotalPage", pg.totalPage());
                    break;
                }
                default:
                    break;
            }
            out.put("data", data);
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    private static String filter(String name, String phone, String nameCol, String phoneCol) {
        StringBuilder w = new StringBuilder("1=1");
        if (!name.isEmpty()) {
            w.append(" AND ").append(nameCol).append(" = :n");
        }
        if (!phone.isEmpty()) {
            w.append(" AND ").append(phoneCol).append(" = :ph");
        }
        return w.toString();
    }

    private static List<Integer> ids(List<Row> rows, String col) {
        List<Integer> l = new ArrayList<>();
        rows.forEach(r -> l.add(r.i(col)));
        return l.isEmpty() ? List.of(-2147483648) : l;
    }

    private static Row find(List<Row> rows, String col, int id) {
        return rows.stream().filter(r -> r.i(col) == id).findFirst().orElse(null);
    }

    // ------------------------------------------------------------ rapports de soldes

    private static final String CUS = "SELECT c.customer_id, c.name, a.customer_account_id, i.asn, i.station_id FROM customers c, "
        + "customer_card_infos i, customer_accounts a WHERE i.card_status_type_id = 0 AND c.customer_id = i.customer_id "
        + "AND i.customer_account_id = a.customer_account_id AND (left(i.asn, 6) = '010001' OR left(i.asn, 6) = 'FFFFFF')";
    private static final String EMP = "SELECT e.station_id, e.employee_id, e.name, a.customer_account_id, i.asn FROM employees e, "
        + "employee_card_infos i, customer_accounts a WHERE i.card_status_type_id = 0 AND e.employee_id = i.employee_id "
        + "AND i.customer_account_id = a.customer_account_id AND left(i.asn, 6) = '010004'";

    /** Solde au dernier mouvement avant (ou à) une date, par compte. */
    private static String balanceAt(String base, String keyCols) {
        return "SELECT " + keyCols + ", a.account_no, coalesce(a.balance, 0) AS balance FROM (" + base + ") cus LEFT JOIN "
            + "(SELECT c.account_no, c.balance, c.trans_datetime FROM account_service_logs c, (SELECT account_no, max(trans_datetime) AS mt "
            + "FROM account_service_logs WHERE trans_datetime <= :at AND left(account_no, 1) <> '0' GROUP BY account_no) d "
            + "WHERE c.account_no = d.account_no AND c.trans_datetime = d.mt) a ON cus.customer_account_id::text = a.account_no";
    }

    /** Sommes d'un type d'opération entre deux dates, par compte. */
    private static String sums(String base, String keyCols, int type, boolean withDiscount) {
        return "SELECT " + keyCols + ", coalesce(d.amn, 0) AS amn, coalesce(d.famount, 0) AS famount FROM (" + base + ") cus LEFT JOIN "
            + "(SELECT account_no, sum(amount) AS amn, sum(fee_amount" + (withDiscount ? " + discount_amount" : "") + ") AS famount "
            + "FROM account_service_logs WHERE transaction_type_id = " + type + " AND trans_datetime BETWEEN :s AND :e "
            + "AND left(account_no, 1) <> '0' GROUP BY account_no) d ON cus.customer_account_id::text = d.account_no";
    }

    @RequestMapping("/GetUserCardSummary")
    public String GetUserCardSummary(@RequestBody(required = false) String body) {
        Row cp = req(body);
        String[] a = cp.s("Mess").split("\\|", -1);
        String s = a[0];
        String e = a[1];
        int num = Integer.parseInt(a[2]);
        try {
            if (s.isEmpty() || e.isEmpty()) {
                return ret(false, "Les dates de début et de fin sont obligatoires", 0);
            }
            String keys = "cus.station_id, cus.name, cus.customer_id, cus.asn";
            Map<String, Object> ps = p("s", Conv.toDateTime(s), "e", Conv.toDateTime(e), "at", Conv.toDateTime(s));
            List<Row> ini = db.query(balanceAt(CUS, keys) + " ORDER BY cus.customer_id", ps);
            List<Row> save = db.query(sums(CUS, keys, 2, true) + " ORDER BY cus.customer_id", ps);
            List<Row> charge = db.query(sums(CUS, keys, 12, true) + " ORDER BY cus.customer_id", ps);
            List<Row> use = db.query(sums(CUS, keys, 11, false) + " ORDER BY cus.customer_id", ps);
            List<Row> transfer = db.query(sums(CUS, keys, 16, false) + " ORDER BY cus.customer_id", ps);
            List<Row> notFuel = db.query(sums(CUS, keys, 13, true) + " ORDER BY cus.customer_id", ps);
            List<Row> fin = db.query(balanceAt(CUS, keys) + " ORDER BY cus.customer_id", p("at", Conv.toDateTime(e)));
            Row list = deal.dto().make("WxMobileWeb.Controllers.UserReportList");
            List<Row> data = new ArrayList<>();
            Row total = deal.dto().make("WxMobileWeb.Controllers.UserTotalReport");
            if (!ini.isEmpty()) {
                for (int i = 0; i < ini.size(); i++) {
                    double iniB = ini.get(i).d("balance");
                    double finB = fin.get(i).d("balance");
                    Row r = deal.dto().make("WxMobileWeb.Controllers.UserTotalReport",
                        "Name", ini.get(i).get("name"), "AccountNo", Conv.toStr(ini.get(i).get("customer_id")), "Asn", ini.get(i).get("asn"),
                        "IniBalance", dec3(iniB), "SaveAmount", dec3(save.get(i).d("amn")), "FreeAmount", dec3(save.get(i).d("famount")),
                        "ChargeAmount", dec3(charge.get(i).d("amn") + charge.get(i).d("famount")),
                        "TransferAmount", dec3(transfer.get(i).d("amn") + transfer.get(i).d("famount")),
                        "NotFuelAmount", dec3(notFuel.get(i).d("amn") + notFuel.get(i).d("famount")),
                        "Amount", dec3(use.get(i).d("amn")), "Volume", dec3(use.get(i).d("famount")), "FinalBalacne", dec3(finB),
                        "Error", dec3(iniB + save.get(i).d("amn") + save.get(i).d("famount") + charge.get(i).d("amn")
                            + charge.get(i).d("famount") + transfer.get(i).d("amn") + transfer.get(i).d("famount") - use.get(i).d("amn")
                            + notFuel.get(i).d("amn") + notFuel.get(i).d("famount") - finB));
                    data.add(r);
                    accumulate(total, r, "IniBalance", "SaveAmount", "FreeAmount", "ChargeAmount", "Amount", "TransferAmount",
                        "NotFuelAmount", "Volume", "FinalBalacne", "Error");
                }
                data.add(total);
            }
            int count = data.size();
            list.put("data", num > 0 ? new ArrayList<>(data.stream().skip((long) (num - 1) * 50).limit(50).toList()) : data);
            return okJson(list, count);
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    private static BigDecimal dec3(double d) {
        return Conv.toDecimal(d).setScale(3, RoundingMode.HALF_EVEN);
    }

    private static void accumulate(Row total, Row r, String... cols) {
        for (String c : cols) {
            total.put(c, total.dec(c).add(r3(r.dec(c))));
        }
    }

    @RequestMapping("/GetEmployeeSummary")
    public String GetEmployeeSummary(@RequestBody(required = false) String body) {
        Row cp = req(body);
        String[] a = cp.s("Mess").split("\\|", -1);
        try {
            String s = a[0];
            String e = a[1];
            int num = Integer.parseInt(a[2]);
            String keys = "cus.station_id, cus.name, cus.employee_id, cus.asn";
            Map<String, Object> ps = p("s", Conv.toDateTime(s), "e", Conv.toDateTime(e), "at", Conv.toDateTime(s));
            List<Row> ini = db.query(balanceAt(EMP, keys) + " ORDER BY cus.asn", ps);
            List<Row> save = db.query(sums(EMP, keys, 2, true) + " ORDER BY cus.asn", ps);
            List<Row> charge = db.query(sums(EMP, keys, 12, true) + " ORDER BY cus.asn", ps);
            List<Row> transfer = db.query(sums(EMP, keys, 16, true) + " ORDER BY cus.asn", ps);
            List<Row> notFuel = db.query(sums(EMP, keys, 13, true) + " ORDER BY cus.asn", ps);
            List<Row> use = db.query(sums(EMP, keys, 11, true) + " ORDER BY cus.asn", ps);
            List<Row> fin = db.query(balanceAt(EMP, keys) + " ORDER BY cus.asn", p("at", Conv.toDateTime(e)));
            Row list = deal.dto().make("WxMobileWeb.Controllers.EmpReportList");
            List<Row> data = new ArrayList<>();
            Row total = deal.dto().make("WxMobileWeb.Controllers.EmpTotalReport");
            for (int i = 0; i < ini.size(); i++) {
                double iniB = ini.get(i).d("balance");
                double finB = fin.get(i).d("balance");
                Row r = deal.dto().make("WxMobileWeb.Controllers.EmpTotalReport",
                    "StationName", deal.getStationName(ini.get(i).i("station_id")), "Name", ini.get(i).get("name"),
                    "AccountNo", Conv.toStr(ini.get(i).get("employee_id")), "Asn", ini.get(i).get("asn"), "IniBalance", dec3(iniB),
                    "SaveAmount", dec3(save.get(i).d("amn")), "ChargeAmount", dec3(charge.get(i).d("amn")),
                    "FreeAmount", dec3(save.get(i).d("famount")), "TransferAmount", dec3(transfer.get(i).d("amn") + transfer.get(i).d("famount")),
                    "NotFuelAmount", dec3(notFuel.get(i).d("amn") + notFuel.get(i).d("famount")), "Amount", dec3(use.get(i).d("amn")),
                    "Volume", dec3(use.get(i).d("famount")), "FinalBalacne", dec3(finB),
                    "Error", dec3(iniB + save.get(i).d("amn") + save.get(i).d("famount") + charge.get(i).d("amn") + transfer.get(i).d("amn")
                        + transfer.get(i).d("famount") - use.get(i).d("amn") - finB + notFuel.get(i).d("amn") + notFuel.get(i).d("famount")));
                data.add(r);
                accumulate(total, r, "IniBalance", "SaveAmount", "ChargeAmount", "FreeAmount", "Amount", "TransferAmount",
                    "NotFuelAmount", "Volume", "FinalBalacne", "Error");
            }
            int count = data.size();
            data.add(total);
            list.put("data", num > 0 ? new ArrayList<>(data.stream().skip((long) (num - 1) * 50).limit(50).toList()) : data);
            return okJson(list, count);
        } catch (Exception ex) {
            return ret(true, enc(com.ltstation.center.legacy.Wire.json(ex.getMessage())), 0);
        }
    }

    @RequestMapping("/GetSedimentaryReport")
    public String GetSedimentaryReport(@RequestBody(required = false) String body) {
        Row list = deal.dto().make("WxMobileWeb.Controllers.UserReportList");
        try {
            Row cp = req(body);
            String[] a = cp.s("Mess").split("\\|", -1);
            String end = a[1];
            int num = Integer.parseInt(a[2]);
            if (end.isEmpty()) {
                return ret(true, enc(com.ltstation.center.legacy.Wire.json("La date de fin est obligatoire")), 0);
            }
            List<Row> rows = db.query("SELECT b.station_id, b.name, b.asn, b.customer_id, b.customer_account_id, b.account_no, "
                + "coalesce(c.balance, 0) AS balance, c.create_time FROM (SELECT cu.customer_id, cu.name, i.asn, i.customer_account_id, "
                + "i.account_no, i.station_id FROM customers cu, customer_card_infos i WHERE cu.customer_id = i.customer_id "
                + "AND i.card_status_type_id = 0 AND left(i.asn, 6) = '010001') b LEFT JOIN (SELECT l.account_no, l.balance, a.create_time "
                + "FROM account_service_logs l, (SELECT account_no, max(trans_datetime) AS create_time FROM account_service_logs "
                + "WHERE trans_datetime < :e GROUP BY account_no) a WHERE l.account_no = a.account_no AND l.trans_datetime = a.create_time) c "
                + "ON b.customer_account_id::text = c.account_no", p("e", Conv.toDateTime(end)));
            List<Row> data = new ArrayList<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (Row r : rows) {
                data.add(deal.dto().make("WxMobileWeb.Controllers.UserTotalReport", "Name", r.get("name"), "AccountNo", r.get("account_no"),
                    "Asn", r.get("asn"), "FinalBalacne", dec3(r.d("balance")), "CreateTime", Conv.toStrOrNull(r.get("create_time"))));
                sum = sum.add(Conv.toDecimal(r.d("balance")).setScale(0, RoundingMode.HALF_EVEN));
            }
            list.put("SumAmount", sum);
            int count = data.size();
            list.put("data", num > 0 ? new ArrayList<>(data.stream().skip((long) (num - 1) * 50).limit(50).toList()) : data);
            return okJson(list, count);
        } catch (Exception ex) {
            return ret(true, enc(com.ltstation.center.legacy.Wire.json(ex.getMessage())), 0);
        }
    }

    /** Ligne de relevé (SingleBillUserLog) avec l'écart de solde calculé comme l'original. */
    private List<Row> billLines(List<Row> logs, boolean handleType13) {
        List<Row> out = new ArrayList<>();
        int n = 0;
        double last = 0.0;
        double prev = 0.0;
        String cur = null;
        for (Row item : logs) {
            if (!item.s("ASN").equals(cur)) {
                last = 0.0;
                cur = item.s("ASN");
            }
            if (n == 0 || last == 0.0) {
                Row before = db.first("AccountServiceLogs", "{AccountNo} = :n AND " + TS + " < :t ORDER BY {TransDatetime} DESC",
                    p("n", item.str("AccountNo"), "t", item.dt("TransDatetime")));
                if (before != null) {
                    prev = r3(before.d("Balance"));
                }
            } else {
                prev = last;
            }
            double amount = item.d("Amount");
            double bal = item.d("Balance");
            double fee = item.d("FeeAmount");
            double disc = item.d("DiscountAmount");
            Row l = deal.dto().make("SingleBillUserLog");
            l.put("Name", deal.getCardUserName(item.s("ASN")));
            l.put("Account", item.get("AccountNo"));
            l.put("Asn", item.get("ASN"));
            l.put("OperateType", CenterDeal.getTransactionType(item.i("TransactionTypeID")));
            int t = item.i("TransactionTypeID");
            if (t == 13 && !handleType13) {
                t = -1;
            }
            switch (t) {
                case 1, 3, 4, 6, 7, 8 -> {
                    l.put("Error", "0");
                    l.put("Amount", Conv.num(r3(amount)));
                }
                case 2 -> {
                    l.put("Error", Conv.num(r3(bal - amount - fee - disc - prev)));
                    l.put("Amount", Conv.num(r3(amount)));
                    l.put("Discount", Conv.toDecimal(fee).add(item.dec("DiscountAmount")).setScale(3, RoundingMode.HALF_EVEN)
                        .stripTrailingZeros().toPlainString());
                    last = r3(bal);
                }
                case 12 -> {
                    l.put("Error", Conv.num(r3(bal - amount - fee - disc - prev)));
                    l.put("Amount", Conv.num(r3(amount + disc)));
                    l.put("Discount", "0");
                    last = r3(bal);
                }
                case 11 -> {
                    l.put("Error", Conv.num(r3(bal + Math.abs(amount) - prev)));
                    l.put("Amount", Conv.num(r3(amount)));
                    l.put("Volume", Conv.num(r3(fee)));
                    last = r3(bal);
                }
                case 16 -> {
                    l.put("Error", Conv.num(r3(bal - (Math.abs(amount) + prev))));
                    l.put("Amount", Conv.num(r3(amount)));
                    l.put("Volume", Conv.num(r3(fee)));
                    last = r3(bal);
                }
                case 13 -> {
                    l.put("Error", Conv.num(r3(bal - amount - prev)));
                    l.put("Amount", Conv.num(r3(amount)));
                    last = r3(bal);
                }
                default -> {
                    l.put("Error", Conv.num(r3(bal + amount - prev)));
                    l.put("Amount", Conv.num(r3(amount)));
                    last = r3(bal);
                }
            }
            l.put("StationName", deal.getStationName(item.i("StationID")));
            l.put("CreateTime", Conv.toStr(item.get("TransDatetime")));
            l.put("Balance", Conv.num(r3(bal)));
            out.add(l);
            n++;
        }
        return out;
    }

    private Row bill(List<String> asns, String start, String end, int draw, int num, boolean handleType13, boolean sortByTime) {
        Row out = deal.dto().make("SingleBillList");
        int total = 0;
        List<Row> lines = new ArrayList<>();
        if (!asns.isEmpty()) {
            String w = "{ASN} IN (:a) AND " + TS + " >= :s AND " + TS + " <= :e";
            Map<String, Object> ps = p("a", asns, "s", Conv.toDateTime(start), "e", Conv.toDateTime(end));
            List<Row> logs;
            if (num < 0) {
                logs = db.list("AccountServiceLogs", w + " ORDER BY {TransDatetime}, {ASN}", ps);
                total = logs.size();
            } else {
                Sugar.Page pg = db.page("AccountServiceLogs", w, ps, "{TransDatetime}, {ASN}", num, 50);
                logs = pg.rows();
                total = pg.total();
            }
            lines = billLines(logs, handleType13);
        }
        if (sortByTime) {
            lines.sort(Comparator.comparing(r -> r.s("CreateTime")));
        }
        out.put("draw", draw);
        out.put("recordsTotal", total);
        out.put("recordsFiltered", total);
        out.put("data", lines);
        out.put("__total", total);
        return out;
    }

    @RequestMapping("/GetSingleBillReport")
    public String GetSingleBillReport(@RequestBody(required = false) String body) {
        Row cp = req(body);
        String[] a = cp.s("Mess").split("\\|", -1);
        try {
            String start = a[0];
            String end = a[1];
            String kind = a[2];
            String text = a[3];
            String asn = a[4];
            String phone = a[5];
            String carNo = a[6];
            int draw = Integer.parseInt(a[7]);
            int num = Integer.parseInt(a[8]);
            List<String> list = new ArrayList<>();
            switch (kind) {
                case "1" -> {
                    for (Row c : db.query("SELECT customer_id FROM customers WHERE (name = :n AND :n <> '') OR (telephone = :ph AND :ph <> '') "
                        + "OR (vehicle_no = :car AND :car <> '')", p("n", text, "ph", phone, "car", carNo))) {
                        list.addAll(db.jdbc().queryForList("SELECT asn FROM customer_card_infos WHERE customer_id = :c",
                            p("c", c.i("customer_id")), String.class));
                    }
                }
                case "2" -> {
                    for (Row e : db.query("SELECT employee_id FROM employees WHERE (name = :n AND :n <> '') OR (telephone = :ph AND :ph <> '')",
                        p("n", text, "ph", phone))) {
                        list.addAll(db.jdbc().queryForList("SELECT asn FROM employee_card_infos WHERE employee_id = :e",
                            p("e", e.i("employee_id")), String.class));
                    }
                }
                case "3" -> {
                    Row g = new Row();
                    if (!text.isEmpty()) {
                        g = db.first("Groups", "{Name} = :n OR {ShortName} = :n", p("n", text));
                    } else if (!carNo.isEmpty()) {
                        Row gi = db.first("GroupCardInfos", "{ASN} = :a", p("a", carNo));
                        if (gi != null) {
                            g = db.first("Groups", "{GroupID} = :g", p("g", gi.i("GroupID")));
                        }
                    } else if (!phone.isEmpty()) {
                        g = db.first("Groups", "{Telephone} = :ph", p("ph", phone));
                    }
                    if (g == null) {
                        throw fail("Le groupe n'existe pas");
                    }
                    Row gi = db.first("GroupCardInfos", "{GroupID} = :g", p("g", g.i("GroupID")));
                    if (gi == null) {
                        throw fail("Object reference not set to an instance of an object.");
                    }
                    if (gi.s("ASN").startsWith("010014")) {
                        list.addAll(db.jdbc().queryForList("SELECT asn FROM customer_card_infos WHERE group_id = :g",
                            p("g", gi.i("GroupID")), String.class));
                        list.add(gi.s("ASN"));
                    }
                }
                default -> {
                }
            }
            if (!asn.isEmpty()) {
                list.clear();
                list.add(asn);
            }
            Row out = bill(list, start, end, draw, num, true, false);
            int total = Conv.toInt(out.remove("__total"));
            return okJson(out, total);
        } catch (Exception ex) {
            return ret(true, enc(com.ltstation.center.legacy.Wire.json(ex.getMessage())), 0);
        }
    }

    @RequestMapping("/GetGroupBillReport")
    public String GetGroupBillReport(@RequestBody(required = false) String body) {
        Row cp = req(body);
        String[] a = cp.s("Mess").split("\\|", -1);
        try {
            String start = a[0];
            String end = a[1];
            String account = a[2];
            String asn = a[3];
            String phone = a[4];
            String certNo = a[5];
            String contact = a[6];
            int draw = Integer.parseInt(a[7]);
            int num = Integer.parseInt(a[8]);
            List<String> list = new ArrayList<>();
            for (Row g : db.query("SELECT group_id FROM groups WHERE ((name = :n OR short_name = :n) AND :n <> '') "
                + "OR (telephone = :ph AND :ph <> '') OR (certificate_no = :c AND :c <> '') OR (contact = :ct AND :ct <> '')",
                p("n", account, "ph", phone, "c", certNo, "ct", contact))) {
                Row gi = db.first("GroupCardInfos", "{GroupID} = :g", p("g", g.i("group_id")));
                if (gi == null) {
                    throw fail("Object reference not set to an instance of an object.");
                }
                list.add(gi.s("ASN"));
                list.addAll(db.jdbc().queryForList("SELECT asn FROM customer_card_infos WHERE group_id = :g", p("g", g.i("group_id")),
                    String.class));
            }
            if (!asn.isEmpty()) {
                list.clear();
                list.add(asn);
            }
            Row out = bill(list, start, end, draw, num, false, true);
            int total = Conv.toInt(out.remove("__total"));
            return okJson(out, total);
        } catch (Exception ex) {
            return ret(true, enc(com.ltstation.center.legacy.Wire.json(ex.getMessage())), 0);
        }
    }

    @RequestMapping("/ScoreMessage")
    public String ScoreMessage(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            requireStation(modal);
            Row info = db.first("CustomerCardInfos", "{CustomerID} = :c AND {CardStatusTypeID} = 0", p("c", Conv.toInt(modal.str("Mess"))));
            if (info == null) {
                throw fail("L'utilisateur n'existe pas");
            }
            return okText(info.dec("Score").toPlainString());
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/ScoreDeal")
    public String ScoreDeal(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            requireStation(modal);
            String[] a = modal.s("Mess").split("\\|", -1);
            Row info = db.first("CustomerCardInfos", "{CustomerID} = :c AND {CardStatusTypeID} = 0", p("c", Conv.toInt(a[0])));
            if (info == null) {
                throw fail("L'utilisateur n'existe pas");
            }
            BigDecimal v = Conv.toDecimal(a[1]);
            if (info.dec("Score").compareTo(v) > 0) {
                info.put("Score", info.dec("Score").subtract(v));
                db.update("CustomerCardInfos", info);
                return okEmpty();
            }
            throw fail("Les points dépassent le plafond");
        } catch (Exception ex) {
            return err(ex);
        }
    }
}
