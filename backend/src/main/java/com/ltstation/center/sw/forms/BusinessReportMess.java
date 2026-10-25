package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/** BusinessReportMess : opérations sur les comptes des cartes (Center), 50 par page, et export Excel. */
@SwWindow("BusinessReportMess")
public class BusinessReportMess extends SwForm {

    /** Date + heure au format de l'original (« 2026-10-5 7:3:0 », sans zéros). */
    private String dt(String date, String time) {
        LocalDateTime d = c(date).Value();
        LocalDateTime t = c(time).Value();
        return d.getYear() + "-" + d.getMonthValue() + "-" + d.getDayOfMonth() + " " + t.getHour() + ":" + t.getMinute() + ":" + t.getSecond();
    }

    private Row request(String dts, String dte) {
        Conv.toDateTime(dts);
        Conv.toDateTime(dte);
        return SW.dto().make("BussinessMess", "Dts", dts, "DtE", dte, "Code", IniInfo.Code, "PageIndex", 0, "SelectIndex",
            c("CardTypeSelect").SelectedIndex(), "OSelectIndex", c("OperateType").SelectedIndex(), "CompanyID", IniInfo.CompanyID);
    }

    private String countText(Row list) {
        if ("zh-CN".equals(IniInfo.Language)) {
            return "共" + list.str("totalNumber") + "条记录，每页50条记录，共" + list.str("totalPage") + "页";
        }
        return "Total Record:" + list.str("totalNumber") + "，50 records per page，Total Page:" + list.str("totalPage");
    }

