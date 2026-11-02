package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;

/** EmpForm : cartes employé d'une station. */
@SwWindow("CardMake.EmpForm")
public class EmpForm extends CardMakeForm {

    public void EmpForm_Load() {
        try {
            LoadStations("StationSelect");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void StationSelect_SelectedIndexChanged() {
        if (c("StationSelect").SelectedValue() != null) {
            LoadStationEmpCard(Conv.toInt(c("StationSelect").SelectedValue()));
        }
    }

    private void LoadStationEmpCard(int StationID) {
        try {
            g("TransactionView").RowsClear();
            for (Row item : sendRows("ExtendGetEmployeeCard", Row.of("StationID", StationID))) {
                g("TransactionView").RowsAdd("", item.str("ASN"), GetCardStatue(item.i("CardStatusTypeID")));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void QueryBtn_Click() {
        Open("CardMake.EmployeeCardAdd");
    }

    public void TransactionView_CellContentDoubleClick() {
        try {
            int index = g("TransactionView").CurrentRowIndex();
            ShowDialog("CardMake.EmployeeCardUpdate", r -> {
                if (r.ok()) {
                    StationSelect_SelectedIndexChanged();
                }
            }, "ASN", String.valueOf(g("TransactionView").Cell(index, 1)));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
