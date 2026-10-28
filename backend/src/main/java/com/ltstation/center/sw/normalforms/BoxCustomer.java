package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar.Page;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** BoxScreenView.BoxCustomer : cartes clients, clients à crédit et groupes, 20 par page chacun. */
@SwWindow("BoxScreenView.BoxCustomer")
public class BoxCustomer extends SwForm {

    public void BoxCustomer_Load() {
        ShowCustomerData(1);
        ShowPersonCreditData(1);
        ShowGroupData(1);
    }

    private void ShowCustomerData(int PageIndex) {
        g("UserCardDataView").RowsClear();
        try {
            Page page = db().page("CustomerCardInfos", null, null, null, PageIndex, 20);
            for (Row item : page.rows()) {
                Row customer = db().first("Customers", "{CustomerID} = :c", p("c", item.get("CustomerID")));
                g("UserCardDataView").RowsAdd(customer != null ? customer.str("Name") : "", BaseFunction.GetNormalCardType(item.str("ASN")), item.str("ASN"));
            }
            c("UserCardPageLabel").Text(PageIndex + "/" + page.totalPage());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowPersonCreditData(int PageIndex) {
        try {
            Page page = db().page("PersonalCredit", null, null, null, PageIndex, 20);
            g("PersonCreditDataView").RowsClear();
            for (Row item : page.rows()) {
                g("PersonCreditDataView").RowsAdd(item.str("AccountName"));
            }
            c("PersonCreditPageLabel").Text(PageIndex + "/" + page.totalPage());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowGroupData(int PageIndex) {
        try {
            Page page = db().page("Group", "{GroupID} <> 100000001", null, null, PageIndex, 20);
            g("GroupDataView").RowsClear();
            for (Row item : page.rows()) {
                g("GroupDataView").RowsAdd(item.str("Name"));
            }
            c("GroupPage").Text(PageIndex + "/" + page.totalPage());
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** Pages des trois listes ; les boutons « crédit » lisent la page des groupes, comme dans l'original. */
    public void UserCardPreBtn_Click() {
        try {
            String[] array = c("UserCardPageLabel").Text().split("/");
            int num = Conv.toInt(array[0]);
            int num2 = Conv.toInt(array[1]);
            String[] array2 = c("GroupPage").Text().split("/");
            int num3 = Conv.toInt(array2[0]);
            int num4 = Conv.toInt(array2[1]);
            int num5 = num3;
            switch (sender) {
                case "UserCardPreBtn" -> {
                    if (num == 0 || num == 1) {
                        throw new IllegalStateException(L("FirstPage"));
                    }
                    ShowCustomerData(num - 1);
                }
                case "UserCardNextBtn" -> {
                    if (num == num2) {
                        throw new IllegalStateException(L("LastPage"));
                    }
                    ShowCustomerData(num + 1);
                }
                case "GroupPreBtn" -> {
                    if (num3 == 0 || num3 == 1) {
                        throw new IllegalStateException(L("FirstPage"));
                    }
                    ShowGroupData(num3 - 1);
                }
                case "GroupNextBtn" -> {
                    if (num3 == num4) {
                        throw new IllegalStateException(L("LastPage"));
                    }
                    ShowGroupData(num3 + 1);
                }
                case "PersonCreditPreBtn" -> {
                    if (num5 == 0 || num5 == 1) {
                        throw new IllegalStateException(L("FirstPage"));
                    }
                    ShowGroupData(num5 - 1);
                }
                case "PersonCreditNextBtn" -> {
                    if (num3 == num4) {
                        throw new IllegalStateException(L("LastPage"));
                    }
                    ShowPersonCreditData(num5 + 1);
                }
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
