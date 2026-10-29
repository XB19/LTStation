package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;
import java.util.List;

/** PortSet : ports série des pistolets (NormalPortSet) ; ajout, modification, suppression (1031), ports des boîtiers. */
@SwWindow("NormalForm.PortSet")
public class PortSet extends SwForm {

    public void PortSet_Load() {
        ShowData();
    }

    private void ShowData() {
        try {
            g("ComConfigView").RowsClear();
            for (Row item : db().list("NormalPortSet", "ORDER BY {PortID}", null)) {
                String parity = switch (item.i("Parity")) {
                    case 0 -> "Aucune";
                    case 1 -> "Impaire";
                    case 2 -> "Paire";
                    default -> "";
                };
                String type = item.i("AgreementTypeID") == 0 ? "Boîtier de communication" : "";
                g("ComConfigView").RowsAdd(item.get("ID"), item.str("PortID"), item.get("Baud"), item.get("DataBit"), item.get("StopBit"), parity, type);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Add_Click() {
        ShowDialog("NormalForm.PortAdd", r -> {
            if ("OK".equals(r.result())) {
                ShowData();
            }
        });
    }

    private int currentId() {
        if (g("ComConfigView").CurrentRow() == null) {
            throw new IllegalStateException("Configurez d'abord un port");
        }
        return Conv.toInt(g("ComConfigView").Cell(g("ComConfigView").CurrentRowIndex(), 0));
    }

    public void ComConfigView_CellDoubleClick() {
        try {
            int id = currentId();
            ShowDialog("NormalForm.PortUpdate", r -> {
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
                Row set = db().first("NormalPortSet", "{ID} = :i", p("i", id));
                if (set == null) {
                    throw new IllegalStateException("Port série introuvable");
                }
                db().deleteEntity("NormalPortSet", set);
                NormalFunction.MainFormMessage.accept(1031, set.i("ID"), 0);
                ShowData();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Delopy_Click() {
        try {
            int id = currentId();
            ShowDialog("NormalForm.BoxPortSet", r -> { }, "PID", id, "Index", g("ComConfigView").CurrentRowIndex());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** DowLoad_Click : pas relié à un bouton dans l'original (téléchargement vers les ports de protocole 1). */
    public void DowLoad_Click() {
        try {
            List<Row> list = db().list("NormalPortSet");
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).i("AgreementTypeID") == 1) {
                    NormalFunction.MainFormMessage.accept(1030, i, 0);
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
