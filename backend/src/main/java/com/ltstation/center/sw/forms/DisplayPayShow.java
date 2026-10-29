package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** DisplayPayShow : équipements d'extension (application, RFID, afficheur de prix, écran de paiement). */
@SwWindow("DisplayPayShow")
public class DisplayPayShow extends SwForm {

    private List<Row> GetDataList() {
        try {
            return db().list("ExtendShow");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return List.of();
        }
    }

    private void ShowData(List<Row> list) {
        try {
            g("PayView").RowsClear();
            for (Row item : list) {
                g("PayView").RowsAdd(item.get("ID"), GetExtendType(item.i("ExtendType")), item.get("Port"), item.get("IP"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private String GetExtendType(int id) {
        return switch (id) {
            case 0 -> "APP";
            case 1 -> "RFID";
            case 2 -> L("PriceShow");
            case 3 -> "Écran de paiement";
            default -> "";
        };
    }

    public void DisplayPayShow_Load() {
        ShowData(GetDataList());
    }

    /** Un seul gestionnaire pour Fermer / Ajouter / Supprimer (sender = bouton). */
    public void CloseButton_Click() {
        try {
            switch (sender) {
                case "CloseButton" -> Close();
                case "AddButton" -> ShowDialog("AddExtendPay", r -> {
                    if (r.ok()) {
                        ShowData(GetDataList());
                    }
                });
                case "DeleteButton" -> {
                    int index = g("PayView").CurrentRowIndex();
                    if (index < 0) {
                        throw new IllegalStateException("Object reference not set to an instance of an object.");
                    }
                    int id = Integer.parseInt(Conv.toStr(g("PayView").Cell(index, "ID")));
                    Row e = db().first("ExtendShow", "{ID} = :id", p("id", id));
                    if (e == null) {
                        throw new IllegalStateException(L("ObjectEmpty"));
                    }
                    db().deleteEntity("ExtendShow", e);
                }
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void PayView_CellDoubleClick() {
        try {
            int index = g("PayView").CurrentRowIndex();
            if (index < 0) {
                throw new IllegalStateException("Object reference not set to an instance of an object.");
            }
            ShowDialog("AddExtendPay", r -> { }, "UpdateMessage", Integer.parseInt(Conv.toStr(g("PayView").Cell(index, "ID"))));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
