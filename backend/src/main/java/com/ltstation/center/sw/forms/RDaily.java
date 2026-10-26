package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * RDaily : rapport journalier (ID 0) ou mensuel (ID 1) calculé sur les transactions, ou relu une fois clôturé ;
 * clôture (DailyReport) et export Excel. L'original exécute la clôture dans un thread derrière ProBar.
 */
@SwWindow("RDaily")
public class RDaily extends SwForm {

    private int DM;
    public LocalDateTime DTEnd;

    private static final String NOZ_SQL = "select {OilTransactions.GunId} AS GunId, {OilTransactions.GradeSpecificID} AS GradeSpecificID,"
        + " min({OilTransactions.TrueTime}) AS SMinTime, max({OilTransactions.TrueTime}) AS SmaxTime, sum({OilTransactions.TruelyPay}) AS SumAmount,"
        + " sum({OilTransactions.Volume}) AS SumVolum from [OilTransactions]"
        + " where CAST({OilTransactions.TrueTime} AS timestamp) >= :s and CAST({OilTransactions.TrueTime} AS timestamp) <= :e"
        + " group by {OilTransactions.GunId}, {OilTransactions.GradeSpecificID}";

    @Override
    protected void init() {
        DM = argInt("ID");
        if (IniInfo.NormalType == 2) {
            g("NozView").HeaderText("dataGridViewTextBoxColumn12", L("StartKG"));
            g("NozView").HeaderText("dataGridViewTextBoxColumn13", L("EndKG"));
        }
    }

    /** Math.Round(decimal, n) : l'échelle n'est jamais augmentée. */
    private static BigDecimal rd(Object v, int n) {
        BigDecimal d = Conv.toDecimal(v);
        return d.scale() > n ? d.setScale(n, RoundingMode.HALF_EVEN) : d;
    }

    private static String rs(Object v, int n) {
        return rd(v, n).toPlainString();
    }

    private LocalDateTime start(LocalDateTime end) {
        return DM == 0 ? end.minusDays(1) : end.minusMonths(1);
    }

