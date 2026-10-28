package com.ltstation.center.sw.normal;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Clôtures de la caisse V2 (BoxScreenV2.CloseShiftCheck / CloseDayCheck / CloseMonthCheck) : tableaux JSON
 * BoxNozShift, BoxTestNozShift, BoxProductSaleShift, BoxPayMentShift, BoxDiscountShift… enregistrés dans
 * BoxShiftList, BoxDayList et BoxMonthList (lus ensuite par BoxBase.BoxShiftPrint / BoxDayPrint).
 */
public final class BoxShiftClose {

    private BoxShiftClose() {
    }

    private static Sugar db() {
        return SW.db();
    }

    private static Map<String, Object> p(Object... kv) {
        return Sugar.p(kv);
    }

    /** BaseFunction.GetTransactionVolumeTotal(NozID, Start, End, out …) : null si la requête échoue. */
    public static Row GetTransactionVolumeTotal(int NozID, long Start, long End) {
        try {
            List<Row> list = db().list("NormalTransaction", "{NozID} = :n AND {CreateTime} >= :s AND {CreateTime} <= :e ORDER BY {CreateTime}",
                p("n", NozID, "s", Start, "e", End));
            Row r = Row.of("StartVolume", BigDecimal.ZERO, "EndVolume", BigDecimal.ZERO, "Volume", BigDecimal.ZERO, "Amount", BigDecimal.ZERO, "oilCount", 0);
            if (!list.isEmpty()) {
                Row first = list.get(0);
                r.put("StartVolume", first.dec("VolumeTotal").subtract(first.dec("Volume")));
                r.put("EndVolume", list.get(list.size() - 1).dec("VolumeTotal"));
                r.put("Volume", list.stream().map(t -> t.dec("Volume")).reduce(BigDecimal.ZERO, BigDecimal::add));
                r.put("Amount", list.stream().map(t -> t.dec("Amount")).reduce(BigDecimal.ZERO, BigDecimal::add));
                r.put("oilCount", list.size());
            } else {
                Row last = db().first("NormalTransaction", "{NozID} = :n AND {CreateTime} < :s ORDER BY {CreateTime} DESC", p("n", NozID, "s", Start));
                if (last != null) {
                    r.put("StartVolume", last.dec("VolumeTotal"));
                    r.put("EndVolume", last.dec("VolumeTotal"));
                }
            }
            return r;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static List<Row> payMentList() {
        List<Row> list = new ArrayList<>();
        for (String m : List.of("Cash", "QR code transfer", "Bank transfer", "Credit card", "Fleet card", "Coupon", "Personal credit")) {
            list.add(Row.of("PayMentMethod", m, "Quantity", BigDecimal.ZERO, "Amount", BigDecimal.ZERO));
        }
        return list;
    }

    private static List<Row> discountList() {
        List<Row> list = new ArrayList<>();
        list.add(Row.of("DiscountType", "Discount", "Quantity", BigDecimal.ZERO, "Amount", BigDecimal.ZERO));
        return list;
    }

    private static List<Row> productSale(String where, Map<String, Object> params) {
        List<Row> list = new ArrayList<>();
        for (Row g : db().query(db().sql("ProductLog", "select {ProductRealID} as productrealid, sum({Amount}) as amount, sum({Quantity}) as quantity "
            + "from [ProductLog] where " + where + " group by {ProductRealID}"), params)) {
            Row product = db().first("ProductList", "{ID} = :i", p("i", g.get("productrealid")));
            list.add(Row.of("Product", product == null ? "" : product.str("Name"), "Quantity", g.dec("quantity"), "Amount", g.dec("amount")));
        }
        return list;
    }

    /** Clôture du poste en cours (IniInfo.BoxShift) et ouverture du suivant ; null si tout s'est bien passé. */
    public static String CloseShiftCheck() {
        try {
            Row boxShiftList = db().first("BoxShiftList", "{BoxShiftID} = :b", p("b", IniInfo.BoxShift));
            if (boxShiftList == null) {
                throw new IllegalStateException("Shift Lost");
            }
            long num = BaseFunction.ConvertDateTimeToInt();
            List<Row> list2 = new ArrayList<>();
            for (Row noz : db().list("NormalNoz", "ORDER BY {NozID}", null)) {
                Row v = GetTransactionVolumeTotal(noz.i("NozID"), Conv.toLong(boxShiftList.get("StartTime")), num);
                if (v != null) {
                    list2.add(Row.of("NozID", noz.i("NozID"), "Product", BaseFunction.GetNormalGradeByID(noz.i("GradeID")), "Start", v.get("StartVolume"),
                        "End", v.get("EndVolume"), "Volume", v.get("Volume"), "Amount", v.get("Amount"), "oilCount", v.get("oilCount")));
                }
            }
            List<Row> list3 = new ArrayList<>();
            for (Row g : db().query(db().sql("BillOrder", "select {NozID} as nozid, sum({Volume}) as volume, sum({Amount}) as amount from [BillOrder] "
                + "where {BoxShift} = :b and {BillPayMent} = 7 group by {NozID}"), p("b", IniInfo.BoxShift))) {
                Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", g.get("nozid")));
                if (noz != null) {
                    list3.add(Row.of("NozID", Conv.toInt(g.get("nozid")), "Product", BaseFunction.GetNormalGradeByID(noz.i("GradeID")),
                        "Volume", g.dec("volume"), "Amount", g.dec("amount")));
                }
            }
            List<Row> list4 = productSale("{ShiftNumber} = :b", p("b", IniInfo.BoxShift));
            List<Row> list5 = payMentList();
            for (Row g : db().query(db().sql("BillOrder", "select {BillPayMent} as paymentmethod, count({BillPayMent}) as paymentcount, "
                + "sum({Amount}) as paymentamount from [BillOrder] where {BoxShift} = :b group by {BillPayMent}"), p("b", IniInfo.BoxShift))) {
                // Index des montants comme dans l'original ; le nombre est toujours écrit sur la ligne « Cash »
                int idx = switch (Conv.toInt(g.get("paymentmethod"))) {
                    case 0 -> 0;
                    case 1 -> 3;
                    case 2 -> 4;
                    case 3 -> 1;
                    case 4 -> 2;
                    case 5 -> 5;
                    case 6 -> 6;
                    default -> -1;
                };
                if (idx >= 0) {
                    list5.get(idx).put("Amount", g.dec("paymentamount"));
                    list5.get(0).put("Quantity", g.dec("paymentcount"));
                }
            }
            List<Row> list6 = discountList();
            for (Row item : db().list("BillOrderSummary", "{BoxShift} = :b", p("b", IniInfo.BoxShift))) {
                if (!item.str("Discount").isEmpty()) {
                    List<?> values = (List<?>) Wire.parse(item.str("Discount"));
                    list6.get(0).put("Quantity", list6.get(0).dec("Quantity").add(BigDecimal.valueOf(values.size())));
                    list6.get(0).put("Amount", list6.get(0).dec("Amount").add(values.stream().map(Conv::toDecimal).reduce(BigDecimal.ZERO, BigDecimal::add)));
                }
            }
            boxShiftList.put("EndTime", num);
            boxShiftList.put("NozTable", Wire.json(list2));
            boxShiftList.put("TestTable", Wire.json(list3));
            boxShiftList.put("ProductSaleTable", Wire.json(list4));
            boxShiftList.put("ProductRefundTable", "[]");
            boxShiftList.put("PayMentSumTable", Wire.json(list5));
            boxShiftList.put("DiscountSumTable", Wire.json(list6));
            boxShiftList.put("MoneySubmitTable", "[]");
            boxShiftList.put("AbnormalTable", "[]");
            boxShiftList.put("Complete", 1);
            db().update("BoxShiftList", boxShiftList);
            String shift = String.valueOf(IniInfo.BoxShift);
            String text = shift.substring(0, shift.length() - 2);
            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            if (text.equals(today)) {
                IniInfo.BoxShift = Integer.parseInt(text + BaseFunction.SetStrLen(String.valueOf(Integer.parseInt(shift.substring(shift.length() - 2)) + 1), 2));
            } else {
                IniInfo.BoxShift = Integer.parseInt(today + "01");
            }
            db().insert("BoxShiftList", Row.of("StartTime", num, "BoxShiftID", IniInfo.BoxShift, "Complete", 0, "CreateTime", BaseFunction.ConvertDateTimeToInt(),
                "BoxDay", LocalDate.now().toString(), "DayComplete", 0));
            return null;
        } catch (RuntimeException ex) {
            return ex.getMessage();
        }
    }

    /** Cumul des tableaux des postes (ou des journées) dans les listes du jour (ou du mois). */
    private static void merge(Row src, List<Row> noz, List<Row> test, List<Row> pay, List<Row> discount) {
        if (!src.str("NozTable").isEmpty()) {
            for (Row item : Wire.parseRows(src.str("NozTable"))) {
                Row cur = noz.stream().filter(t -> t.i("NozID") == item.i("NozID")).findFirst().orElse(null);
                if (item.i("oilCount") > 0) {
                    if (cur == null) {
                        noz.add(Row.of("NozID", item.i("NozID"), "Product", item.str("Product"), "Start", item.dec("Start"), "End", item.dec("End"),
                            "Volume", item.dec("Volume"), "Amount", item.dec("Amount"), "oilCount", item.i("oilCount")));
                    } else {
                        cur.put("Amount", cur.dec("Amount").add(item.dec("Amount")));
                        cur.put("Volume", cur.dec("Volume").add(item.dec("Volume")));
                        cur.put("oilCount", cur.i("oilCount") + item.i("oilCount"));
                        cur.put("End", item.dec("End"));
                    }
                }
            }
        }
        if (!src.str("TestTable").isEmpty()) {
            for (Row item : Wire.parseRows(src.str("TestTable"))) {
                Row cur = test.stream().filter(t -> t.i("NozID") == item.i("NozID")).findFirst().orElse(null);
                if (cur == null) {
                    test.add(Row.of("NozID", item.i("NozID"), "Product", item.str("Product"), "Volume", item.dec("Volume"), "Amount", item.dec("Amount")));
                } else {
                    cur.put("Amount", cur.dec("Amount").add(item.dec("Amount")));
                    cur.put("Volume", cur.dec("Volume").add(item.dec("Volume")));
                }
            }
        }
        if (!src.str("PayMentSumTable").isEmpty()) {
            for (Row item : Wire.parseRows(src.str("PayMentSumTable"))) {
                Row cur = pay.stream().filter(t -> t.str("PayMentMethod").equals(item.str("PayMentMethod"))).findFirst().orElse(null);
                if (cur == null) {
                    pay.add(Row.of("PayMentMethod", item.str("PayMentMethod"), "Quantity", item.dec("Quantity"), "Amount", item.dec("Amount")));
                } else {
                    cur.put("Amount", cur.dec("Amount").add(item.dec("Amount")));
                    cur.put("Quantity", cur.dec("Quantity").add(item.dec("Quantity")));
                }
            }
        }
        if (!src.str("DiscountSumTable").isEmpty()) {
            for (Row item : Wire.parseRows(src.str("DiscountSumTable"))) {
                discount.get(0).put("Amount", discount.get(0).dec("Amount").add(item.dec("Amount")));
                discount.get(0).put("Quantity", discount.get(0).dec("Quantity").add(item.dec("Quantity")));
            }
        }
    }

    /** Journée des postes clôturés donnés (BoxDayList) ; null si l'enregistrement est fait. */
    public static String CloseDayCheck(List<Row> list) {
        try {
            List<Integer> shifts = list.stream().map(t -> t.i("BoxShiftID")).toList();
            List<Row> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.comparingInt(t -> t.i("BoxShiftID")));
            List<Row> noz = new ArrayList<>();
            List<Row> test = new ArrayList<>();
            List<Row> products = shifts.isEmpty() ? new ArrayList<>() : productSale("{ShiftNumber} IN (:s)", p("s", shifts));
            List<Row> pay = payMentList();
            List<Row> discount = discountList();
            long startTime = 0;
            long endTime = 0;
            for (int i = 0; i < sorted.size(); i++) {
                if (i == 0) {
                    startTime = Conv.toLong(sorted.get(i).get("StartTime"));
                }
                if (i == sorted.size() - 1) {
                    endTime = Conv.toLong(sorted.get(i).get("EndTime"));
                }
                merge(sorted.get(i), noz, test, pay, discount);
            }
            String day = sorted.get(0).str("BoxDay");
            db().insert("BoxDayList", Row.of("StartTime", startTime, "EndTime", endTime, "DiscountSumTable", Wire.json(discount), "MoneySubmitTable", "[]",
                "PayMentSumTable", Wire.json(pay), "ProductSaleTable", Wire.json(products), "AbnormalTable", "[]", "NozTable", Wire.json(noz),
                "ProductRefundTable", "[]", "TestTable", Wire.json(test), "CreateTime", BaseFunction.ConvertDateTimeToInt(), "BoxDay", day,
                "MonthComplete", 0, "BoxMonth", day.substring(0, 7)));
            return null;
        } catch (RuntimeException ex) {
            return ex.getMessage();
        }
    }

    /** Mois des journées données (BoxMonthList) ; les ventes de produits ne sont pas reprises, comme dans l'original. */
    public static String CloseMonthCheck(List<Row> list) {
        try {
            List<Row> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.comparingInt(t -> t.i("ID")));
            List<Row> noz = new ArrayList<>();
            List<Row> test = new ArrayList<>();
            List<Row> pay = payMentList();
            List<Row> discount = discountList();
            long startTime = 0;
            long endTime = 0;
            for (int i = 0; i < sorted.size(); i++) {
                if (i == 0) {
                    startTime = Conv.toLong(sorted.get(i).get("StartTime"));
                }
                if (i == sorted.size() - 1) {
                    endTime = Conv.toLong(sorted.get(i).get("EndTime"));
                }
                merge(sorted.get(i), noz, test, pay, discount);
            }
            db().insert("BoxMonthList", Row.of("StartTime", startTime, "EndTime", endTime, "DiscountSumTable", Wire.json(discount), "MoneySubmitTable", "[]",
                "PayMentSumTable", Wire.json(pay), "ProductSaleTable", "[]", "AbnormalTable", "[]", "NozTable", Wire.json(noz), "ProductRefundTable", "[]",
                "TestTable", Wire.json(test), "CreateTime", BaseFunction.ConvertDateTimeToInt(), "BoxMonth", sorted.get(0).str("BoxMonth")));
            return null;
        } catch (RuntimeException ex) {
            return ex.getMessage();
        }
    }
}
