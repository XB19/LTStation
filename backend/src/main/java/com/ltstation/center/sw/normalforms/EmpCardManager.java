package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar.Page;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** Card.EmpCardManager : cartes employé locales, 10 par page ; double-clic = modification, bouton = création. */
@SwWindow("Card.EmpCardManager")
public class EmpCardManager extends SwForm {

    public void EmpCardManager_Load() {
        SetPage("1");
    }

    /** CurPage.Text = … : déclenche CurPage_TextChanged si le texte change, comme en WinForms. */
    private void SetPage(String page) {
        c("CurPage").Text(page);
    }

    public void QueryBtn_Click() {
        ShowDialog("Card.EmpCardCreate", r -> {
            if ("OK".equals(r.result())) {
                SetPage("1");
            }
        });
    }

    private void ShowData(List<Row> card) {
        try {
            g("TransactionView").RowsClear();
            for (Row item : card) {
                g("TransactionView").RowsAdd(item.str("Name"), item.str("ASN"), BaseFunction.GetCardStatue(Conv.toInt(item.get("Status"))));
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
                throw new IllegalStateException(L("PageError"));
            }
            Page page = db().page("EmployeeCardInfos", null, null, null, result, 10);
            if (page.totalPage() < result) {
                throw new IllegalStateException(L("LastPage"));
            }
            c("PageLabel").Text(result + "/" + page.totalPage());
            ShowData(page.rows());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void TransactionView_CellDoubleClick() {
        try {
            int index = g("TransactionView").CurrentRowIndex();
            String asn = String.valueOf(g("TransactionView").Cell(index, 1));
            ShowDialog("Card.EmpCardUpdate", r -> {
                if ("OK".equals(r.result())) {
                    SetPage("1");
                }
            }, "Asn", asn);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Cur_Click() {
        try {
            int result;
            try {
                result = Integer.parseInt(c("CurPage").Text().trim());
            } catch (NumberFormatException e) {
                result = 0;
                SetPage("1");
            }
            if (sender.equals("Next")) {
                int totalPage = db().page("EmployeeCardInfos", null, null, null, 1, 10).totalPage();
                if (result != totalPage) {
                    result++;
                }
                SetPage(String.valueOf(result));
            } else if (sender.equals("Cur")) {
                if (result != 1) {
                    result--;
                }
                SetPage(String.valueOf(result));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
