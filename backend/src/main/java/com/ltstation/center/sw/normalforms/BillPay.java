package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar.Page;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.ArrayList;
import java.util.List;

/** BoxScreenViewV2.BillPay : clients à crédit (locaux) et leurs relevés (Center) ; double-clic sur un relevé impayé = règlement. */
@SwWindow("BoxScreenViewV2.BillPay")
public class BillPay extends SwForm {

    private List<Row> list = new ArrayList<>();

    public void BillPay_Load() {
        ShowPersonalData(1);
    }

    private void ShowPersonalData(int PageIndex) {
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
                GetPayBill(page.rows().get(0).i("ID"), 1);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowBillData(List<Row> bills) {
        try {
            g("BillDataView").RowsClear();
            for (Row item : bills) {
                g("BillDataView").RowsAdd(item.get("ID"), item.str("AccountName"), item.get("BillDate"), item.get("SumAmount"),
                    BaseFunction.StampToDateTime(item.str("CreateTime")), item.i("Pay") == 0 ? L("Unpaid") : L("Paid"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void GetPayBill(int PersonCreditID, int PageIndex) {
        try {
            CustomerRespone r = BaseFunction.post("Station/GetPersonCreditBill",
                SW.dto().make("PersonalCreditBillRequest", "PersonalCreditID", PersonCreditID, "PageIndex", PageIndex));
            if (r.Result()) {
                list = Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()));
                ShowBillData(list);
                c("BillPage").Text(PageIndex + "/" + r.TotalCount());
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private int currentCredit() {
        int index = g("PersonCreditDataView").CurrentRowIndex();
        return Integer.parseInt(String.valueOf(g("PersonCreditDataView").Cell(index, 0)));
    }

    public void PersonCreditDataView_CellClick() {
        try {
            GetPayBill(currentCredit(), 1);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Pages des clients et des relevés ; les deux lisent PageLabel, comme dans l'original. */
    public void BillPreBtn_Click() {
        try {
            String[] array = c("PageLabel").Text().split("/");
            int num = Conv.toInt(array[0]);
            int num2 = Conv.toInt(array[1]);
            int num3 = num;
            int num4 = num2;
            switch (sender) {
                case "PreBtn" -> {
                    if (num == 0 || num == 1) {
                        throw new IllegalStateException(L("FirstPage"));
                    }
                    ShowPersonalData(num - 1);
                }
                case "NextBtn" -> {
                    if (num == num2) {
                        throw new IllegalStateException(L("LastPage"));
                    }
                    ShowPersonalData(num + 1);
                }
                case "BillPreBtn" -> {
                    if (num3 == 0 || num3 == 1) {
                        throw new IllegalStateException(L("FirstPage"));
                    }
                    GetPayBill(currentCredit(), num3 - 1);
                }
                case "BillNextBtn" -> {
                    if (num3 == num4) {
                        throw new IllegalStateException(L("LastPage"));
                    }
                    GetPayBill(currentCredit(), num3 + 1);
                }
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void BillDataView_CellDoubleClick() {
        try {
            int index = g("BillDataView").CurrentRowIndex();
            int id = Integer.parseInt(String.valueOf(g("BillDataView").Cell(index, 0)));
            Row bill = list.stream().filter(t -> t.i("ID") == id).findFirst().orElse(null);
            if (bill != null) {
                if (bill.i("Pay") != 0) {
                    throw new IllegalStateException("Bill Already Paid");
                }
                ShowDialog("BoxScreenViewV2.BillDetial", r -> {
                    if ("OK".equals(r.result())) {
                        GetPayBill(bill.i("PersonalCreditID"), 1);
                    }
                }, "Bill", bill);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
