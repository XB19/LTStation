package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar.Page;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BoxScreenView.OtherProductView : produits de boutique (20 par page, groupe et recherche) ; double-clic = vente sur la facture
 * en cours (argument BillOrderNumber, « BillRun » = vente directe), menu = modification ou vente.
 */
@SwWindow("BoxScreenView.OtherProductView")
public class OtherProductView extends SwForm {

    private static String[] GroupName() {
        return new String[] {L("Food"), L("Lubricants"), L("EngineOil"), L("Cleaner"), L("Tools")};
    }

    private String bill() {
        return arg("BillOrderNumber") == null ? "" : argStr("BillOrderNumber");
    }

    public void AddBtn_Click() {
        ShowDialog("BoxScreenView.ProductEdit", r -> {
            if ("OK".equals(r.result())) {
                ShowData(0);
            }
        }, "ID", 0);
    }

    private void ShowData(int pageNumber) {
        try {
            int index = c("GroupSelect").SelectedIndex();
            String search = c("SearchBox").Text();
            StringBuilder where = new StringBuilder("1 = 1");
            Map<String, Object> params = new LinkedHashMap<>();
            if (index != 0) {
                where.append(" AND {ProductGroup} = :g");
                params.put("g", String.valueOf(index - 1));
            }
            if (!search.isEmpty()) {
                where.append(" AND {Name} LIKE :s");
                params.put("s", "%" + search + "%");
            }
            // ToPageList(0, …) de SqlSugar lit la première page
            Page page = db().page("ProductList", where.toString(), params, "{ID}", Math.max(pageNumber, 1), 20);
            g("OilDataView").RowsClear();
            String[] groups = GroupName();
            for (Row item : page.rows()) {
                g("OilDataView").RowsAdd(item.get("ID"), item.str("ProductID"), item.str("Name"), item.get("Price"), item.get("Quantity"),
                    groups[Conv.toInt(item.get("ProductGroup"))]);
            }
            c("PageLabel").Text(pageNumber + "/" + page.totalPage());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private int currentId() {
        int index = g("OilDataView").CurrentRowIndex();
        return Integer.parseInt(String.valueOf(g("OilDataView").Cell(index, 0)));
    }

    public void OilDataView_CellDoubleClick() {
        try {
            int num = currentId();
            if ("BillRun".equals(bill())) {
                ShowDialog("BoxScreenViewV2.ProductNormalSalerView", r -> {
                    if ("OK".equals(r.result())) {
                        ShowData(1);
                    }
                }, "ID", num);
            } else {
                ShowDialog("BoxScreenView.ProductSalerView", r -> {
                    if ("OK".equals(r.result())) {
                        Close("OK");
                    }
                }, "ID", num, "BillOrderNumber", bill());
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void OtherProductView_Load() {
        try {
            c("GroupSelect").SelectedIndex(0);
            ShowData(1);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ProductUpdateMenu_Click() {
        try {
            int id = currentId();
            if (sender.equals("ProductUpdateMenu")) {
                ShowDialog("BoxScreenView.ProductEdit", r -> {
                    if ("OK".equals(r.result())) {
                        ShowData(1);
                    }
                }, "ID", id);
            } else if (sender.equals("ProductSaleMenu")) {
                ShowDialog("BoxScreenView.ProductSalerView", r -> {
                    if ("OK".equals(r.result())) {
                        ShowData(1);
                    }
                }, "ID", id, "BillOrderNumber", bill());
            }
        } catch (RuntimeException ignored) {
            // comme l'original
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

    public void SearchBtn_Click() {
        ShowData(1);
    }
}
