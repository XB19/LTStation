package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.form.SwWindow;
import java.util.ArrayList;
import java.util.List;

/** ReportView : dépôts de la station sur une période (TransactionTypeID = 2), avec export Excel. */
@SwWindow("CardMake.ReportView")
public class ReportView extends CardMakeForm {

    private Row query(int page) {
        return Row.of("StartTime", time("DTStart"), "EndTime", time("EndDateTime"), "StationID", CM.StationID, "PageIndex", page, "TransactionTypeID", 2);
    }

    private static Object[] line(Row i) {
        return new Object[] {i.get("TransactionID"), i.str("CardNumber"), i.str("CardUserName"), i.str("PhoneNumber"), i.str("TransactionDateTime"), i.get("DepositAmount")};
    }

    private void GetData(int PageIndex) {
        try {
            Row r = sendRow("GetLogList", query(PageIndex));
            c("TotalPageSecond").Text(Conv.toStr(r.get("TotalPage")));
            c("AmountTotal").Text(Conv.toStr(r.get("TotalAmount")));
            g("TransactionView").RowsClear();
            for (Row item : listOf(r, "list")) {
                g("TransactionView").RowsAdd(line(item));
            }
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
                result = 1;
            }
            GetData(result);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Next_Click() {
        try {
            int num = Conv.toInt(c("TotalPageSecond").Text());
            if (num == 0) {
                throw new IllegalStateException("Please Query First");
            }
            int num2 = Conv.toInt(c("CurPage").Text());
            if ("Next".equals(sender)) {
                if (num2 == num) {
                    throw new IllegalStateException("it's Last Page");
                }
                c("CurPage").Text(String.valueOf(num2 + 1));
            } else if ("Cur".equals(sender)) {
                if (num2 == 1) {
                    throw new IllegalStateException("it's First Page");
                }
                c("CurPage").Text(String.valueOf(num2 - 1));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void QueryBtn_Click() {
        try {
            if ("1".equals(c("CurPage").Text())) {
                GetData(1);
            } else {
                c("CurPage").Text("1");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ExportBtn_Click() {
        try {
            int num = 0;
            int num2 = 1;
            List<Object[]> list = new ArrayList<>();
            while (num < num2) {
                num++;
                Row r = sendRow("ExtendExportExcel", Row.of("ExportType", 1, "requestData", Wire.json(query(num))));
                num2 = r.i("TotalPage");
                listOf(r, "list").forEach(i -> list.add(line(i)));
            }
            ExportExcel("Report-" + stamp(), "Deposit Report", new String[] {"TransactionID", "CardNumber", "CardUserName", "PhoneNumber", "TransactionDateTime", "DepositAmount"},
                list, null);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
