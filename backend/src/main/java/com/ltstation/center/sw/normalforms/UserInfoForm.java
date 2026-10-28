package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar.Page;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.Holder;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** Card.UserInfoForm : cartes clients (nom, téléphone, carte, solde, points), 20 par page. */
@SwWindow("Card.UserInfoForm")
public class UserInfoForm extends SwForm {

    public void UserInfoForm_Load() {
        ShowData(1);
    }

    private void ShowData(int pageIndex) {
        try {
            Page page = db().page("CustomerCardInfos", null, null, null, pageIndex, 20);
            g("UserCardDataView").RowsClear();
            for (Row item : page.rows()) {
                Holder h = BaseFunction.GetCustomerMess(item.str("ASN"));
                if (h.ok()) {
                    g("UserCardDataView").RowsAdd(h.a().str("Name"), h.a().str("Telephone"), item.str("ASN"),
                        Conv.netRound(h.c().dec("Balance"), 3), item.get("Score"));
                }
            }
            c("UserCardPageLabel").Text(pageIndex + "/" + page.totalPage());
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void UserCardPreBtn_Click() {
        try {
            String[] array = c("UserCardPageLabel").Text().split("/");
            int num = Conv.toInt(array[0]);
            int num2 = Conv.toInt(array[1]);
            if (sender.equals("UserCardPreBtn")) {
                if (num == 0 || num == 1) {
                    throw new IllegalStateException(L("FirstPage"));
                }
                ShowData(num - 1);
            } else if (sender.equals("UserCardNextBtn")) {
                if (num == num2) {
                    throw new IllegalStateException(L("LastPage"));
                }
                ShowData(num + 1);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
