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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ShiftForm : relève d'équipe. Les transactions de l'équipe sont marquées Shift = '2' pendant l'affichage,
 * puis la clôture enregistre DutyGunInfo / DutyBrandSales / DutyDenomination et renomme l'équipe.
 * L'original charge et clôture dans des threads derrière ProBar ; ici la requête attend la fin.
 */
@SwWindow("ShiftForm")
public class ShiftForm extends SwForm {

    private String ShiftStr = "";
    private String[][] NozTable;
    private String[][] TankTable;
    private LocalDateTime StartDateTime;
    private LocalDateTime EndDateTime;
    private int PageIndex;
    private int totalNumber;

    private static final String GROUP_SQL = "select {OilTransactions.GunId} AS GunId, {OilTransactions.GradeSpecificID} AS GradeSpecificID,"
        + " sum({OilTransactions.TruelyPay}) AS SumAmount, sum({OilTransactions.Volume}) AS SumVolume, min({OilTransactions.TrueTime}) AS SMinTime,"
        + " max({OilTransactions.TrueTime}) AS SMaxTime from [OilTransactions] where %s group by {OilTransactions.GunId}, {OilTransactions.GradeSpecificID}";

    private static final String TYPE_SQL = "select a.GradeSpecificID AS \"GradeSpecificID\", a.PPU AS \"PPU\", MIN(a.MinTime) as \"MinTime\","
        + " Max(a.MaxTime) as \"MaxTime\", SUM(CASE WHEN a.Asn in ('000000','110003') THEN a.Amount ELSE 0 END) AS \"NotCardA\","
        + " SUM(CASE WHEN a.Asn in ('000000','110003') THEN a.Volume ELSE 0 END) AS \"NotCardV\","
        + " SUM(CASE a.Asn WHEN '010004' THEN a.Amount ELSE 0 END) AS \"EmpCardA\", SUM(CASE a.Asn WHEN '010004' THEN a.Volume ELSE 0 END) AS \"EmpCardV\","
        + " SUM(CASE a.Asn WHEN '010001' THEN a.Amount ELSE 0 END) AS \"PerCardA\", SUM(CASE a.Asn WHEN '010001' THEN a.Volume ELSE 0 END) AS \"PerCardV\","
        + " SUM(CASE a.Asn WHEN '010011' THEN a.Amount ELSE 0 END) AS \"CreCardA\", SUM(CASE a.Asn WHEN '010011' THEN a.Volume ELSE 0 END) AS \"CreCardV\","
        + " SUM(CASE a.Asn WHEN '010006' THEN a.Amount ELSE 0 END) AS \"CashCouponsM\", SUM(CASE a.Asn WHEN '010006' THEN a.Volume ELSE 0 END) AS \"CashCouponsV\""
        + " from (select SUBSTRING({OilTransactions.Asn}, 1, 6) as Asn, {OilTransactions.GradeSpecificID} AS GradeSpecificID, {OilTransactions.PPU} AS PPU,"
        + " SUM({OilTransactions.TruelyPay}) as Amount, SUM({OilTransactions.Volume}) as Volume, Min({OilTransactions.TrueTime}) as MinTime,"
        + " MAX({OilTransactions.TrueTime}) as MaxTime from [OilTransactions] where {OilTransactions.Shift} = '2'"
        + " group by {OilTransactions.GradeSpecificID}, {OilTransactions.PPU}, SUBSTRING({OilTransactions.Asn}, 1, 6)) a group by a.GradeSpecificID, a.PPU";

    private static final String EMP_SQL = "select {OilTransactions.EmployeeID} AS EmployeeID, sum({OilTransactions.TruelyPay}) AS SumAmount,"
        + " sum({OilTransactions.Volume}) AS SumVolume from [OilTransactions] where {OilTransactions.Shift} = '2' group by {OilTransactions.EmployeeID}";

    private static final String WINDOW = "CAST({OilTransactions.TrueTime} AS timestamp) >= :a AND CAST({OilTransactions.TrueTime} AS timestamp) <= :b"
        + " AND {OilTransactions.Shift} = '1' AND {OilTransactions.GunId} = :g";

    private static BigDecimal rd(Object v, int n) {
        BigDecimal d = Conv.toDecimal(v);
        return d.scale() > n ? d.setScale(n, RoundingMode.HALF_EVEN) : d;
    }

    private Row shiftConfig() {
        return c("ShiftNo").SelectedRow();
    }

