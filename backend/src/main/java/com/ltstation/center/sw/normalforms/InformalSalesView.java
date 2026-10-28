package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.ArrayList;
import java.util.List;

/** BoxScreenView.InformalSalesView : ventes annulées (BillComplete 4) cochées puis rattachées à la facture en cours (argument BillOrder). */
@SwWindow("BoxScreenView.InformalSalesView")
public class InformalSalesView extends SwForm {

    private String check() {
        return g("OilDataView").columns().get(1).name();
    }

    public void InformalSalesView_Load() {
        try {
            ShowData(db().list("BillOrder", "{BillComplete} = 4", null));
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void ShowData(List<Row> orders) {
        try {
            g("OilDataView").RowsClear();
            for (Row order : orders) {
                g("OilDataView").RowsAdd(order.get("ID"), false, order.get("POSID"), order.get("NozID"), order.str("GradeName"), order.get("Volume"),
                    order.get("Price"), order.get("Amount"), BaseFunction.StampToDateTime(order.str("TransactionTime")).format(Conv.STD));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void OilDataView_CellClick() {
        int i = g("OilDataView").CurrentRowIndex();
        if (i >= 0) {
            g("OilDataView").Cell(i, check(), !Conv.toBool(g("OilDataView").Cell(i, 1)));
        }
    }

    /** Sélection de plusieurs lignes : cochées (une seule ligne sélectionnable dans cette version). */
    public void OilDataView_SelectionChanged() {
    }

    public void SelectAllBtn_Click() {
        for (int i = 0; i < g("OilDataView").RowCount(); i++) {
            g("OilDataView").Cell(i, check(), true);
        }
    }

    public void ConfirmBtn_Click() {
        try {
            String billOrder = argStr("BillOrder");
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < g("OilDataView").RowCount(); i++) {
                if (Conv.toBool(g("OilDataView").Cell(i, 1))) {
                    list.add(Conv.toInt(g("OilDataView").Cell(i, 0)));
                }
            }
            if (!list.isEmpty()) {
                List<Row> list2 = db().list("BillOrder", "{ID} IN (:ids)", p("ids", list));
                List<Integer> list3 = new ArrayList<>();
                for (Row item : list2) {
                    item.put("BillOrderNumber", billOrder);
                    item.put("BillComplete", 0);
                    list3.add(item.i("TransactionID"));
                }
                db().updateAll("BillOrder", list2);
                if (!list3.isEmpty()) {
                    List<Row> list4 = db().list("NormalTransaction", "{ID} IN (:ids)", p("ids", list3));
                    for (Row item : list4) {
                        item.put("SaleCheck", billOrder + "-1");
                    }
                    db().updateAll("NormalTransaction", list4);
                }
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
