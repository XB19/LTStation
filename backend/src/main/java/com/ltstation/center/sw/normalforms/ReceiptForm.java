package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.FrxReport;
import com.ltstation.center.sw.normal.BoxBase;
import java.util.List;

/** BoxScreenView.ReceiptForm : factures de la caisse et aperçu du ticket (le premier à l'ouverture, puis celui cliqué). */
@SwWindow("BoxScreenView.ReceiptForm")
public class ReceiptForm extends SwForm {

    public void ReceiptForm_Load() {
        try {
            ShowData(db().list("BillOrderSummary"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowData(List<Row> list) {
        try {
            g("OilDataView").RowsClear();
            int num = 0;
            for (Row item : list) {
                if (num == 0) {
                    num = item.i("ID");
                }
                g("OilDataView").RowsAdd(item.get("ID"), item.str("BillOrderNumber"), item.get("RealAmount"), 0);
            }
            if (num == 0) {
                return;
            }
            Row summary = db().first("BillOrderSummary", "{ID} = :i", p("i", num));
            if (summary != null && Preview(summary, num)) {
                c("OrderNumberBox").Text(summary.str("BillOrderNumber"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private boolean Preview(Row summary, int num) {
        FrxReport report = BoxBase.BoxCashPreviewPrint(summary.str("BillOrderNumber"), String.valueOf(num), "1",
            BaseFunction.StampToDateTime(summary.str("CreateTime")).format(Conv.STD), summary.str("OldOrderNumber"),
            summary.dec("RealAmount").toPlainString(), new String[1]);
        if (report == null) {
            return false;
        }
        ShowPrepared(report, "previewControl1");
        return true;
    }

    public void OilDataView_CellContentClick() {
        try {
            int index = g("OilDataView").CurrentRowIndex();
            int num = Integer.parseInt(String.valueOf(g("OilDataView").Cell(index, 0)));
            Row summary = db().first("BillOrderSummary", "{ID} = :i", p("i", num));
            if (summary != null) {
                Preview(summary, num);
                c("OrderNumberBox").Text(summary.str("BillOrderNumber"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CloseBtn_Click() {
        Close("Cancel");
    }
}
