package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OilTransacation : recherche des transactions de carburant (filtres, totaux par type de carte, 50 par page) et export. */
@SwWindow("OilTransacation")
public class OilTransacation extends SwForm {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OilTransacation.class);

    /** Filtres de GetSugarQueryable : clause WHERE et paramètres (null si la requête est invalide). */
    private record Query(String where, Map<String, Object> params) {
    }

    public void OilTransacation_Load() {
        LoadCardNo();
        LoadAsn();
        LoadCustomerName();
        LoadOilType();
        LoadNoz();
        LoadEmp();
        LoadShiftDuty();
        c("ShiftSelect").SelectedIndex(0);
        c("CardTypeSelect").SelectedIndex(0);
        c("OrderSelect").SelectedIndex(0);
        c("StartDate").Value(LocalDateTime.now().minusDays(3));
        c("EndDate").Value(LocalDateTime.now().plusHours(1));
        if (IniInfo.NormalType == 2) {
            g("TransactionView").HeaderText("VolumeTotal", L("Quality"));
            g("TotalSumView").HeaderText("TotalVolume", L("TotalQuality"));
        }
    }

    private void LoadShiftDuty() {
        try {
            c("ShiftDuty").DataSource(db().list("ShiftConfig"), "Name", "ID");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void LoadOilType() {
        c("OilCodeSelect").DataSource(db().list("GradeSpecifics"), "Name", "GradeSpecificID");
    }

    private void LoadNoz() {
        c("NozSelect").DataSource(db().list("GunConfig"), "GUNCODE", "GUNCODE");
    }

    private void LoadEmp() {
        c("EmpSelect").DataSource(db().list("Employees"), "Name", "EmployeeID");
    }

    /** Valeurs distinctes non vides d'une colonne : suggestions de saisie du champ. */
    private void distinct(String table, String col, String box) {
        List<String> list = new ArrayList<>();
        for (Row r : db().query(db().sql("select {" + table + "." + col + "} AS v from [" + table + "] where {" + table + "." + col + "} <> ''"
            + " group by {" + table + "." + col + "}"), null)) {
            list.add(r.str("v"));
        }
        c(box).AutoCompleteCustomSource(list);
    }

    private void LoadCardNo() {
        distinct("CustomerCardInfos", "VehicleNo", "CardNoSelect");
    }

    private void LoadAsn() {
        distinct("CustomerCardInfos", "ASN", "AsnSelect");
    }

    private void LoadCustomerName() {
        distinct("Customers", "Name", "UserSelect");
    }

    private String GetCardType(String CardType) {
        return switch (CardType) {
            case "000000" -> L("NonCard");
            case "010001" -> L("UserCard");
            case "010004" -> L("EmployeeCard");
            case "010011" -> L("CreditCard");
            case "010013" -> L("AssistantCard");
            case "010006" -> L("RepairCard");
            default -> "other";
        };
    }

    private List<Row> GetOil(int page) {
        List<Row> result = new ArrayList<>();
        try {
            Query q = GetSugarQueryable();
            if (q == null) {
                throw new NullPointerException();
            }
            if (page == 1) {
                List<Row> list = db().query(db().sql("select substring({OilTransactions.Asn}, 1, 6) AS Key, sum({OilTransactions.TruelyPay}) AS SumAmount,"
                    + " sum({OilTransactions.Volume}) AS SumVolume, count(*) AS SumCount from [OilTransactions]" + (q.where == null ? "" : " where " + q.where)
                    + " group by substring({OilTransactions.Asn}, 1, 6) order by min({OilTransactions.ID})"), q.params);
                g("TotalSumView").RowsClear();
                BigDecimal value = BigDecimal.ZERO;
                BigDecimal d = BigDecimal.ZERO;
                long num = 0;
                for (Row item : list) {
                    value = value.add(Conv.toDecimal(item.get("SumAmount")));
                    d = d.add(Conv.toDecimal(item.get("SumVolume")));
                    num += Conv.toLong(item.get("SumCount"));
                }
                g("TotalSumView").RowsAdd(L("Total"), Conv.round(d, IniInfo.Vradio), Conv.round(value.doubleValue(), IniInfo.Mradio), num);
                for (Row item : list) {
                    g("TotalSumView").RowsAdd(GetCardType(item.str("Key")), Conv.round(Conv.toDecimal(item.get("SumVolume")), IniInfo.Vradio),
                        Conv.round(Conv.toDouble(item.get("SumAmount")), IniInfo.Mradio), Conv.toLong(item.get("SumCount")));
                }
            }
            String order = c("OrderSelect").SelectedIndex() != 0 ? "{GunId}" : "{TrueTime} DESC";
            Sugar.Page pg = db().page("OilTransactions", q.where, q.params, order, page, 50);
            result = pg.rows();
            if (IniInfo.Language.contains("en")) {
                c("TotalCount").Text("Record Total:" + pg.total() + ",50 per page Total Page:" + pg.totalPage());
            } else {
                c("TotalCount").Text("共" + pg.total() + "条记录,每页50条 共" + pg.totalPage() + "页");
            }
            c("CurPage").Text(String.valueOf(page));
            c("TotalPageSecond").Text(String.valueOf(pg.totalPage()));
            SetButtonEnable(page);
            return result;
        } catch (RuntimeException e) {
            // L'original ignore l'erreur ; trace seulement dans le journal
            log.warn("GetOil", e);
            return result;
        }
    }

    private Query GetSugarQueryable() {
        try {
            LocalDateTime Start = c("StartDate").Value();
            LocalDateTime End = c("EndDate").Value();
            if (End.isBefore(Start)) {
                throw new IllegalStateException(L("EndOutofStart"));
            }
            List<String> w = new ArrayList<>();
            Map<String, Object> params = new LinkedHashMap<>();
            if (c("DateCheck").Checked()) {
                w.add("CAST({OilTransactions.TrueTime} AS timestamp) >= :start AND CAST({OilTransactions.TrueTime} AS timestamp) <= :end");
                params.put("start", Start);
                params.put("end", End);
            }
            if (c("OilCodeCheck").Checked()) {
                w.add("{OilTransactions.GradeSpecificID} = :grade");
                params.put("grade", c("OilCodeSelect").SelectedRow().i("GradeSpecificID"));
            }
            if (c("ShiftSelect").SelectedIndex() == 1) {
                w.add("length({OilTransactions.Shift}) > 3");
            }
            if (c("ShiftSelect").SelectedIndex() == 2) {
                w.add("length({OilTransactions.Shift}) = 1");
            }
            if (c("CardNoCheck").Checked()) {
                w.add("{OilTransactions.CarNumber} = :car");
                params.put("car", c("CardNoSelect").Text());
            }
            if (c("NozCheck").Checked()) {
                w.add("{OilTransactions.GunId} = :noz");
                params.put("noz", Conv.toInt(c("NozSelect").Text()));
            }
            if (c("CardTypeCheck").Checked()) {
                String[] types = {"010004", "010001", "010011", "010013"};
                int i = c("CardTypeSelect").SelectedIndex();
                if (i >= 0 && i < types.length) {
                    w.add("substring({OilTransactions.Asn}, 1, 6) = :ctype");
                    params.put("ctype", types[i]);
                }
            }
            if (c("AsnCheck").Checked()) {
                w.add("{OilTransactions.Asn} = :asn");
                params.put("asn", c("AsnSelect").Text());
            }
            if (c("EmpCheck").Checked()) {
                w.add("{OilTransactions.EmployeeID} = :emp");
                params.put("emp", c("EmpSelect").SelectedRow().i("EmployeeID"));
            }
            if (c("UserCheck").Checked()) {
                List<String> list2 = new ArrayList<>();
                for (Row r : db().query(db().sql("select {CustomerCardInfos.ASN} AS ASN from [Customers] inner join [CustomerCardInfos]"
                    + " on {Customers.CustomerID} = {CustomerCardInfos.CustomerID} where {Customers.Name} = :n"), p("n", c("UserSelect").Text()))) {
                    list2.add(r.str("ASN"));
                }
                if (!list2.isEmpty()) {
                    w.add("{OilTransactions.Asn} IN (:asns)");
                    params.put("asns", list2);
                }
            }
            if (c("ShiftDutyCheck").Checked()) {
                String text = c("ShiftDate").Value().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                w.add("{OilTransactions.Shift} = :duty");
                params.put("duty", text + BaseFunction.SetStrLen(c("ShiftDuty").SelectedValue().toString(), 2));
            }
            return new Query(w.isEmpty() ? null : String.join(" AND ", w), params);
        } catch (RuntimeException e) {
            log.warn("GetSugarQueryable", e);
            return null;
        }
    }

    private static String s(Object v) {
        return v == null ? "" : Conv.toStr(v);
    }

    private DataTable ListToDataTabel(List<Row> oils) {
        DataTable dt = new DataTable();
        try {
            for (String col : new String[] {"GunId", "GradeSpecificID", "PPU", "Volume", "AMN", "TruelyPay", "TrueTime", "Asn", "BAL", "TType", "Unit",
                "POSP", "POSTTC", "EmployeeID", "VolumeTotal", "Shift", "CarNumber", "Mileage", "Discount", "Coupon", "CouponValue"}) {
                dt.addColumn(col);
            }
            for (Row oil : oils) {
                dt.addRow(s(oil.get("GunId")), BaseFunction.GetOilNameByID(oil.i("GradeSpecificID")), s(oil.get("PPU")), s(oil.get("Volume")),
                    s(oil.get("AMN")), s(oil.get("TruelyPay")), s(oil.get("TrueTime")), oil.get("Asn").toString(), s(oil.get("BAL")),
                    BaseFunction.GetTType(oil.i("TType"), oil.str("Asn")), BaseFunction.GetPayType(oil.i("Unit")), s(oil.get("POSP")),
                    oil.get("POSTTC").toString(), BaseFunction.GetEmpName(oil.i("EmployeeID")), s(oil.get("VolumeTotal")), oil.get("Shift").toString(),
                    s(oil.get("CarNumber")), s(oil.get("Mileage")), s(oil.get("Discount")), oil.get("Coupon").toString(), s(oil.get("CouponValue")));
            }
            return dt;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void SetButtonEnable(int page) {
        c("Cur").Enabled(page != 1);
        c("CurEnd").Enabled(page != 1);
        boolean last = Conv.toInt(c("TotalPageSecond").Text()) == page;
        c("Next").Enabled(!last);
        c("NextEnd").Enabled(!last);
    }

    public void Select_Click() {
        try {
            ShowData(GetOil(1));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static int hex(Object v) {
        return v == null ? 0 : Integer.parseInt(v.toString(), 16);
    }

    private void ShowData(List<Row> list) {
        g("TransactionView").RowsClear();
        for (Row item : list) {
            g("TransactionView").RowsAdd(item.get("GunId"), BaseFunction.GetOilNameByID(item.i("GradeSpecificID")), item.get("PPU"), item.get("Volume"),
                item.get("AMN"), item.get("TruelyPay"), item.get("TrueTime"), item.get("Asn"), item.get("BAL"),
                BaseFunction.GetTType(item.i("TType"), item.str("Asn")), BaseFunction.GetPayType(item.i("Unit")), item.get("POSP"), hex(item.get("POSTTC")),
                BaseFunction.GetEmpName(item.i("EmployeeID")), item.get("VolumeTotal"), item.get("Shift"), item.get("CarNumber"), item.get("Mileage"),
                item.get("Discount"), item.get("Coupon"), item.get("CouponValue"));
        }
    }

    public void Next_Click() {
        try {
            switch (sender) {
                case "Next" -> {
                    int num2 = Conv.toInt(c("TotalPageSecond").Text());
                    int num3 = Conv.toInt(c("CurPage").Text());
                    if (num3 + 1 > num2) {
                        throw new IllegalStateException(L("LastPage"));
                    }
                    c("CurPage").Text(String.valueOf(num3 + 1));
                }
                case "NextEnd" -> c("CurPage").Text(c("TotalPageSecond").Text());
                case "Cur" -> {
                    int num = Conv.toInt(c("CurPage").Text());
                    if (num == 1) {
                        throw new IllegalStateException(L("FirstPage"));
                    }
                    c("CurPage").Text(String.valueOf(num - 1));
                }
                case "CurEnd" -> c("CurPage").Text("1");
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CurPage_TextChanged() {
        try {
            int num = Conv.toInt(c("CurPage").Text());
            int num2 = Conv.toInt(c("TotalPageSecond").Text());
            if (num < 0 || num > num2) {
                throw new IllegalStateException(L("UnvalidatedRequest"));
            }
            ShowData(GetOil(num));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            ShowData(GetOil(1));
        }
    }

    public void ExportPage_Click() {
        try {
            ExportFile exportFile = new ExportFile();
            String[] out = {""};
            switch (sender) {
                case "ExportPage" -> {
                    Query q = GetSugarQueryable();
                    List<Row> oils = db().page("OilTransactions", q.where, q.params, null, Conv.toInt(c("CurPage").Text()), 50).rows();
                    exportFile.DataTableToExcel(L("Transaction"), ListToDataTabel(oils), out);
                    DownloadFile(out[0]);
                }
                case "Export" -> {
                    Query q = GetSugarQueryable();
                    List<Row> oils = db().list("OilTransactions", q.where, q.params);
                    exportFile.DataTableToExcel(L("Transaction"), ListToDataTabel(oils), out);
                    DownloadFile(out[0]);
                }
                default -> {
                    // PrintPage / Print : sans effet dans l'original
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