    public void SearchButton_Click() {
        try {
            Row obj = request(dt("DTStartDate", "DTStartTime"), dt("DTEndDate", "DTEndTime"));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/BussinessGetMess", Wire.json(obj));
            if (r.Result()) {
                Row list = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                ShowLog(list.rows("list"));
                c("MLabel").Text(countText(list));
                c("TotalPageSecond").Text(list.str("totalPage"));
                c("CurPage").Text("1");
                g("BussinessTotalView").RowsClear();
                g("BussinessTotalView").RowsAdd(list.get("totalNumber"), list.get("totalSave"), list.get("totalDisAmount"), list.get("totalUnSave"),
                    list.get("totalNotFuelAmount"), list.get("totalAmount"), list.get("totalVolume"));
            }
            c("Export").Enabled(true);
            CurNextChange(Conv.toInt(c("CurPage").Text()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static BigDecimal r(Object v) {
        BigDecimal d = Conv.toDecimal(v);
        return d.scale() > IniInfo.Mradio ? d.setScale(IniInfo.Mradio, RoundingMode.HALF_EVEN) : d;
    }

    private void ShowLog(List<Row> list) {
        g("BusinessLogView").RowsClear();
        for (Row item : list) {
            BigDecimal num = BigDecimal.ZERO;
            BigDecimal num2 = BigDecimal.ZERO;
            BigDecimal num3 = BigDecimal.ZERO;
            BigDecimal num4 = BigDecimal.ZERO;
            switch (item.i("TransactionTypeID")) {
                case 2, 12, 16 -> {
                    num = r(item.get("Amount"));
                    num2 = r(Conv.toDecimal(item.get("FeeAmount")).add(Conv.toDecimal(item.get("DiscountAmount"))));
                }
                case 10, 11, 13 -> {
                    num3 = r(item.get("FeeAmount"));
                    num4 = r(item.get("Amount"));
                }
                default -> {
                }
            }
            g("BusinessLogView").RowsAdd(item.get("ASN"), BaseFunction.GetNameByAsn(item.str("ASN")), BaseFunction.GetLogType(item.i("TransactionTypeID")),
                num, num2, num4, num3, Conv.toDateTime(item.get("TransDatetime")).format(Conv.STD), item.get("Balance"));
        }
    }

    /** Changement de page : l'original relit toujours la première page (PageIndex 0). */
    public void CurPage_TextChanged() {
        try {
            int num = Conv.toInt(c("CurPage").Text());
            int num2 = Conv.toInt(c("TotalPageSecond").Text());
            if (num < 0 || num > num2) {
                throw new IllegalStateException(L("PageError"));
            }
            Row obj = request(dt("DTStartDate", "DTStartTime"), dt("DTEndDate", "DTEndTime"));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/BussinessGetMess", Wire.json(obj));
            if (!r.Result()) {
                return;
            }
            Row list = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            ShowLog(list.rows("list"));
            c("MLabel").Text(countText(list));
            c("TotalPageSecond").Text(list.str("totalPage"));
            c("CurPage").Text(String.valueOf(num));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Next_Click() {
        switch (sender) {
            case "Next" -> c("CurPage").Text(String.valueOf(Conv.toInt(c("CurPage").Text()) + 1));
            case "Cur" -> c("CurPage").Text(String.valueOf(Conv.toInt(c("CurPage").Text()) - 1));
            case "CurEnd" -> c("CurPage").Text("1");
            case "NextEnd" -> c("CurPage").Text(String.valueOf(Conv.toInt(c("TotalPageSecond").Text())));
            default -> {
            }
        }
        CurNextChange(Conv.toInt(c("CurPage").Text()));
    }

    private void CurNextChange(int pageIndex) {
        int num = Conv.toInt(c("TotalPageSecond").Text());
        boolean first = pageIndex == 1;
        c("Cur").Enabled(!first);
        c("CurEnd").Enabled(!first);
        boolean last = pageIndex == num;
        c("Next").Enabled(!last);
        c("NextEnd").Enabled(!last);
        if (!first && pageIndex < num) {
            for (String b : new String[] {"Cur", "CurEnd", "Next", "NextEnd"}) {
                c(b).Enabled(true);
            }
        }
    }

    public void Export_Click() {
        LocalDateTime dts = Conv.toDateTime(dt("DTStartDate", "DTStartTime"));
        LocalDateTime dte = Conv.toDateTime(dt("DTEndDate", "DTEndTime"));
        String[] out = {""};
        new ExportFile().DataTableToExcel("Buss", GetDataTable(dts, dte), out);
        DownloadFile(out[0]);
    }

    private DataTable GetDataTable(LocalDateTime dts, LocalDateTime dte) {
        DataTable dt = new DataTable();
        try {
            for (String col : new String[] {"Asn", "UserName", "TransactionID", "SaveAmount", "DisCount", "Amount", "Volume", "TrueTime", "Balance"}) {
                dt.addColumn(col);
            }
            dt.addRow(L("CardNo"), L("UserName"), L("TransactionType"), L("Recharge"), L("GiftAmount"), L("ConsumptionAmount"), L("Volume"), L("Time"),
                L("Balance"));
            int num = 1;
            Row req = request(dts.format(Conv.STD), dte.format(Conv.STD));
            Row list = new Row();
            while (true) {
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/BussinessGetMess", Wire.json(req));
                if (!r.Result()) {
                    break;
                }
                list = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                for (Row item : list.rows("list")) {
                    int type = item.i("TransactionTypeID");
                    boolean save = type == 2 || type == 12;
                    boolean fuel = type == 11;
                    dt.addRow(item.str("ASN"), BaseFunction.GetNameByAsn(item.str("ASN")), BaseFunction.GetLogType(type),
                        save ? item.str("Amount") : "0",
                        save ? Conv.toDecimal(item.get("DiscountAmount")).add(Conv.toDecimal(item.get("FeeAmount"))).toPlainString() : "0",
                        fuel ? item.str("Amount") : "0", fuel ? item.str("FeeAmount") : "0",
                        Conv.toDateTime(item.get("TransDatetime")).format(Conv.STD), item.str("Balance"));
                }
                if (num == list.i("totalPage") || list.i("totalPage") == 0) {
                    break;
                }
                req.put("PageIndex", req.i("PageIndex") + 1);
                num++;
            }
            dt.addRow(L("Total"), L("TotalRecord"), null, L("TotalRecharge"), L("SummaryDiscount"), L("TotalConsumptionAmount"), L("TotalSumVolume"));
            dt.addRow(null, list.get("totalNumber"), null, list.get("totalSave"), list.get("totalDisAmount"), list.get("totalAmount"),
                list.get("totalVolume"));
            return dt;
        } catch (RuntimeException e) {
            return dt;
        }
    }

    public void BusinessReportMess_Load() {
        c("DTStartDate").Value(LocalDateTime.now().minusDays(1));
        c("DTEndDate").Value(LocalDateTime.now().plusDays(1));
        if (IniInfo.NormalType == 2) {
            g("BusinessLogView").HeaderText("dataGridViewTextBoxColumn6", L("KG"));
            g("BussinessTotalView").HeaderText("dataGridViewTextBoxColumn14", L("TotalKG"));
        }
    }
}
