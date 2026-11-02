package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;

/** CardTransformTable : cartes NP à transformer en cartes réelles. */
@SwWindow("CardMake.CardTransformTable")
public class CardTransformTable extends CardMakeForm {

    public void CardTransformTable_Load() {
        GetPageData(1);
    }

    private void GetPageData(int Page) {
        try {
            Row list = sendRow("GetNPCardtransform", Row.of("Index", Page, "QueryName", c("QueryBox").Text()));
            c("TotalCount").Text("Total Record:" + Conv.toStr(list.get("TotalNumber")) + ",Total Page:" + Conv.toStr(list.get("TotalPage")));
            c("TotalPageSecond").Text(Conv.toStr(list.get("TotalPage")));
            g("TransactionView").RowsClear();
            for (Row i : listOf(list, "list")) {
                g("TransactionView").RowsAdd(i.str("Asn"), i.get("CustomerID"), i.str("CustomerName"), i.str("Phone"), i.str("ValidTime"), i.get("Balance"));
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
                throw new IllegalStateException("Page Format Error");
            }
            GetPageData(result);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Next_Click() {
        try {
            int num = Conv.toInt(c("CurPage").Text());
            int num2 = Conv.toInt(c("TotalPageSecond").Text());
            if ("Next".equals(sender)) {
                if (num >= num2) {
                    throw new IllegalStateException("Last Page");
                }
                c("CurPage").Text(String.valueOf(num + 1));
            } else if ("Cur".equals(sender)) {
                if (num <= 1) {
                    throw new IllegalStateException("First Page");
                }
                c("CurPage").Text(String.valueOf(num - 1));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void TransactionView_CellDoubleClick() {
        try {
            int i = g("TransactionView").CurrentRowIndex();
            var v = g("TransactionView");
            ShowDialog("CardMake.RealCardTransform", r -> {
                if (r.ok()) {
                    GetPageData(Conv.toInt(c("CurPage").Text()));
                }
            }, "Asn", String.valueOf(v.Cell(i, 0)), "CustomerID", Conv.toInt(v.Cell(i, 1)), "CustomerName", String.valueOf(v.Cell(i, 2)), "Phone", String.valueOf(v.Cell(i, 3)),
                "ValidTime", String.valueOf(v.Cell(i, 4)), "Balance", String.valueOf(v.Cell(i, 5)));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
