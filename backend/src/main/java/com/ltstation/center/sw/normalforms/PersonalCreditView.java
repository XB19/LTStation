package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar.Page;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/** BoxScreenView.PersonalCreditView : choix d'un client à crédit (20 par page, recherche par nom) ; renvoie ValueID. */
@SwWindow("BoxScreenView.PersonalCreditView")
public class PersonalCreditView extends SwForm {

    public void PersonalCreditView_Load() {
        ShowData(1);
    }

    private void ShowData(int PageIndex) {
        String search = c("SearchBox").Text().equals(L("Query")) ? "" : c("SearchBox").Text();
        try {
            Page page = search.isEmpty()
                ? db().page("PersonalCredit", null, null, null, PageIndex, 20)
                : db().page("PersonalCredit", "{AccountName} LIKE :s", p("s", "%" + search + "%"), null, PageIndex, 20);
            g("PersonCreditDataView").RowsClear();
            for (Row item : page.rows()) {
                g("PersonCreditDataView").RowsAdd(item.get("ID"), item.str("AccountName"));
            }
            c("PageLabel").Text(PageIndex + "/" + page.totalPage());
            if (!page.rows().isEmpty()) {
                ShowCustomer(page.rows().get(0).i("ID"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void PreBtn_Click() {
        try {
            String[] array = c("PageLabel").Text().split("/");
            int num = Conv.toInt(array[0]);
            int num2 = Conv.toInt(array[1]);
            if (sender.equals("PreBtn")) {
                if (num == 0 || num == 1) {
                    throw new IllegalStateException(L("FirstPage"));
                }
                ShowData(num - 1);
            } else if (sender.equals("NextBtn")) {
                if (num == num2) {
                    throw new IllegalStateException(L("LastPage"));
                }
                ShowData(num + 1);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void PersonCreditDataView_CellClick() {
        try {
            int index = g("PersonCreditDataView").CurrentRowIndex();
            ShowCustomer(Integer.parseInt(String.valueOf(g("PersonCreditDataView").Cell(index, 0))));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowCustomer(int id) {
        try {
            Row pd = db().first("PersonalCredit", "{ID} = :i", p("i", id));
            if (pd == null) {
                return;
            }
            BigDecimal num = db().list("PersonCreditLog", "{PersonCreditID} = :i AND {CreateTime} > :u", p("i", id, "u", pd.get("UpdateTime")))
                .stream().map(t -> t.dec("Amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
            c("CreditNameBox").Text(pd.str("AccountName"));
            c("CreditPasswordBox").Text(pd.str("AccountPassword"));
            c("CreditGroupBox").Text(pd.str("CustomerGroup"));
            c("CreditAddressBox").Text(pd.str("FirstAddress"));
            c("CreditAddressSecondBox").Text(pd.str("SecondAddress"));
            c("CreditAddressThirdBox").Text(pd.str("ThirdAddress"));
            c("CreditPhoneBox").Text(pd.str("Phone"));
            c("CreditEmailBox").Text(pd.str("Email"));
            c("CreditFaxBox").Text(pd.str("FaxNumber"));
            c("CreditTaxBox").Text(pd.str("TaxIDNumber"));
            c("CreditCarNoBox").Text(pd.str("CarLicense"));
            c("CreditCardNoBox").Text(pd.str("CardNumber"));
            c("CreditLimitBox").Text(pd.dec("CreditLimit").toPlainString());
            c("CreditUseBox").Text(pd.dec("UseAmount").add(num).toPlainString());
            c("PersonBillDateBox").Text(pd.str("BillDate"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SearchBox_Click() {
        if (c("SearchBox").Text().equals(L("Query"))) {
            c("SearchBox").Text("");
        }
    }

    public void SearchBox_TextChanged() {
        ShowData(1);
    }

    public void ConfirmBtn_Click() {
        try {
            int index = g("PersonCreditDataView").CurrentRowIndex();
            if (index == -1) {
                throw new IllegalStateException(L("UserInexistence"));
            }
            result.put("ValueID", Integer.parseInt(String.valueOf(g("PersonCreditDataView").Cell(index, 0))));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