    public void SearchButton_Click() {
        try {
            LocalDateTime d = c("DTDate").Value();
            LocalDateTime t = c("DTTime").Value();
            LocalDateTime dtE = LocalDateTime.of(d.getYear(), d.getMonthValue(), d.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond());
            LocalDateTime dtS = start(dtE);
            DTEnd = dtE;
            switch (sender) {
                case "DailyReport" -> DailyStart();
                case "DailyPrint" -> {
                }
                default -> {
                    String f = dtS.format(DateTimeFormatter.ofPattern(DM == 0 ? "yyyyMMdd" : "yyyyMM"));
                    boolean closed = DM == 0 ? db().first("DailyGunReports", "{Shift} = :f", p("f", f)) != null
                        : db().first("MonthlyGunReports", "{Month} = :f", p("f", f)) != null;
                    c("EndLabel").Visible(closed);
                    if (closed) {
                        ReadNozLoad(f);
                        ReadTypeLoad(f);
                        ReadCardLoad(f);
                        ReadTotalLoad(f);
                    } else {
                        NozShow(dtS, dtE);
                        TankShow(dtE);
                        CardShow(CardData(dtE));
                    }
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** NozRead / NozReadM puis ReadNozLoad. */
    private void ReadNozLoad(String f) {
        try {
            List<Row> list = DM == 0 ? db().list("DailyGunReports", "{Shift} = :f", p("f", f)) : db().list("MonthlyGunReports", "{Month} = :f", p("f", f));
            g("NozView").RowsClear();
            for (Row x : list) {
                g("NozView").RowsAdd(x.get("GunCode"), BaseFunction.GetOilNameByID(x.i("GradeSpecificID")), rd(x.get("StartPT"), IniInfo.Vradio),
                    rd(x.get("EndPT"), IniInfo.Vradio), rd(x.get(DM == 0 ? "Volume" : "TotalSales"), IniInfo.Vradio), rd(x.get("Amount"), IniInfo.Mradio),
                    x.get("StartTime"), x.get("EndTime"), rd(x.get(DM == 0 ? "PTC" : "Gap"), IniInfo.Vradio));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** TypeRead / TypeReadM puis ReadTypeLoad (le mensuel n'a pas de bons de carburant). */
    private void ReadTypeLoad(String f) {
        try {
            List<Row> list = DM == 0 ? db().list("DailySalesReports", "{Shift} = :f", p("f", f)) : db().list("MonthSalesReports", "{Month} = :f", p("f", f));
            g("TankView").RowsClear();
            for (Row x : list) {
                Object cm = DM == 0 ? rd(x.get("CreditCouponsM"), IniInfo.Mradio) : BigDecimal.ZERO;
                Object cv = DM == 0 ? rd(x.get("CreditCouponsV"), IniInfo.Vradio) : BigDecimal.ZERO;
                g("TankView").RowsAdd(BaseFunction.GetOilNameByID(x.i("GradeSpecificID")), rd(x.get("PPU"), IniInfo.Pradio), rd(x.get("Density"), IniInfo.Pradio),
                    rd(x.get("RCM"), IniInfo.Mradio), rd(x.get("RCV"), IniInfo.Vradio), rd(x.get("CashM"), IniInfo.Mradio), rd(x.get("CashV"), IniInfo.Vradio),
                    rd(x.get("DepositM"), IniInfo.Mradio), rd(x.get("DepositV"), IniInfo.Vradio), rd(x.get("CreditM"), IniInfo.Mradio),
                    rd(x.get("CreditV"), IniInfo.Vradio), cm, cv, rd(x.get("MaintM"), IniInfo.Mradio), rd(x.get("MaintV"), IniInfo.Vradio));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** CardRead / CardReadM puis ReadCardLoad. */
    private void ReadCardLoad(String f) {
        try {
            List<Row> list = db().list(DM == 0 ? "DailyCardShift" : "MonthCardShift", "{ShiftNumber} = :f", p("f", f));
            g("CardView").RowsClear();
            for (Row x : list) {
                g("CardView").RowsAdd(x.get("CardType"), Conv.toInt(x.get("TotalCount")), rd(x.get("TotalAmount"), IniInfo.Mradio),
                    rd(x.get("TotalVolume"), IniInfo.Vradio), x.get("StartTime"), x.get("EndTime"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** ReadTotalLoad / ReadTotalLoadM (TotalUserVolume deux fois, comme l'original). */
    private void ReadTotalLoad(String f) {
        try {
            List<Row> list = db().list(DM == 0 ? "DailyTotalShift" : "MonthTotalShift", "{ShiftNumber} = :f", p("f", f));
            g("TotalSumView").RowsClear();
            for (Row x : list) {
                g("TotalSumView").RowsAdd(x.get("TotalCount"), x.get("TotalVolume"), x.get("TotalAmount"), x.get("TotalUserVolume"), x.get("TotalUserVolume"),
                    x.get("TotalEmpVolume"), x.get("TotalEmpAmount"), x.get("TotalCreditVolume"), x.get("TotalCreditAmount"), x.get("TotalCashCouponsV"),
                    x.get("TotalCashCouponsA"), x.get("TotalNotCardVolume"), x.get("TotalNotCardAmount"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private Row transaction(Object gun, Object trueTime) {
        return db().first("OilTransactions", "{GunId} = :g AND {TrueTime} = :t", p("g", gun, "t", trueTime));
    }

    private void NozShow(LocalDateTime dtS, LocalDateTime dtE) {
        try {
            List<Row> list = db().query(db().sql(NOZ_SQL), p("s", dtS, "e", dtE));
            g("NozView").RowsClear();
            for (Row item : list) {
                Row first = transaction(item.get("GunId"), item.get("SMinTime"));
                double num = first == null ? 0.0 : Conv.round(Conv.toDouble(first.get("VolumeTotal")) - Conv.toDouble(first.get("Volume")), IniInfo.Mradio);
                Row last = transaction(item.get("GunId"), item.get("SmaxTime"));
                double num2 = last == null ? 0.0 : Conv.round(Conv.toDouble(last.get("VolumeTotal")), IniInfo.Vradio);
                BigDecimal num3 = rd(Conv.toDecimal(num2).subtract(Conv.toDecimal(num)).subtract(Conv.toDecimal(item.get("SumVolum"))), IniInfo.Vradio);
                g("NozView").RowsAdd(item.get("GunId"), BaseFunction.GetOilNameByID(item.i("GradeSpecificID")), num, num2,
                    Conv.round(Conv.toDouble(item.get("SumVolum")), IniInfo.Vradio), Conv.round(Conv.toDouble(item.get("SumAmount")), IniInfo.Mradio),
                    item.str("SMinTime").replace("T", " "), item.str("SmaxTime").replace("T", " "), num3);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static DataTable table(List<Row> rows, String... cols) {
        DataTable dt = new DataTable();
        for (String c : cols) {
            dt.addColumn(c);
        }
        for (Row r : rows) {
            Object[] v = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                v[i] = r.get(cols[i]);
            }
            dt.addRow(v);
        }
        return dt;
    }

    private static final String[] TANK_COLS = {"GradeSpecificID", "PPU", "MinTime", "MaxTime", "NotCardA", "NotCardV", "EmpCardA", "EmpCardV", "PerCardA",
        "PerCardV", "CreCardA", "CreCardV", "CashCouponsM", "CashCouponsV"};

    private String between(LocalDateTime DT) {
        return "{OilTransactions.TrueTime} between '" + start(DT).format(Conv.STD) + "' and '" + DT.format(Conv.STD) + "'";
    }

    private DataTable TankData(LocalDateTime DT) {
        try {
            StringBuilder sum = new StringBuilder();
            String[][] kinds = {{"000000", "NotCard"}, {"010004", "EmpCard"}, {"010001", "PerCard"}, {"010011", "CreCard"}};
            for (String[] k : kinds) {
                sum.append(", SUM(CASE a.Asn WHEN '").append(k[0]).append("' THEN a.Amount ELSE 0 END) AS \"").append(k[1]).append("A\"")
                    .append(", SUM(CASE a.Asn WHEN '").append(k[0]).append("' THEN a.Volume ELSE 0 END) AS \"").append(k[1]).append("V\"");
            }
            sum.append(", SUM(CASE a.Asn WHEN '010006' THEN a.Amount ELSE 0 END) AS \"CashCouponsM\"")
                .append(", SUM(CASE a.Asn WHEN '010006' THEN a.Volume ELSE 0 END) AS \"CashCouponsV\"");
            String sql = "select a.GradeSpecificID AS \"GradeSpecificID\", a.PPU AS \"PPU\", MIN(a.MinTime) as \"MinTime\", Max(a.MaxTime) as \"MaxTime\"" + sum
                + " from (select SUBSTRING({OilTransactions.Asn}, 1, 6) as Asn, {OilTransactions.GradeSpecificID} AS GradeSpecificID, {OilTransactions.PPU} AS PPU,"
                + " SUM({OilTransactions.TruelyPay}) as Amount, SUM({OilTransactions.Volume}) as Volume, Min({OilTransactions.TrueTime}) as MinTime,"
                + " MAX({OilTransactions.TrueTime}) as MaxTime from [OilTransactions] where " + between(DT)
                + " group by {OilTransactions.GradeSpecificID}, {OilTransactions.PPU}, SUBSTRING({OilTransactions.Asn}, 1, 6)) a group by a.GradeSpecificID, a.PPU";
            return table(db().query(db().sql(sql), null), TANK_COLS);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return null;
        }
    }

    /** Densité du carburant (PriceInfos.Newdensity) ; "" si inconnue. */
    private String density(int grade) {
        Row gra = db().first("GradeSpecifics", "{GradeSpecificID} = :g", p("g", grade));
        if (gra != null) {
            Row price = db().first("PriceInfos", "{Code} = :c", p("c", gra.get("Code")));
            if (price != null) {
                return Conv.toStr(price.get("Newdensity"));
            }
        }
        return "";
    }

    private static BigDecimal d(DataTable t, int i, String col) {
        return Conv.toDecimal(t.get(i, col));
    }

    private void TankShow(LocalDateTime DT) {
        try {
            DataTable t = TankData(DT);
            g("TankView").RowsClear();
            for (int i = 0; i < t.Rows.size(); i++) {
                int grade = Conv.toInt(t.get(i, "GradeSpecificID"));
                BigDecimal num = rd(d(t, i, "NotCardA").add(d(t, i, "EmpCardA")).add(d(t, i, "PerCardA")).add(d(t, i, "CreCardA")).add(d(t, i, "CashCouponsM")),
                    IniInfo.Mradio);
                BigDecimal num2 = rd(d(t, i, "NotCardV").add(d(t, i, "EmpCardV")).add(d(t, i, "PerCardV")).add(d(t, i, "CreCardV")).add(d(t, i, "CashCouponsV")),
                    IniInfo.Vradio);
                g("TankView").RowsAdd(BaseFunction.GetOilNameByID(grade), Conv.toStr(t.get(i, "PPU")), density(grade), num, num2,
                    rs(t.get(i, "EmpCardA"), IniInfo.Mradio), rs(t.get(i, "EmpCardV"), IniInfo.Vradio), rs(t.get(i, "PerCardA"), IniInfo.Mradio),
                    rs(t.get(i, "PerCardV"), IniInfo.Vradio), rs(t.get(i, "CreCardA"), IniInfo.Mradio), rs(t.get(i, "CreCardV"), IniInfo.Vradio),
                    rs(t.get(i, "CashCouponsM"), IniInfo.Mradio), rs(t.get(i, "CashCouponsV"), IniInfo.Vradio), rs(t.get(i, "NotCardA"), IniInfo.Mradio),
                    rs(t.get(i, "NotCardV"), IniInfo.Vradio));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void CardShow(DataTable t) {
        try {
            BigDecimal[] s = new BigDecimal[13];
            java.util.Arrays.fill(s, BigDecimal.ZERO);
            int num = 0;
            g("CardView").RowsClear();
            for (int i = 0; i < t.Rows.size(); i++) {
                String text = Conv.toStr(t.get(i, "Asn"));
                BigDecimal vol = d(t, i, "Vol");
                BigDecimal amt = d(t, i, "Amount");
                int k = switch (text) {
                    case "010001" -> 1;
                    case "110003", "000000" -> 7;
                    case "010004" -> 3;
                    case "010011" -> 5;
                    case "010006" -> 9;
                    default -> 0;
                };
                text = switch (k) {
                    case 1 -> L("UserCard");
                    case 7 -> L("NonCard");
                    case 3 -> L("EmployeeCard");
                    case 5 -> L("CreditCard");
                    case 9 -> L("RepairCard");
                    default -> text;
                };
                if (k > 0) {
                    s[k] = s[k].add(vol);
                    s[k + 1] = s[k + 1].add(amt);
                }
                num += Conv.toInt(t.get(i, "CardCount"));
                s[12] = s[12].add(amt);
                s[11] = s[11].add(vol);
                g("CardView").RowsAdd(text, Conv.toStr(t.get(i, "CardCount")), rs(amt, IniInfo.Mradio), rs(vol, IniInfo.Vradio), Conv.toStr(t.get(i, "MinTime")),
                    Conv.toStr(t.get(i, "MaxTime")));
            }
            int v = IniInfo.Vradio;
            int m = IniInfo.Mradio;
            g("TotalSumView").RowsClear();
            g("TotalSumView").RowsAdd(num, rd(s[11], v), rd(s[12], m), rd(s[1], v), rd(s[2], m), rd(s[3], v), rd(s[4], m), rd(s[5], v), rd(s[6], m), rd(s[9], v),
                rd(s[10], m), rd(s[7], v), rd(s[8], m));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private DataTable CardData(LocalDateTime Dt) {
        try {
            String sql = "select SUBSTRING({OilTransactions.Asn}, 1, 6) as \"Asn\", Count(*) as \"CardCount\", SUM({OilTransactions.TruelyPay}) as \"Amount\","
                + " SUM({OilTransactions.Volume}) as \"Vol\", MIN({OilTransactions.TrueTime}) as \"MinTime\", Max({OilTransactions.TrueTime}) as \"MaxTime\""
                + " from [OilTransactions] where " + between(Dt) + " Group by SUBSTRING({OilTransactions.Asn}, 1, 6)";
            return table(db().query(db().sql(sql), null), "Asn", "CardCount", "Amount", "Vol", "MinTime", "MaxTime");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return null;
        }
    }

    private void DailyStart() {
        try {
            if (c("EndLabel").Visible()) {
                throw new IllegalStateException(L("SettlementComplete"));
            }
            DailyWork();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static String now() {
        return LocalDateTime.now().format(Conv.STD);
    }

    private void DailyWork() {
        var tx = db().begin();
        try {
            LocalDateTime DTStart = start(DTEnd);
            String DailyF = DTStart.format(DateTimeFormatter.ofPattern(DM == 0 ? "yyyyMMdd" : "yyyyMM"));
            List<Row> list = db().query(db().sql(NOZ_SQL), p("s", DTStart, "e", DTEnd));
            String[][] array = new String[list.size()][6];
            int i = 0;
            for (Row item : list) {
                Row first = transaction(item.get("GunId"), item.get("SMinTime"));
                if (first == null) {
                    throw new NullPointerException();
                }
                double num = Conv.round(Conv.toDouble(first.get("VolumeTotal")) - Conv.toDouble(first.get("Volume")), IniInfo.Vradio);
                Row last = transaction(item.get("GunId"), item.get("SmaxTime"));
                if (last == null) {
                    throw new NullPointerException();
                }
                double num2 = Conv.round(Conv.toDouble(last.get("VolumeTotal")), IniInfo.Vradio);
                BigDecimal value = rd(Conv.toDecimal(num2).subtract(Conv.toDecimal(num)).subtract(Conv.toDecimal(item.get("SumVolum"))), IniInfo.Vradio);
                Row common = Row.of("StationID", IniInfo.StationID, "GunCode", item.get("GunId"), "StartPT", num, "EndPT", num2, "Amount",
                    Conv.toDouble(item.get("SumAmount")), "GradeSpecificID", item.get("GradeSpecificID"), "StartTime", item.get("SMinTime"), "EndTime",
                    item.get("SmaxTime"), "Upload", 0, "AccountTime", now(), "EmployeeID", IniInfo.LoginID);
                if (DM == 0) {
                    common.put("Volume", item.get("SumVolum"));
                    common.put("Shift", DailyF);
                    common.put("PTC", value.doubleValue());
                    db().insert("DailyGunReports", common);
                } else {
                    common.put("TotalSales", item.get("SumVolum"));
                    common.put("Month", DailyF);
                    common.put("Gap", value.doubleValue());
                    db().insert("MonthlyGunReports", common);
                }
                array[i][0] = Conv.toStr(item.get("GunId"));
                array[i][1] = BaseFunction.GetOilNameByID(item.i("GradeSpecificID"));
                array[i][2] = Conv.num(Conv.round(num, IniInfo.Vradio));
                array[i][3] = Conv.num(Conv.round(num2, IniInfo.Vradio));
                array[i][4] = Conv.num(Conv.round(Conv.toDouble(item.get("SumVolum")), IniInfo.Vradio));
                array[i][5] = value.toPlainString();
                i++;
            }
            DataTable t = TankData(DTEnd);
            String[][] array2 = new String[t.Rows.size()][16];
            db().delete("DailySalesReports", "{Shift} = :f", p("f", DailyF));
            for (i = 0; i < t.Rows.size(); i++) {
                int grade = Conv.toInt(t.get(i, "GradeSpecificID"));
                String text = density(grade);
                double value2 = Conv.toDouble(t.get(i, "EmpCardA")) + Conv.toDouble(t.get(i, "PerCardA")) + Conv.toDouble(t.get(i, "CreCardA"))
                    + Conv.toDouble(t.get(i, "NotCardA"));
                double value3 = Conv.toDouble(t.get(i, "EmpCardV")) + Conv.toDouble(t.get(i, "PerCardV")) + Conv.toDouble(t.get(i, "CreCardV"))
                    + Conv.toDouble(t.get(i, "NotCardV"));
                Row r = Row.of("StationID", IniInfo.StationID, "GradeSpecificID", grade, "PPU", Conv.toDouble(t.get(i, "PPU")), "CashV",
                    Conv.toDouble(t.get(i, "EmpCardV")), "CashM", Conv.toDouble(t.get(i, "EmpCardA")), "DepositV", Conv.toDouble(t.get(i, "PerCardV")),
                    "DepositM", Conv.toDouble(t.get(i, "PerCardA")), "CreditV", Conv.toDouble(t.get(i, "CreCardV")), "CreditM",
                    Conv.toDouble(t.get(i, "CreCardA")), "CreditCouponsV", Conv.toDouble(t.get(i, "CashCouponsV")), "CreditCouponsM",
                    Conv.toDouble(t.get(i, "CashCouponsM")), "RCV", value3, "RCM", value2, "AccountTime", now());
                if (DM == 0) {
                    r.put("Shift", DailyF);
                    r.put("Density", Conv.toDouble(text));
                    r.put("MaintV", Conv.toDouble(t.get(i, "NotCardV")));
                    r.put("MaintM", Conv.toDouble(t.get(i, "NotCardA")));
                    // Comme l'original : l'identifiant de l'employé est rangé dans CashCouponsM
                    r.put("CashCouponsM", IniInfo.LoginID);
                    db().insert("DailySalesReports", r);
                } else {
                    r.put("Month", DailyF);
                    r.put("Density", text.isEmpty() ? 0.0 : Conv.toDouble(text));
                    // Comme l'original : MainpoV reçoit le montant (NotCardA)
                    r.put("MainpoV", Conv.toDouble(t.get(i, "NotCardA")));
                    r.put("MainpoM", Conv.toDouble(t.get(i, "NotCardA")));
                    r.put("EmpoyeeID", IniInfo.LoginID);
                    db().insert("MonthSalesReports", r);
                }
                array2[i] = new String[] {BaseFunction.GetOilNameByID(grade), rs(t.get(i, "PPU"), IniInfo.Pradio), rs(t.get(i, "EmpCardA"), IniInfo.Mradio),
                    rs(t.get(i, "EmpCardV"), IniInfo.Vradio), rs(t.get(i, "PerCardA"), IniInfo.Mradio), rs(t.get(i, "PerCardV"), IniInfo.Vradio),
                    rs(t.get(i, "CreCardA"), IniInfo.Mradio), rs(t.get(i, "CreCardV"), IniInfo.Vradio), rs(t.get(i, "NotCardA"), IniInfo.Mradio),
                    rs(t.get(i, "NotCardV"), IniInfo.Vradio), "0", "0", rs(t.get(i, "CashCouponsM"), IniInfo.Vradio), rs(t.get(i, "CashCouponsV"), IniInfo.Vradio),
                    Conv.num(Conv.round(value2, IniInfo.Mradio)), Conv.num(Conv.round(value3, IniInfo.Vradio))};
            }
            db().delete("DailyCSTByUsers", "{PayoutGroupAmount} = :f", p("f", Conv.toDouble(DailyF)));
            DataTable dt = CardData(DTEnd);
            BigDecimal[] s = new BigDecimal[13];
            java.util.Arrays.fill(s, BigDecimal.ZERO);
            int num3 = 0;
            for (i = 0; i < dt.Rows.size(); i++) {
                String text2 = Conv.toStr(dt.get(i, "Asn"));
                BigDecimal vol = rd(dt.get(i, "Vol"), IniInfo.Vradio);
                BigDecimal amt = rd(dt.get(i, "Amount"), IniInfo.Mradio);
                int k = switch (text2) {
                    case "010001" -> 1;
                    case "000000" -> 7;
                    case "010004" -> 3;
                    case "010011" -> 5;
                    case "010006" -> 9;
                    default -> 0;
                };
                text2 = switch (k) {
                    case 1 -> L("UserCard");
                    case 7 -> L("NonCard");
                    case 3 -> L("EmployeeCard");
                    case 5 -> L("CreditCard");
                    case 9 -> L("RepairCard");
                    default -> text2;
                };
                if (k > 0) {
                    s[k] = s[k].add(vol);
                    s[k + 1] = s[k + 1].add(amt);
                }
                num3 += Conv.toInt(dt.get(i, "CardCount"));
                // Comme l'original : TotalVolume cumule les montants et TotalAmount les volumes
                s[11] = s[11].add(amt);
                s[12] = s[12].add(vol);
                db().insert(DM == 0 ? "DailyCardShift" : "MonthCardShift", Row.of("StationID", IniInfo.StationID, "ShiftNumber", DailyF, "CardType", text2,
                    "TotalVolume", Conv.toDecimal(dt.get(i, "Vol")), "TotalAmount", Conv.toDecimal(dt.get(i, "Amount")), "TotalCount",
                    Conv.toInt(dt.get(i, "CardCount")), "StartTime", Conv.toDateTime(dt.get(i, "MinTime")).format(Conv.STD), "EndTime",
                    Conv.toDateTime(dt.get(i, "MaxTime")).format(Conv.STD)));
            }
            db().insert(DM == 0 ? "DailyTotalShift" : "MonthTotalShift", Row.of("ShiftNumber", DailyF, "StationID", IniInfo.StationID, "TotalCount", num3,
                "TotalVolume", s[11], "TotalAmount", s[12], "TotalUserVolume", s[1], "TotalUserAmount", s[2], "TotalEmpVolume", s[3], "TotalEmpAmount", s[4],
                "TotalCreditVolume", s[5], "TotalCreditAmount", s[6], "TotalNotCardVolume", s[7], "TotalNotCardAmount", s[8], "TotalCashCouponsA", s[10],
                "TotalCashCouponsV", s[9]));
            tx.commit();
            MessageBox(L("OperateSuccess"));
            sender = "SearchButton";
            SearchButton_Click();
            Row station = db().first("Stations", "ORDER BY {StationID}", null);
            if (station == null) {
                throw new NullPointerException();
            }
            String[] out = {""};
            new ExportFile().DailyToExcel(station.str("Name") + L(DM == 0 ? "DailyReport" : "MonthReport"), DailyF, array, array2, dt, out);
            DownloadFile(out[0]);
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    public void RDaily_Load() {
        c("DTDate").Value(LocalDateTime.now());
        c("DailyTab").SelectedIndex(0);
        if (DM == 0) {
            c("label3").Text(L("SetlementTimeForDaily"));
            c("DailyReport").Text(L("DailyReport"));
            c("groupBox3").Text(L("DailyReportData"));
            c("CardDaily").Text(L("CardDailyReport"));
            c("DailyNoz").Text(L("NozDailyReport"));
            c("DailyTank").Text(L("EnergyDailyReport"));
        }
        if (DM == 1) {
            c("label3").Text(L("SetlementTimeForMonth"));
            c("DailyReport").Text(L("MonthReport"));
            c("groupBox3").Text(L("MonthReportData"));
            c("CardDaily").Text(L("CardMonthReport"));
            c("DailyNoz").Text(L("NozMonthReport"));
            c("DailyTank").Text(L("EnergyMonthReport"));
        }
    }

    /** SearchButton.PerformClick() */
    public void DTDate_ValueChanged() {
        sender = "SearchButton";
        SearchButton_Click();
    }
}
