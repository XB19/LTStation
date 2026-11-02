package com.ltstation.center.serverdeal;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Listes, relevés, cartes NP et clés de /ExtendUpdate/* (CardMake). RECONSTRUCTION, voir ExtendCardController.
 * Règles déduites : 20 lignes par page ; une carte NP (np_value renseigné) devient physique quand Memo1 vaut « Real » ;
 * les clés sont les lignes Data 0 à 19, et 21 / 22 pour les codes (mêmes index que ServerDeal/UpdateSecretKey).
 */
@RestController
@RequestMapping("/ExtendUpdate")
public class ExtendReportController extends ServerDealBase {

    private static final int PAGE = 20;
    private static final String REAL = "Real";

    private final boolean secretSame;

    public ExtendReportController(@Qualifier("centerSugar") Sugar db, CenterDeal deal, @Value("${app.discount-per-company:true}") boolean secretSame) {
        super(db, deal);
        this.secretSame = secretSame;
    }

    private static int pages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }

    private static LocalDateTime date(String s) {
        return Conv.toDateTime(s);
    }

    // ------------------------------------------------------------ cartes ouvertes

    private Row cardList(Row m, int size) {
        Map<String, Object> q = p("s", m.i("StationID"), "a", date(m.str("StartTime")), "b", date(m.str("EndTime")));
        String where = "i.station_id = :s AND i.issue_datetime >= :a AND i.issue_datetime <= :b";
        Long total = db.scalar("SELECT count(*) FROM customer_card_infos i WHERE " + where, q, Long.class);
        q.put("l", size);
        q.put("o", (long) Math.max(m.i("PageIndex") - 1, 0) * size);
        List<Row> list = new ArrayList<>();
        for (Row r : db.query("SELECT i.asn, u.name, u.telephone, i.issue_datetime FROM customer_card_infos i LEFT JOIN customers u ON u.customer_id = i.customer_id "
            + "WHERE " + where + " ORDER BY i.issue_datetime DESC LIMIT :l OFFSET :o", q)) {
            LocalDateTime dt = r.dt("issue_datetime");
            list.add(Row.of("Asn", r.str("asn"), "CustomerName", r.str("name"), "Phone", r.str("telephone"), "IssueDate", dt == null ? "" : dt.format(Conv.STD)));
        }
        long t = total == null ? 0 : total;
        return Row.of("TotalPage", pages(t, size), "TotaNumber", t, "list", list);
    }

    @RequestMapping("/GetCardList")
    public String GetCardList(@RequestBody(required = false) String body) {
        try {
            return okJson(cardList(req(body), PAGE));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ recharges de la station

    private Row logList(Row m, int size) {
        Map<String, Object> q = p("s", m.i("StationID"), "t", m.i("TransactionTypeID"), "a", date(m.str("StartTime")), "b", date(m.str("EndTime")));
        String where = "l.station_id = :s AND l.transaction_type_id = :t AND l.trans_datetime >= :a AND l.trans_datetime <= :b";
        Row sum = db.queryFirst("SELECT count(*) AS c, coalesce(sum(l.amount), 0) AS s FROM account_service_logs l WHERE " + where, q);
        q.put("l", size);
        q.put("o", (long) Math.max(m.i("PageIndex") - 1, 0) * size);
        List<Row> list = new ArrayList<>();
        for (Row r : db.query("SELECT l.id, l.asn, l.trans_datetime, l.amount FROM account_service_logs l WHERE " + where + " ORDER BY l.id DESC LIMIT :l OFFSET :o", q)) {
            CenterDeal.CusCard c = deal.getCusCardInfo(r.str("asn"));
            LocalDateTime dt = r.dt("trans_datetime");
            list.add(Row.of("TransactionID", r.get("id"), "CardNumber", r.str("asn"), "CardUserName", c.ok() ? c.customers().str("Name") : "",
                "PhoneNumber", c.ok() ? c.customers().str("Telephone") : "", "TransactionDateTime", dt == null ? "" : dt.format(Conv.STD), "DepositAmount", r.get("amount")));
        }
        long t = Conv.toLong(sum.get("c"));
        return Row.of("TotalPage", pages(t, size), "TotaNumber", t, "TotalAmount", sum.dec("s"), "list", list);
    }

    @RequestMapping("/GetLogList")
    public String GetLogList(@RequestBody(required = false) String body) {
        try {
            return okJson(logList(req(body), PAGE));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ relevé d'un client

    private Row userLog(Row m) {
        String asn = m.str("CardNo");
        if (asn.isEmpty() && !m.str("UserName").isEmpty()) {
            Row u = db.first("Customers", "{Name} = :n", p("n", m.str("UserName")));
            Row i = u == null ? null : db.first("CustomerCardInfos", "{CustomerID} = :c", p("c", u.i("CustomerID")));
            asn = i == null ? "" : i.str("ASN");
        }
        CenterDeal.CusCard c = deal.getCusCardInfo(asn);
        if (!c.ok()) {
            throw fail(c.error());
        }
        int size = m.i("PageSize");
        Map<String, Object> q = p("x", asn, "a", date(m.str("DTStart")), "b", date(m.str("DTEnd")));
        String where = "asn = :x AND trans_datetime >= :a AND trans_datetime <= :b";
        Long total = db.scalar("SELECT count(*) FROM account_service_logs WHERE " + where, q, Long.class);
        String sql = "SELECT id, trans_datetime, transaction_type_id, station_id, amount, balance FROM account_service_logs WHERE " + where + " ORDER BY id";
        if (size > 0) {
            q.put("l", size);
            q.put("o", (long) Math.max(m.i("PageIndex") - 1, 0) * size);
            sql += " LIMIT :l OFFSET :o";
        }
        List<Row> list = new ArrayList<>();
        for (Row r : db.query(sql, q)) {
            double amount = r.d("amount");
            LocalDateTime dt = r.dt("trans_datetime");
            list.add(Row.of("CreateTime", dt == null ? "" : dt.format(Conv.STD), "TransactionID", r.get("id"), "Description", CenterDeal.getTransactionType(r.i("transaction_type_id")),
                "Station", deal.getStationName(r.i("station_id")), "Product", "", "QTY", "", "Debit", amount < 0 ? String.valueOf(-amount) : "",
                "Credit", amount >= 0 ? String.valueOf(amount) : "", "Balance", String.valueOf(r.d("balance"))));
        }
        long t = total == null ? 0 : total;
        return Row.of("Name", c.customers().str("Name"), "CardNo", asn, "IName", c.customers().str("Name"), "Balance", c.accounts().dec("Balance"), "TotalNumber", t,
            "TotalPage", size > 0 ? pages(t, size) : 1, "list", list);
    }

    @RequestMapping("/GetUserLogList")
    public String GetUserLogList(@RequestBody(required = false) String body) {
        try {
            return okJson(userLog(req(body)));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** ExportType : 1 recharges, 2 relevé complet, 3 cartes ; le logiciel fabrique lui-même le fichier. */
    @RequestMapping("/ExtendExportExcel")
    public String ExtendExportExcel(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            Row data = Wire.parseRow(m.str("requestData"));
            return okJson(switch (m.i("ExportType")) {
                case 1 -> logList(data, PAGE);
                case 2 -> userLog(data);
                case 3 -> cardList(data, PAGE);
                default -> throw fail("Export inconnu");
            });
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCardDepositList")
    public String GetCardDepositList(@RequestBody(required = false) String body) {
        try {
            return okJson(db.list("AccountServiceLogs", "{ASN} = :a AND {TransactionTypeID} = 2 ORDER BY {ID} DESC", p("a", req(body).str("ASN"))));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** Journal d'une carte, avec les totaux dans l'enveloppe de la réponse (TotalCount, TotalAmount, TotalPage). */
    @RequestMapping("/NPGetUserLog")
    public String NPGetUserLog(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            int size = m.i("PageSize") > 0 ? m.i("PageSize") : PAGE;
            Map<String, Object> q = p("a", m.str("CardNo"), "t", m.i("TransactionType"));
            String where = "{ASN} = :a AND {TransactionTypeID} = :t";
            Row sum = db.queryFirst(db.sql("AccountServiceLogs", "SELECT count(*) AS c, coalesce(sum({Amount}), 0) AS s FROM [AccountServiceLogs] WHERE " + where), q);
            Sugar.Page page = db.page("AccountServiceLogs", where, q, "{ID} DESC", Math.max(m.i("PageIndex"), 1), size);
            long t = Conv.toLong(sum.get("c"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Result", true);
            out.put("Mess", enc(Wire.json(page.rows())));
            out.put("TotalCount", t);
            out.put("TotalAmount", sum.dec("s"));
            out.put("TotalPage", pages(t, size));
            return Wire.json(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ cartes NP sans carte physique

    @RequestMapping("/GetNPCardtransform")
    public String GetNPCardtransform(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            Map<String, Object> q = p("n", "%" + m.str("QueryName") + "%", "r", REAL);
            String where = "i.np_value IS NOT NULL AND i.np_value <> '' AND coalesce(i.memo1, '') <> :r AND (u.name ILIKE :n OR u.telephone ILIKE :n OR i.asn ILIKE :n)";
            String from = " FROM customer_card_infos i JOIN customers u ON u.customer_id = i.customer_id JOIN customer_accounts a ON a.customer_account_id = i.customer_account_id ";
            Long total = db.scalar("SELECT count(*)" + from + "WHERE " + where, q, Long.class);
            q.put("l", PAGE);
            q.put("o", (long) Math.max(m.i("Index") - 1, 0) * PAGE);
            List<Row> list = new ArrayList<>();
            for (Row r : db.query("SELECT i.asn, i.customer_id, u.name, u.telephone, i.valid_datetime, a.balance" + from + "WHERE " + where + " ORDER BY i.asn LIMIT :l OFFSET :o", q)) {
                LocalDateTime dt = r.dt("valid_datetime");
                list.add(Row.of("Asn", r.str("asn"), "CustomerName", r.str("name"), "CustomerID", r.i("customer_id"), "Phone", r.str("telephone"),
                    "ValidTime", dt == null ? "" : dt.format(Conv.STD), "Balance", r.dec("balance")));
            }
            long t = total == null ? 0 : total;
            return okJson(Row.of("TotalPage", pages(t, PAGE), "TotalNumber", t, "list", list));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/UpdateNPCardTransform")
    public String UpdateNPCardTransform(@RequestBody(required = false) String body) {
        try {
            Row i = db.first("CustomerCardInfos", "{ASN} = :a", p("a", req(body).str("Asn")));
            if (i == null) {
                throw fail("Carte introuvable");
            }
            i.put("Memo1", REAL);
            i.put("UpdateDatetime", LocalDateTime.now());
            db.update("CustomerCardInfos", i);
            return okEmpty();
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ clés

    private int company(Row m) {
        return secretSame ? m.i("CompanyID") : 0;
    }

    @RequestMapping("/GetSecretKey")
    public String GetSecretKey(@RequestBody(required = false) String body) {
        try {
            List<String> seeds = new ArrayList<>();
            for (Row d : db.list("Data", "{CompanyID} = :c AND {ID} < 20 ORDER BY {ID}", p("c", company(req(body))))) {
                seeds.add(d.str("D_Data"));
            }
            return okJson(Row.of("OPA", "", "OPB", "", "NPA", "", "NPB", "", "m_sSeedKey", seeds));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** Les anciens codes chiffrés (lignes 21 et 22) doivent correspondre avant de remplacer les clés. */
    @RequestMapping("/KeyUpdate")
    public String KeyUpdate(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row m = req(body);
                int c = company(m);
                Row a = db.first("Data", "{ID} = 21 AND {CompanyID} = :c", p("c", c));
                Row b = db.first("Data", "{ID} = 22 AND {CompanyID} = :c", p("c", c));
                if (a != null && b != null && !(a.str("D_Data").equals(m.str("OPA")) && b.str("D_Data").equals(m.str("OPB")))) {
                    throw fail("L'ancien code est incorrect");
                }
                List<?> list = m.get("m_sSeedKey") instanceof List<?> l ? l : List.of();
                for (int i = 0; i < list.size(); i++) {
                    int index = i > 19 ? i + 1 : i;
                    Row d = db.first("Data", "{ID} = :i AND {CompanyID} = :c", p("i", index, "c", c));
                    boolean add = d == null;
                    d = add ? new Row() : d;
                    d.put("ID", index);
                    d.put("D_Type", String.valueOf(index));
                    d.put("D_Ver", "01");
                    d.put("D_Data", Conv.toStr(list.get(i)));
                    d.put("CompanyID", c);
                    if (add) {
                        db.insert("Data", d);
                    } else {
                        db.update("Data", d);
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
