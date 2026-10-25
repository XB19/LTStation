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
import com.ltstation.center.sw.hw.CardWrite;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** CardLog : historique d'une carte sur Center (50 lignes par page), totaux et export Excel. */
@SwWindow("CardLog")
public class CardLog extends SwForm {

    private String Account = "";
    private String UserName = "";
    private String CarNo = "";

    @Override
    protected void init() {
        On("CertificateNo", "TextChanged", "SkinTxt_TextChanged");
    }

    /** Appelée par PWDInput (BtnName « CardNoSearch ») ou directement sans code PIN. */
    public boolean Card_Reload(String Pwd, String btnName) {
        try {
            CardWrite.UserPublicData[] user = {null};
            boolean num = CardWrite.ReadCardOnLoad(Pwd, user, new BigDecimal[1]);
            if (!num) {
                throw new IllegalStateException(L("ReadCardFailed"));
            }
            c("CardNo").Text(user[0].ASN);
            return num;
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return false;
        }
    }

    public void CardNoSearch_Click() {
        String name = sender;
        try {
            String[] res = {""};
            if (CardWrite.ReadPinID(res)) {
                if (res[0].equals("01")) {
                    ShowDialog("PWDInput", r -> { }, "BtnName", name);
                } else {
                    Card_Reload("", name);
                }
                return;
            }
            throw new IllegalStateException(L("CardReaderDisconnect"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static String fmt(BigDecimal d, String format) {
        return new DecimalFormat(format, DecimalFormatSymbols.getInstance(Locale.ROOT)).format(d);
    }

    private void ShowData(List<Row> list) {
        try {
            g("CardLogView").RowsClear();
            for (Row item : list) {
                String value = "";
                String value2 = "";
                String value3 = "";
                String value4 = "";
                int type = item.i("TransactionTypeID");
                if (type == 2 || type == 12 || type == 1) {
                    value = fmt(Conv.round(Conv.toDecimal(item.get("Amount")), IniInfo.Mradio), IniInfo.MoneyFormat);
                    value2 = fmt(Conv.round(Conv.toDecimal(item.get("FeeAmount")).add(Conv.toDecimal(item.get("DiscountAmount"))), IniInfo.Mradio),
                        IniInfo.MoneyFormat);
                } else {
                    value3 = fmt(Conv.round(Conv.toDecimal(item.get("Amount")), IniInfo.Mradio), IniInfo.MoneyFormat);
                    value4 = fmt(Conv.round(Conv.toDecimal(item.get("FeeAmount")), IniInfo.Vradio), IniInfo.VolumeFormat);
                }
                g("CardLogView").RowsAdd(item.get("ASN"), item.get("ProcessCode"), BaseFunction.GetLogType(type), value, value2, value3, value4,
                    Conv.toDateTime(item.get("TransDatetime")).format(Conv.STD), Conv.round(Conv.toDecimal(item.get("Balance")), IniInfo.Mradio));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void SetButtonEnable(int page, int totalPage) {
        c("Cur").Enabled(page != 1);
        c("CurEnd").Enabled(page != 1);
        c("Next").Enabled(totalPage != page);
        c("NextEnd").Enabled(totalPage != page);
    }

    /** Date + heure courte (ToShortTimeString) comme l'original. */
    private String dt(String date, String time) {
        LocalDateTime d = c(date).Value();
        LocalDateTime t = c(time).Value();
        return d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " " + t.format(DateTimeFormatter.ofPattern("H:mm"));
    }

    private String mess() {
        return Account + "|" + dt("DTStartDate", "DTStartTime") + "|" + dt("DTEndDate", "DTEndTime") + "|" + CarNo;
    }

    public void LogSearch_Click() {
        try {
            Account = c("CardNo").Text();
            UserName = "";
            CarNo = c("CardNoRequest").Text();
            if (Account.isEmpty() && CarNo.isEmpty() && c("CertificateNo").Text().isEmpty()) {
                throw new IllegalStateException(L("PlsQueryTerms"));
            }
            GetLog(1);
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", mess());
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCardLogTotal", Wire.json(obj));
            if (!r.Result()) {
                return;
            }
            List<Row> list = Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()));
            if (list.isEmpty()) {
                return;
            }
            double num = 0.0;
            double num2 = 0.0;
            double num3 = 0.0;
            double num4 = 0.0;
            double num5 = 0.0;
            for (Row item : list) {
                int type = item.i("TransactionTypeID");
                if (type == 2 || type == 1) {
                    num += Conv.toDouble(item.get("Amount"));
                    num2 += Conv.toDouble(item.get("FeeAmount")) + Conv.toDouble(item.get("DiscountAmount"));
                }
                if (type == 12) {
                    num5 += Conv.toDouble(item.get("Amount"));
                }
                if (type == 11) {
                    num3 += Conv.toDouble(item.get("Amount"));
                    num4 += Conv.toDouble(item.get("FeeAmount"));
                }
            }
            g("LogTotalView").RowsClear();
            g("LogTotalView").RowsAdd(list.get(0).get("AName"), num, num5, num2, num3, num4);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void GetLog(int Page) {
        try {
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", Page, "Mess", mess());
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCardLog", Wire.json(obj));
            if (r.Result()) {
                int pages = r.TotalCount() / 50 + 1;
                c("MLabel").Text("共" + r.TotalCount() + "条记录，每页50条记录，共" + pages + "页");
                c("TotalPageSecond").Text(String.valueOf(pages));
                c("CurPage").Text(String.valueOf(Page));
                SetButtonEnable(Page, pages);
                ShowData(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())));
                return;
            }
            throw new IllegalStateException(L("CommunicationFailed"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
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
                throw new IllegalStateException(L("UnvalidatedInput"));
            }
            GetLog(num);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** ExportExcel : l'original l'exécute dans un thread derrière ProBar puis ouvre le fichier. */
    public void Export_Click() {
        String[] out = {""};
        new ExportFile().DataTableToExcel("Log", GetDataTable(), out);
        MessageBox(L("ExportSuccess"));
        DownloadFile(out[0]);
    }

    private DataTable GetDataTable() {
        DataTable dt = new DataTable();
        try {
            for (String col : new String[] {"Asn", "UserName", "TransactionID", "SaveAmount", "DisCount", "Amount", "Volume", "TrueTime", "Balance"}) {
                dt.addColumn(col);
            }
            dt.addRow(L("CardNo"), L("UserName"), L("TransactionType"), L("Recharge"), L("GiftAmount"), L("ConsumptionAmount"), L("Volume"), L("Time"),
                L("Balance"));
            int num = 1;
            while (true) {
                Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", num, "Mess", mess());
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCardLog", Wire.json(obj));
                if (!r.Result()) {
                    break;
                }
                for (Row item : Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()))) {
                    String value = "";
                    Row info = db().first("CustomerCardInfos", "{ASN} = :a", p("a", item.str("ASN")));
                    if (info != null) {
                        value = db().first("Customers", "{CustomerID} = :c", p("c", info.get("CustomerID"))).str("Name");
                    }
                    int type = item.i("TransactionTypeID");
                    boolean save = type == 2 || type == 12;
                    boolean fuel = type == 11;
                    dt.addRow(item.str("ASN"), value, BaseFunction.GetLogType(type),
                        save ? Conv.round(Conv.toDecimal(item.get("Amount")), IniInfo.Mradio).toPlainString() : "0",
                        save ? Conv.round(Conv.toDecimal(item.get("FeeAmount")).add(Conv.toDecimal(item.get("DiscountAmount"))), IniInfo.Mradio).toPlainString()
                            : "0",
                        fuel ? Conv.round(Conv.toDecimal(item.get("Amount")), IniInfo.Mradio).toPlainString() : "0",
                        fuel ? Conv.round(Conv.toDecimal(item.get("FeeAmount")), IniInfo.Mradio).toPlainString() : "0",
                        Conv.toDateTime(item.get("TransDatetime")).format(Conv.STD),
                        Conv.round(Conv.toDecimal(item.get("Balance")), IniInfo.Mradio).toPlainString());
                }
                if (num * 50 > r.TotalCount()) {
                    break;
                }
                num++;
            }
            return dt;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void CardLog_Load() {
        c("DTStartDate").Value(LocalDateTime.now().minusDays(1));
        c("DTEndDate").Value(LocalDateTime.now().plusDays(1));
    }

    public void SkinTxt_TextChanged() {
        String CNo = c("CertificateNo").Text();
        Row customers = db().first("Customers", "{CertificateNo} = :n", p("n", CNo));
        if (customers != null) {
            Row info = db().first("CustomerCardInfos", "{CustomerID} = :c ORDER BY {ASN} DESC", p("c", customers.get("CustomerID")));
            if (info != null) {
                c("CardNo").Text(info.get("ASN"));
            }
        }
    }
}
