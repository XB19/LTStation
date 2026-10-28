package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.ArrayList;
import java.util.List;

/**
 * BoxScreenView.BoxNozTransction : transactions non facturées des pistolets d'une pompe (argument NozID = pompe),
 * cochées puis ajoutées à la facture en cours (argument BillOrder) ; l'en-tête de la case coche ou décoche tout.
 */
@SwWindow("BoxScreenView.BoxNozTransction")
public class BoxNozTransction extends SwForm {

    private static final String CHECK = "CheckBoxColumn";

    public void BoxNozTransction_Load() {
        try {
            g("OilDataView").ColumnsAdd(CHECK, "", 60, true);
            On("OilDataView", "CellMouseUp", "OilDataView_CellMouseUp");
            List<Object> inValues = new ArrayList<>();
            for (Row noz : db().list("NormalNoz", "{PumpID} = :p", p("p", argInt("NozID")))) {
                inValues.add(noz.get("NozID"));
            }
            List<Row> list = inValues.isEmpty() ? List.of() : db().list("NormalTransaction",
                "{NozID} IN (:n) AND ({SaleCheck} = '' OR {SaleCheck} IS NULL) AND {Amount} <> 0 AND {Volume} <> 0", p("n", inValues));
            LoadData(list);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Clic sur l'en-tête de la colonne 5 : tout cocher, ou tout décocher si tout est déjà coché. */
    public void OilDataView_CellMouseUp() {
        if (e.i("RowIndex") != -1 || e.i("ColumnIndex") != 5) {
            return;
        }
        boolean flag = !IsAllChecked();
        for (int i = 0; i < g("OilDataView").RowCount(); i++) {
            g("OilDataView").Cell(i, CHECK, flag);
        }
    }

    private boolean IsAllChecked() {
        if (g("OilDataView").RowCount() == 0) {
            return false;
        }
        for (int i = 0; i < g("OilDataView").RowCount(); i++) {
            if (!Boolean.TRUE.equals(g("OilDataView").Cell(i, CHECK))) {
                return false;
            }
        }
        return true;
    }

    private void LoadData(List<Row> list) {
        try {
            g("OilDataView").RowsClear();
            for (Row item : list) {
                g("OilDataView").RowsAdd(item.get("ID"), item.get("NozID"), item.get("Volume"), item.get("Amount"),
                    BaseFunction.StampToDateTime(item.str("CreateTime")));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void OilDataView_CellContentClick() {
        int i = g("OilDataView").CurrentRowIndex();
        if (i >= 0) {
            g("OilDataView").Cell(i, CHECK, !Conv.toBool(g("OilDataView").Cell(i, CHECK)));
        }
    }

    public void OpenAllPumpBtn_Click() {
        try {
            String billOrder = argStr("BillOrder");
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < g("OilDataView").RowCount(); i++) {
                if (Conv.toBool(g("OilDataView").Cell(i, CHECK))) {
                    list.add(Conv.toInt(g("OilDataView").Cell(i, 0)));
                }
            }
            List<Row> list2 = list.isEmpty() ? List.of() : db().list("NormalTransaction", "{ID} IN (:ids)", p("ids", list));
            List<Row> list3 = new ArrayList<>();
            for (Row item : list2) {
                // GetNormalTankNo reçoit le numéro de pistolet, comme dans l'original
                list3.add(Row.of("NozID", item.get("NozID"), "Amount", item.get("Amount"), "Volume", item.get("Volume"), "TransactionID", item.get("ID"),
                    "TransactionTime", item.get("CreateTime"), "BillOrderNumber", billOrder, "BillComplete", 0, "BillPayMent", -1,
                    "GradeName", item.get("GradeName"), "Price", item.get("Price"), "POSID", argInt("NozID"),
                    "TankNo", BaseFunction.GetNormalTankNo(item.i("NozID")), "GradeID", item.get("GradeID")));
                item.put("SaleCheck", billOrder + "-1");
            }
            db().updateAll("NormalTransaction", list2);
            db().insertAll("BillOrder", list3);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Sélection de plusieurs lignes : une seule ligne sélectionnable dans cette version. */
    public void OilDataView_SelectionChanged() {
    }

    public void QuitBtn_Click() {
        Close("Cancel");
    }
}