    private void GetShift() {
        try {
            List<Row> list = db().list("ShiftConfig");
            if (list.isEmpty()) {
                throw new IllegalStateException(L("ShiftSetError"));
            }
            c("ShiftNo").DataSource(list, "Name", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            Close();
        }
    }

    public void ShiftForm_Load() {
        try {
            if (IniInfo.NormalType == 2) {
                g("NozView").HeaderText("Amount", L("TotalKG"));
                g("TransactionView").HeaderText("dataGridViewTextBoxColumn5", L("KKG"));
            }
            GetShift();
            String text = db().scalar(db().sql("select max({DutyGunInfo.Shift}) from [DutyGunInfo]"), null, String.class);
            if (text == null || text.isEmpty()) {
                c("ShiftDT").Value(LocalDateTime.now());
                c("ShiftNo").SelectedIndex(0);
            } else {
                LocalDate value = LocalDate.of(Conv.toInt(text.substring(0, 4)), Conv.toInt(text.substring(4, 6)), Conv.toInt(text.substring(6, 8)));
                int i = Conv.toInt(text.substring(8, 10));
                c("ShiftDT").Value(value.atStartOfDay());
                ShiftNoCheck(i);
            }
            if (IniInfo.NormalType == 2) {
                g("NozView").HeaderText("OilPrice", L("StartKG"));
                g("NozView").HeaderText("Volume", L("EndKG"));
                g("NozView").HeaderText("Amount", L("TotalKG"));
            }
        } catch (RuntimeException e) {
            MessageBox("Load Error");
            Close();
        }
    }

    private void GetDateTime() {
        try {
            StartDateTime = null;
            EndDateTime = null;
            LocalDate value = c("ShiftDT").Value().toLocalDate();
            int num = shiftConfig().i("StartTime");
            int num2 = shiftConfig().i("EndTime");
            StartDateTime = value.atTime(num, 0);
            if (num >= num2) {
                EndDateTime = value.plusDays(1).atTime(num2, 0);
            } else if (num2 != 24) {
                EndDateTime = value.atTime(num2, 0);
            } else {
                EndDateTime = value.atTime(23, 59, 59);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private String shiftStr() {
        return c("ShiftDT").Value().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + BaseFunction.ConverToStr(shiftConfig().str("Name"), 2);
    }

    private Row first(Object gun, Object trueTime) {
        Row r = db().first("OilTransactions", "{TrueTime} = :t AND {GunId} = :g", p("t", trueTime, "g", gun));
        if (r == null) {
            throw new NullPointerException();
        }
        return r;
    }

    /** NozShiftTotal à partir d'un regroupement pistolet / carburant des transactions. */
    private Row nozTotal(Row item) {
        BigDecimal num = rd(Conv.toDouble(first(item.get("GunId"), item.get("SMinTime")).get("VolumeTotal"))
            - Conv.toDouble(first(item.get("GunId"), item.get("SMinTime")).get("Volume")), IniInfo.Vradio);
        BigDecimal num2 = rd(first(item.get("GunId"), item.get("SMaxTime")).get("VolumeTotal"), IniInfo.Vradio);
        return Row.of("GunID", item.get("GunId"), "OilType", BaseFunction.GetOilNameByID(item.i("GradeSpecificID")), "SumAmount",
            rd(item.get("SumAmount"), IniInfo.Mradio), "SumVolume", rd(item.get("SumVolume"), IniInfo.Vradio), "StartTime",
            item.str("SMinTime").replace("T", " "), "EndTime", item.str("SMaxTime").replace("T", " "), "StartVolumeTotal", num, "EndVolumeTotal", num2,
            "Error", rd(num2.subtract(num).subtract(Conv.toDecimal(item.get("SumVolume"))), IniInfo.Vradio));
    }

    private Row dutyNoz(Row x) {
        return Row.of("GunID", x.get("GunCode"), "OilType", BaseFunction.GetOilNameByID(x.i("GradeSpecificID")), "SumAmount", rd(x.get("Amount"), IniInfo.Mradio),
            "SumVolume", rd(x.get("Vol"), IniInfo.Vradio), "StartTime", Conv.toDateTime(x.get("StartTime")).format(Conv.STD), "EndTime",
            Conv.toDateTime(x.get("EndTime")).format(Conv.STD), "StartVolumeTotal", Conv.toDecimal(x.get("STARTPT")), "EndVolumeTotal",
            Conv.toDecimal(x.get("EndPT")), "Error", Conv.toDecimal(x.get("PTC")));
    }

    private void LoadDutyNoz() {
        List<Row> list = new ArrayList<>();
        for (Row x : db().list("DutyGunInfo", "{Shift} = :s", p("s", ShiftStr))) {
            list.add(dutyNoz(x));
        }
        g("NozView").DataSource(list);
    }

    /** Transactions de l'équipe : Shift '1' → '2' dans la plage de chaque pistolet (bornes élargies de Scope minutes). */
    private void LoadNoz() {
        try {
            db().exec(db().sql("OilTransactions", "update [OilTransactions] set {Shift} = '1' where {Shift} = '2'"), null);
            List<Row> list = new ArrayList<>();
            int ScopeMinute = shiftConfig().i("Scope");
            int ScopeMinF = -ScopeMinute;
            for (Row gun : db().list("GunConfig", "ORDER BY {GUNCODE}", null)) {
                Object g = gun.get("GUNCODE");
                String typeSql = "select {OilTransactions.TType} AS TType, min({OilTransactions.TrueTime}) AS MinTime, max({OilTransactions.TrueTime}) AS MaxTime"
                    + " from [OilTransactions] where " + WINDOW + " group by {OilTransactions.TType}";
                List<Row> list3 = db().query(db().sql(typeSql), p("a", StartDateTime.plusMinutes(ScopeMinF), "b", StartDateTime.plusMinutes(ScopeMinute), "g", g));
                List<Row> list4 = db().query(db().sql(typeSql), p("a", EndDateTime.plusMinutes(ScopeMinF), "b", EndDateTime.plusMinutes(ScopeMinute), "g", g));
                LocalDateTime DTS = null;
                LocalDateTime DTE = null;
                for (Row r : list3) {
                    if (r.i("TType") == 5) {
                        DTS = Conv.toDateTime(r.get("MinTime"));
                    }
                }
                for (Row r : list4) {
                    if (r.i("TType") == 6) {
                        DTE = Conv.toDateTime(r.get("MaxTime"));
                    }
                }
                // Requête de l'original dont le résultat n'est pas utilisé
                db().first("DutyGunInfo", "{GunCode} = :g AND CAST({Shift} AS integer) < :s ORDER BY {Shift} DESC",
                    p("g", g, "s", Integer.parseInt(ShiftStr)));
                if (DTS == null) {
                    DTS = StartDateTime.plusMinutes(ScopeMinF);
                }
                if (DTE == null) {
                    DTE = EndDateTime.plusMinutes(ScopeMinute);
                }
                for (Row item : db().query(db().sql(String.format(GROUP_SQL, WINDOW)), p("a", DTS, "b", DTE, "g", g))) {
                    list.add(nozTotal(item));
                }
                db().exec(db().sql("OilTransactions", "update [OilTransactions] set {Shift} = '2' where " + WINDOW), p("a", DTS, "b", DTE, "g", g));
            }
            g("NozView").DataSource(list);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private Row transactionTotal(Row x) {
        return Row.of("Posttc", x.get("POSTTC"), "GunID", x.get("GunId"), "EmpID", x.get("EmployeeID"), "Asn", x.get("Asn"), "Volume",
            rd(x.get("Volume"), IniInfo.Vradio), "Amount", rd(x.get("TruelyPay"), IniInfo.Mradio), "TrueTime", x.str("TrueTime").replace("T", " "),
            "VolumeTotal", rd(x.get("VolumeTotal"), IniInfo.Vradio), "OilType", BaseFunction.GetOilNameByID(x.i("GradeSpecificID")), "PPU",
            rd(x.get("PPU"), IniInfo.Pradio), "TType", BaseFunction.GetTType(x.i("TType"), x.str("Asn")), "Balance", rd(x.get("BAL"), IniInfo.Mradio),
            "PayMent", BaseFunction.GetPayType(x.i("Unit")));
    }

    /** LoadDutyTransactions / LoadTransactions / ReadLoadData : 50 transactions par page de l'équipe donnée. */
    private void LoadShiftTransactions(String shift, int PageIndex) {
        try {
            Sugar.Page pg = db().page("OilTransactions", "{Shift} = :s", p("s", shift), "{GunId}, {POSTTC}", PageIndex, 50);
            totalNumber = pg.total();
            List<Row> list = new ArrayList<>();
            for (Row x : pg.rows()) {
                list.add(transactionTotal(x));
            }
            g("TransactionView").DataSource(list);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private Row typeTotal(String name) {
        Row t = new Row();
        t.put("TypeName", name);
        for (String k : new String[] {"PPU", "EmpVolume", "EmpAmount", "UserVolume", "UserAmount", "CreditVolume", "CreditAmount", "NotCardVolume",
            "NotCardAmount", "TotalVolume", "TotalAmount", "CashCouponsV", "CashCouponsM"}) {
            t.put(k, BigDecimal.ZERO);
        }
        return t;
    }

    private static void addTo(Row total, Row r) {
        for (String k : new String[] {"EmpVolume", "EmpAmount", "UserVolume", "UserAmount", "CreditVolume", "CreditAmount", "NotCardVolume", "NotCardAmount",
            "TotalVolume", "TotalAmount", "CashCouponsV", "CashCouponsM"}) {
            total.put(k, Conv.toDecimal(total.get(k)).add(Conv.toDecimal(r.get(k))));
        }
    }

    /** TypeTotal d'une ligne DutyBrandSales ; withTotals : RCV / RCM relus (sinon recalculés). */
    private Row brandTotal(Row x, boolean readTotals) {
        Row t = typeTotal(BaseFunction.GetOilNameByID(x.i("GradeSpecificID")));
        t.put("PPU", rd(x.get("PPU"), IniInfo.Vradio));
        t.put("EmpVolume", rd(x.get("EmployeeV"), IniInfo.Vradio));
        t.put("EmpAmount", rd(x.get("EmployeeM"), IniInfo.Mradio));
        t.put("UserVolume", rd(x.get("UserV"), IniInfo.Vradio));
        t.put("UserAmount", rd(x.get("UserM"), IniInfo.Mradio));
        t.put("CreditVolume", rd(x.get("CreditV"), IniInfo.Vradio));
        t.put("CreditAmount", rd(x.get("CreditM"), IniInfo.Mradio));
        t.put("NotCardVolume", rd(x.get("TestV"), IniInfo.Vradio));
        t.put("NotCardAmount", rd(x.get("TestM"), IniInfo.Mradio));
        t.put("CashCouponsV", rd(x.get("CashCouponsV"), IniInfo.Vradio));
        t.put("CashCouponsM", rd(x.get("CashCouponsM"), IniInfo.Mradio));
        if (readTotals) {
            t.put("TotalVolume", rd(x.get("RCV"), IniInfo.Vradio));
            t.put("TotalAmount", rd(x.get("RCM"), IniInfo.Mradio));
        } else {
            sums(t);
        }
        return t;
    }

    private static void sums(Row t) {
        t.put("TotalVolume", rd(Conv.toDecimal(t.get("EmpVolume")).add(Conv.toDecimal(t.get("UserVolume"))).add(Conv.toDecimal(t.get("CreditVolume")))
            .add(Conv.toDecimal(t.get("NotCardVolume"))).add(Conv.toDecimal(t.get("CashCouponsV"))), IniInfo.Vradio));
        t.put("TotalAmount", rd(Conv.toDecimal(t.get("EmpAmount")).add(Conv.toDecimal(t.get("UserAmount"))).add(Conv.toDecimal(t.get("CreditAmount")))
            .add(Conv.toDecimal(t.get("NotCardAmount"))).add(Conv.toDecimal(t.get("CashCouponsM"))), IniInfo.Mradio));
    }

    /** LoadDutyTypeTotal : la ligne Total reste à zéro, comme l'original. */
    private void LoadDutyTypeTotal() {
        try {
            List<Row> list = new ArrayList<>();
            for (Row x : db().list("DutyBrandSales", "{Shift} = :s", p("s", ShiftStr))) {
                list.add(brandTotal(x, false));
            }
            list.add(typeTotal(L("Total")));
            g("TotalView").DataSource(list);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadTypeTotal() {
        try {
            List<Row> list = new ArrayList<>();
            Row total = typeTotal(L("Total"));
            for (Row r : db().query(db().sql(TYPE_SQL), null)) {
                Row t = typeTotal(BaseFunction.GetOilNameByID(r.i("GradeSpecificID")));
                t.put("PPU", rd(r.get("PPU"), IniInfo.Vradio));
                t.put("EmpVolume", rd(r.get("EmpCardV"), IniInfo.Vradio));
                t.put("EmpAmount", rd(r.get("EmpCardA"), IniInfo.Mradio));
                t.put("UserVolume", rd(r.get("PerCardV"), IniInfo.Vradio));
                t.put("UserAmount", rd(r.get("PerCardA"), IniInfo.Mradio));
                t.put("CreditVolume", rd(r.get("CreCardV"), IniInfo.Vradio));
                t.put("CreditAmount", rd(r.get("CreCardA"), IniInfo.Mradio));
                t.put("NotCardVolume", rd(r.get("NotCardV"), IniInfo.Vradio));
                t.put("NotCardAmount", rd(r.get("NotCardA"), IniInfo.Mradio));
                t.put("CashCouponsV", rd(r.get("CashCouponsV"), IniInfo.Vradio));
                t.put("CashCouponsM", rd(r.get("CashCouponsM"), IniInfo.Mradio));
                sums(t);
                addTo(total, t);
                list.add(t);
            }
            list.add(total);
            g("TotalView").DataSource(list);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadDutyEmp() {
        try {
            List<Row> list = new ArrayList<>();
            for (Row x : db().list("DutyDenomination", "{Shift} = :s", p("s", ShiftStr))) {
                list.add(Row.of("Emp", BaseFunction.GetEmpName(x.i("GradeSpecificID")), "Amount", rd(x.get("RC"), IniInfo.Mradio), "Volume",
                    rd(x.get("RC2"), IniInfo.Vradio)));
            }
            g("OilTypeView").DataSource(list);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadEmpTotal() {
        try {
            List<Row> list = new ArrayList<>();
            for (Row x : db().query(db().sql(EMP_SQL), null)) {
                list.add(Row.of("Emp", BaseFunction.GetEmpName(x.i("EmployeeID")), "Amount", rd(x.get("SumAmount"), IniInfo.Mradio), "Volume",
                    rd(x.get("SumVolume"), IniInfo.Vradio)));
            }
            g("OilTypeView").DataSource(list);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void SetUIMessage(int PageIndex, int TotalNumber) {
        int pages = TotalNumber / 50 + 1;
        if ("zh-CN".equals(IniInfo.Language)) {
            c("MessageLabel").Text("共" + TotalNumber + "条记录，每页50条，共" + pages + "页");
        } else {
            c("MessageLabel").Text("Total Record:" + TotalNumber + "，50 records per page，Total Page:" + pages);
        }
        c("CurPage").Text(String.valueOf(PageIndex + 1));
        c("TotalPageSecond").Text(String.valueOf(pages));
        SetButtonEnable(PageIndex, TotalNumber);
    }

    private void StartThread() {
        try {
            PageIndex = 0;
            if (!db().list("DutyGunInfo", "{Shift} = :s", p("s", ShiftStr)).isEmpty()) {
                LoadDutyNoz();
                LoadShiftTransactions(ShiftStr, 0);
                LoadDutyEmp();
                LoadDutyTypeTotal();
            } else {
                LoadNoz();
                LoadShiftTransactions("2", 0);
                SetUIMessage(PageIndex, totalNumber);
                LoadEmpTotal();
                LoadTypeTotal();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void CloseThread() {
        try {
            NozShift();
            TypeShift();
            EmpShift();
            db().exec(db().sql("OilTransactions", "update [OilTransactions] set {Shift} = :s where {Shift} = '2'"), p("s", ShiftStr));
            ShiftReportExcel();
            ShiftNoCheck(-1);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CurPage_TextChanged() {
        try {
            int num = Conv.toInt(c("CurPage").Text());
            int num2 = Conv.toInt(c("TotalPageSecond").Text());
            if (num < 0 || num > num2) {
                throw new IllegalStateException(L("UnvalidatedInput"));
            }
            GetDateTime();
            String str = shiftStr();
            if (db().first("DutyGunInfo", "{Shift} = :s", p("s", str)) != null) {
                ShiftStr = str;
                LoadShiftTransactions(ShiftStr, num);
            } else {
                LoadShiftTransactions("2", num);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            GetDateTime();
            LoadShiftTransactions("2", 0);
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

    private void SetButtonEnable(int page, int totalNumber) {
        c("Cur").Enabled(page != 1);
        c("CurEnd").Enabled(page != 1);
        boolean last = totalNumber / 50 + 1 == page;
        c("Next").Enabled(!last);
        c("NextEnd").Enabled(!last);
    }

    public void ShiftClose_Click() {
        try {
            if (c("CheckedLabel").Visible()) {
                MessageBox("已交班");
                return;
            }
            GetDateTime();
            ShiftStr = shiftStr();
            if (g("NozView").RowCount() <= 0) {
                throw new IllegalStateException(L("DataEmpty"));
            }
            CloseThread();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void NozShift() {
        var tx = db().begin();
        try {
            if (db().first("DutyGunInfo", "{Shift} = :s", p("s", ShiftStr)) != null) {
                throw new IllegalStateException(L("ShiftAlreadlyComplete"));
            }
            List<Row> list = db().query(db().sql(String.format(GROUP_SQL, "{OilTransactions.Shift} = '2'")), null);
            NozTable = new String[list.size()][6];
            int num = 0;
            for (Row item : list) {
                Row a = first(item.get("GunId"), item.get("SMinTime"));
                BigDecimal num2 = rd(Conv.toDouble(a.get("VolumeTotal")) - Conv.toDouble(a.get("Volume")), IniInfo.Vradio);
                BigDecimal num3 = rd(first(item.get("GunId"), item.get("SMaxTime")).get("VolumeTotal"), IniInfo.Vradio);
                Row duty = Row.of("Amount", Conv.toDouble(item.get("SumAmount")), "EmployeeID", IniInfo.LoginID, "EndPT", rd(num3, IniInfo.Vradio).doubleValue(),
                    "EndTime", Conv.toDateTime(item.get("SMaxTime")), "GradeSpecificID", item.get("GradeSpecificID"), "GunCode", item.get("GunId"), "OperatorNO",
                    IniInfo.LoginID, "Shift", ShiftStr, "STARTPT", rd(num2, IniInfo.Vradio).doubleValue(), "StartTime", Conv.toDateTime(item.get("SMinTime")),
                    "StationID", IniInfo.StationID, "Time", LocalDateTime.now(), "Vol", Conv.round(Conv.toDouble(item.get("SumVolume")), IniInfo.Vradio),
                    "UploadFlag", 0, "PTC", rd(num3.subtract(num2).subtract(Conv.toDecimal(item.get("SumVolume"))), IniInfo.Vradio).doubleValue(), "Memo", "");
                NozTable[num] = new String[] {Conv.toStr(duty.get("GunCode")), BaseFunction.GetOilNameByID(item.i("GradeSpecificID")),
                    Conv.toStr(duty.get("STARTPT")), Conv.toStr(duty.get("EndPT")), Conv.toStr(duty.get("Vol")), Conv.toStr(duty.get("PTC"))};
                num++;
                db().insert("DutyGunInfo", duty);
            }
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    private void TypeShift() {
        var tx = db().begin();
        try {
            if (db().first("DutyBrandSales", "{Shift} = :s", p("s", ShiftStr)) != null) {
                throw new IllegalStateException(L("ShiftAlreadlyComplete"));
            }
            List<Row> rows = db().query(db().sql(TYPE_SQL), null);
            TankTable = new String[rows.size()][16];
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                int v = IniInfo.Vradio;
                int m = IniInfo.Mradio;
                double ev = rd(r.get("EmpCardV"), v).doubleValue();
                double em = rd(r.get("EmpCardA"), m).doubleValue();
                double uv = rd(r.get("PerCardV"), v).doubleValue();
                double um = rd(r.get("PerCardA"), m).doubleValue();
                double cv = rd(r.get("CreCardV"), v).doubleValue();
                double cm = rd(r.get("CreCardA"), m).doubleValue();
                double tv = rd(r.get("NotCardV"), v).doubleValue();
                double tm = rd(r.get("NotCardA"), m).doubleValue();
                // Comme l'original : CashCouponsM arrondi à Vradio et CashCouponsV à Mradio
                double ccm = rd(r.get("CashCouponsM"), v).doubleValue();
                double ccv = rd(r.get("CashCouponsV"), m).doubleValue();
                double ppu = rd(r.get("PPU"), v).doubleValue();
                double rcv = Conv.round(ev + uv + cv + tv + ccv, v);
                double rcm = Conv.round(em + um + cm + tm + ccm, m);
                db().insert("DutyBrandSales", Row.of("Shift", ShiftStr, "GradeSpecificID", r.i("GradeSpecificID"), "PPU", ppu, "EmployeeV", ev, "EmployeeM", em,
                    "UserV", uv, "UserM", um, "CreditV", cv, "CreditM", cm, "TestV", tv, "TestM", tm, "CashCouponsM", ccm, "CashCouponsV", ccv, "RCV", rcv,
                    "RCM", rcm));
                TankTable[i] = new String[] {BaseFunction.GetOilNameByID(r.i("GradeSpecificID")), Conv.num(ppu), Conv.num(em), Conv.num(ev), Conv.num(um),
                    Conv.num(uv), Conv.num(cm), Conv.num(cv), "0", "0", Conv.num(tm), Conv.num(tv), Conv.num(ccm), Conv.num(ccv), Conv.num(rcm), Conv.num(rcv)};
            }
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    private void EmpShift() {
        var tx = db().begin();
        try {
            if (db().first("DutyDenomination", "{Shift} = :s", p("s", ShiftStr)) != null) {
                throw new IllegalStateException(L("ShiftAlreadlyComplete"));
            }
            for (Row item : db().query(db().sql(EMP_SQL), null)) {
                db().insert("DutyDenomination", Row.of("Shift", ShiftStr, "GradeSpecificID", item.get("EmployeeID"), "RC",
                    rd(item.get("SumAmount"), IniInfo.Mradio).doubleValue(), "RC2", rd(item.get("SumVolume"), IniInfo.Vradio).doubleValue()));
            }
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    private void ShiftReportExcel() {
        try {
            Row station = db().first("Stations", "ORDER BY {StationID}", null);
            if (station == null) {
                throw new NullPointerException();
            }
            String[] out = {""};
            new ExportFile().DailyToExcel(station.str("Name") + L("Shift"), ShiftStr, NozTable, TankTable, new DataTable(), out);
            DownloadFile(out[0]);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** ShiftDT_ValueChanged et ShiftNo_SelectedIndexChanged : équipe déjà clôturée (relecture) ou en cours (calcul). */
    private void ShowShift() {
        GetDateTime();
        ShiftStr = shiftStr();
        boolean closed = db().first("DutyGunInfo", "{Shift} = :s", p("s", ShiftStr)) != null;
        c("CheckedLabel").Visible(closed);
        if (closed) {
            ReadThread();
        } else {
            StartThread();
        }
    }

    public void ShiftDT_ValueChanged() {
        ShowShift();
    }

    public void ShiftNo_SelectedIndexChanged() {
        ShowShift();
    }

    private void ReadThread() {
        try {
            PageIndex = 0;
            ReadLoadNoz();
            ReadLoadType();
            ReadLoadEmp();
            LoadShiftTransactions(ShiftStr, 0);
            SetUIMessage(PageIndex, totalNumber);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ReadLoadNoz() {
        try {
            List<Row> list = new ArrayList<>();
            for (Row x : db().list("DutyGunInfo", "{Shift} = :s ORDER BY {GunCode}", p("s", ShiftStr))) {
                list.add(dutyNoz(x));
            }
            g("NozView").DataSource(list);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ReadLoadType() {
        try {
            List<Row> list = new ArrayList<>();
            Row total = typeTotal(L("Total"));
            for (Row x : db().list("DutyBrandSales", "{Shift} = :s ORDER BY {GradeSpecificID}", p("s", ShiftStr))) {
                Row t = brandTotal(x, true);
                addTo(total, t);
                list.add(t);
            }
            list.add(total);
            g("TotalView").DataSource(list);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ReadLoadEmp() {
        try {
            List<Row> list = new ArrayList<>();
            for (Row x : db().list("DutyDenomination", "{Shift} = :s ORDER BY {GradeSpecificID}", p("s", ShiftStr))) {
                list.add(Row.of("Emp", BaseFunction.GetEmpName(x.i("GradeSpecificID")), "Amount", rd(x.get("RC"), IniInfo.Mradio), "Volume",
                    rd(x.get("RC2"), IniInfo.Vradio)));
            }
            g("OilTypeView").DataSource(list);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ShiftForm_FormClosed() {
        db().exec(db().sql("OilTransactions", "update [OilTransactions] set {Shift} = '1' where {Shift} = '2'"), null);
    }

    private void ShiftNoCheck(int i) {
        try {
            if (i == -1) {
                ShiftNoCheck(c("ShiftNo").SelectedIndex() + 1);
            } else if (i < c("ShiftNo").Items().size()) {
                c("ShiftNo").SelectedIndex(i);
            } else {
                c("ShiftDT").Value(c("ShiftDT").Value().plusDays(1));
                c("ShiftNo").SelectedIndex(0);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
