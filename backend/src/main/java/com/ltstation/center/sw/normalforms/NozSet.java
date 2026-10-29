package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;

/** NozSet : pistolets sur ports série (ajout, modification, suppression signalée à la fenêtre principale : 1029). */
@SwWindow("NormalForm.NozSet")
public class NozSet extends SwForm {

    public void NozSet_Load() {
        ShowData();
    }

    private void ShowData() {
        try {
            g("NozView").RowsClear();
            for (Row item : db().list("NormalNoz", "{IsUsing} = true ORDER BY {NozID}", null)) {
                Row port = db().first("NormalPortSet", "{ID} = :i", p("i", item.get("PortID")));
                if (port == null) {
                    continue;
                }
                Row tank = db().first("NormalTank", "{ID} = :i", p("i", item.get("TankID")));
                if (tank == null) {
                    continue;
                }
                Row grade = db().first("NormalGrade", "{ID} = :i", p("i", item.get("GradeID")));
                if (grade != null) {
                    g("NozView").RowsAdd(item.get("ID"), item.get("NozID"), port.str("PortID"), tank.str("TankName"), grade.str("GradeName"),
                        item.get("Price"));
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void Add_Click() {
        ShowDialog("NormalForm.NozAdd", r -> {
            if ("OK".equals(r.result())) {
                ShowData();
            }
        });
    }

    private int currentId() {
        return Conv.toInt(g("NozView").Cell(g("NozView").CurrentRowIndex(), 0));
    }

    public void NozView_CellDoubleClick() {
        try {
            int id = currentId();
            ShowDialog("NormalForm.NozUpdate", r -> {
                if ("OK".equals(r.result())) {
                    ShowData();
                }
            }, "ID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Delete_Click() {
        try {
            int id = currentId();
            if (Confirm("Supprimer ?")) {
                Row noz = db().first("NormalNoz", "{ID} = :i", p("i", id));
                if (noz == null) {
                    throw new IllegalStateException("Pistolet introuvable");
                }
                db().deleteEntity("NormalNoz", noz);
                NormalFunction.MainFormMessage.accept(1029, noz.i("NozID"), 0);
                ShowData();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
