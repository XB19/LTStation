package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar.Page;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** NormalTransaction : recherche des transactions du mode Normal (période, pistolet, produit), totaux, export, ticket du boîtier. */
@SwWindow("NormalForm.NormalTransaction")
public class NormalTransaction extends SwForm {

    private int totalPage;
    private String CurP = "";
    private List<Row> groups = new ArrayList<>();

    @Override
    protected void init() {
        LoadSelect();
        c("DTStart").Value(LocalDate.now().atStartOfDay());
    }

    private void LoadSelect() {
        try {
            c("NozSelect").DataSource(db().list("NormalNoz", "{IsUsing} = true ORDER BY {NozID}", null), "NozID", "NozID");
            c("EnergySelect").DataSource(db().list("NormalGrade", "{IsUsing} = true", null), "GradeName", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void QueryBtn_Click() {
        GetGroup();
        ShowData(1);
        GetTotal();
    }

    private void GetGroup() {
        try {
            CustomerRespone r = BaseFunction.post("ExtendUpdate/ExtendGetGroup",
                SW.dto().make("Card_Login_Return", "CompanyID", IniInfo.CompanyID, "EmpID", IniInfo.LoginID));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            groups = new ArrayList<>(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())));
            groups.add(Row.of("GroupID", 100000001, "Name", L("Person")));
            groups.sort(Comparator.comparingInt(t -> t.i("GroupID")));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private String groupName(int group) {
        if (group <= 100000001) {
            return "";
        }
        return groups.stream().filter(g -> g.i("GroupID") == group).map(g -> g.str("Name")).findFirst().orElse("");
    }

    /** Filtre : période, pistolet (si coché) et produit (si coché). */
    private String where(Map<String, Object> params) {
        params.put("s", BaseFunction.ConvertDateTimeToInt(c("DTStart").Value()));
        params.put("e", BaseFunction.ConvertDateTimeToInt(c("DTEnd").Value()));
        StringBuilder w = new StringBuilder("{CreateTime} >= :s AND {CreateTime} <= :e");
        if (c("NozCheck").Checked()) {
            params.put("n", Conv.toInt(c("NozSelect").Text().isEmpty() ? "0" : c("NozSelect").Text()));
            w.append(" AND {NozID} = :n");
        }
        if (c("EnergyCheck").Checked()) {
            Object g = c("EnergySelect").SelectedValue();
            params.put("g", g == null ? 0 : Conv.toInt(g));
            w.append(" AND {GradeID} = :g");
        }
        return w.toString();
    }

    private void ShowData(int PageIndex) {
        try {
            Map<String, Object> params = new java.util.LinkedHashMap<>();
            Page page = db().page("NormalTransaction", where(params), params, "{CreateTime} DESC", PageIndex, 50);
            totalPage = page.totalPage();
            c("PageLabel").Text(PageIndex + "/" + totalPage);
            g("TransactionView").RowsClear();
            int i = 0;
            for (Row item : page.rows()) {
                String cardNo = item.str("CardNo");
                g("TransactionView").RowsAdd(item.get("ID"), item.get("NozID"), item.str("GradeName"), cardNo, item.get("Amount"), item.get("Volume"),
                    "".equals(cardNo) ? "" : Conv.netRound(item.dec("CardBalance"), 3).toPlainString(), item.get("Price"), item.get("AmountTotal"),
                    item.get("VolumeTotal"), item.get("RealAmount"), item.get("DiscountValue"), BaseFunction.StampToDateTime(item.str("CreateTime")),
                    item.str("HandShiftNo"), item.str("CardName"), item.str("CarNumber"), groupName(item.i("CardGroup")), item.get("Mileage"));
                if (item.i("Settlement") != 0) {
                    g("TransactionView").RowBackColor(i, "Red");
                }
                i++;
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void GetTotal() {
        try {
            Map<String, Object> params = new java.util.LinkedHashMap<>();
            Row sum = db().queryFirst(db().sql("NormalTransaction", "select SUM({Amount}) AS Amount, SUM({Volume}) AS Volume from [NormalTransaction] where "
                + where(params)), params);
            c("AmountSum").Text(Conv.netRound(sum.dec("Amount"), 3).toPlainString());
            c("VolumeSum").Text(Conv.netRound(sum.dec("Volume"), 3).toPlainString());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CurPage_TextChanged() {
        try {
            int result;
            try {
                result = Integer.parseInt(c("CurPage").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("PageError"));
            }
            if (result < 1) {
                throw new IllegalStateException(L("FirstPage"));
            }
            if (result > totalPage && totalPage != 0) {
                throw new IllegalStateException(L("LastPage"));
            }
            CurP = String.valueOf(result);
            ShowData(result);
            GetTotal();
        } catch (RuntimeException ex) {
            c("CurPage").Text(CurP);
            MessageBox(ex.getMessage());
        }
    }

    public void Next_Click() {
        try {
            int cur = Conv.toInt(c("CurPage").Text().isEmpty() ? "1" : c("CurPage").Text());
            if (sender.equals("Next")) {
                if (cur + 1 > totalPage) {
                    throw new IllegalStateException(L("LastPage"));
                }
                c("CurPage").Text(String.valueOf(cur + 1));
                CurPage_TextChanged();
            } else if (sender.equals("Cur")) {
                if (cur - 1 <= 0) {
                    throw new IllegalStateException(L("FirstPage"));
                }
                c("CurPage").Text(String.valueOf(cur - 1));
                CurPage_TextChanged();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Export PDF des transactions (résumé espèces / cartes) ; le choix Excel n'exporte rien, comme l'original. */
    public void Export_Click() {
        SaveFileDialog("PDF(*.pdf)|*.pdf|Excel(*.xls)|*.xls", L("Transaction") + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), fileName -> {
            try {
                Export(fileName);
            } catch (RuntimeException ex) {
                MessageBox(ex.getMessage());
            }
        });
    }

    private void Export(String fileName) {
        long dt = BaseFunction.ConvertDateTimeToInt(c("DTStart").Value());
        long de = BaseFunction.ConvertDateTimeToInt(c("DTEnd").Value());
        BigDecimal[] s = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (Row item : db().query(db().sql("select SUBSTRING({NormalTransaction.CardNo}, 0, 9) as CardNo, SUM({NormalTransaction.Volume}) as Volume,"
            + " SUM({NormalTransaction.Amount}) as Amount from [NormalTransaction] where {NormalTransaction.CreateTime} between " + dt + " and " + de
            + " group by SUBSTRING({NormalTransaction.CardNo}, 0, 9)"), null)) {
            String card = item.str("CardNo");
            int k = card == null || card.isEmpty() ? 0 : 2;
            s[k] = s[k].add(item.dec("Amount"));
            s[k + 1] = s[k + 1].add(item.dec("Volume"));
            s[4] = s[4].add(item.dec("Amount"));
            s[5] = s[5].add(item.dec("Volume"));
        }
        Row summary = Row.of("Time", c("DTStart").Value().format(com.ltstation.center.legacy.Conv.STD) + " --- " + c("DTEnd").Value().format(Conv.STD),
            "CashA", s[0], "CashV", s[1], "CardA", s[2], "CardV", s[3], "TotalA", s[4], "TotalV", s[5]);
        String[] head = {L("StationName"), L("NozID"), L("Energy"), L("Price"), L("Amount"), L("Volume"), L("CardNo"), L("Balance"), L("AmountTotal"),
            L("VolumeTotal"), L("Time"), L("CurrentShift"), L("UserName"), L("CarNumber"), L("CardGroup"), L("RealAmount"), L("DiscountValue"), L("Mileage")};
        DataTable table = new DataTable();
        for (String h : head) {
            table.addColumn(h);
        }
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        for (Row t : db().list("NormalTransaction", where(params) + " ORDER BY {CreateTime} DESC", params)) {
            String cardNo = t.str("CardNo");
            table.addRow(IniInfo.StationName, t.i("NozID"), t.str("GradeName"), Conv.netRound(t.dec("Price"), IniInfo.Pradio),
                Conv.netRound(t.dec("Amount"), IniInfo.Mradio), Conv.netRound(t.dec("Volume"), IniInfo.Vradio), cardNo,
                "".equals(cardNo) ? BigDecimal.ZERO : Conv.netRound(t.dec("CardBalance"), IniInfo.Mradio), Conv.netRound(t.dec("AmountTotal"), IniInfo.Mradio),
                Conv.netRound(t.dec("VolumeTotal"), IniInfo.Vradio), BaseFunction.StampToDateTime(t.str("CreateTime")).format(Conv.STD), t.str("HandShiftNo"),
                t.str("CardName"), t.str("CarNumber"), groupName(t.i("CardGroup")), t.get("RealAmount"), t.get("DiscountValue"), t.i("Mileage"));
        }
        if (fileName.endsWith(".pdf")) {
            String[] error = {""};
            if (!new ExportFile().ExportPDF("TransactionReport", L("Transaction"), java.time.LocalDateTime.now().format(Conv.STD), summary, table, null,
                fileName, error)) {
                throw new IllegalStateException(error[0]);
            }
            DownloadFile(fileName);
            MessageBox(L("ExportSuccess"));
        }
    }

    public void TransactionView_CellDoubleClick() {
        try {
            int id = Conv.toInt(g("TransactionView").Cell(g("TransactionView").CurrentRowIndex(), 0));
            ShowDialog("NormalForm.TransactionShow", r -> {
                if ("OK".equals(r.result())) {
                    if (c("CurPage").Text().isEmpty()) {
                        c("CurPage").Text("1");
                    }
                    try {
                        ShowData(Integer.parseInt(c("CurPage").Text().trim()));
                    } catch (NumberFormatException ignored) {
                        // comme l'original
                    }
                }
            }, "ID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Impression de la transaction sur le boîtier du pistolet (message 1046). */
    public void DeviceTransactionPrint_Click() {
        try {
            int id = Conv.toInt(g("TransactionView").Cell(g("TransactionView").CurrentRowIndex(), 0));
            Row t = db().first("NormalTransaction", "{ID} = :i", p("i", id));
            if (t == null) {
                throw new IllegalStateException("transaction Lost");
            }
            Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", t.get("NozID")));
            if (noz == null) {
                throw new IllegalStateException("Noz Lost");
            }
            NormalFunction.MainFormMessage.accept(1046, id, noz.i("PortID"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
