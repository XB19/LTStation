package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;

/** BoxPortSet : ports d'un boîtier (arguments PID = boîtier, Index facultatif) ; pistolets du port, programme (1030). */
@SwWindow(value = "NormalForm.BoxPortSet", setup = true)
public class BoxPortSet extends SwForm {

    private int PortID() {
        return argInt("PID");
    }

    private int PortIndex() {
        return arg("Index") != null ? argInt("Index") : -1;
    }

    public void Add_Click() {
        ShowDialog("NormalForm.BoxPortAdd", r -> {
            if ("OK".equals(r.result())) {
                ShowData();
            }
        }, "PID", PortID());
    }

    public void BoxPortSet_Load() {
        ShowData();
    }

    private void ShowData() {
        try {
            g("BoxPortView").RowsClear();
            for (Row item : db().list("Box_Set", "{Port_ID} = :p ORDER BY {Box_PortID}", p("p", PortID()))) {
                // comme l'original : 1 = pair, 2 = impair (inverse de NozDelopy)
                String parity = switch (item.i("Box_Parity")) {
                    case 0 -> "Aucune";
                    case 1 -> "Paire";
                    case 2 -> "Impaire";
                    default -> "";
                };
                String type = switch (item.i("Box_CommunicationTypeID")) {
                    case 0 -> "Aucun protocole";
                    case 1 -> "Blue Sky";
                    case 2 -> "Longye";
                    case 3 -> "Japon";
                    case 4 -> "Dart";
                    case 5 -> "Jaugeur";
                    default -> "";
                };
                g("BoxPortView").RowsAdd(item.get("ID"), item.get("Box_PortID"), item.get("Box_Baud"), item.get("Box_Stop"), parity, type,
                    item.i("Box_DeviceType") == 0 ? "664" : "886", item.get("Box_IntervalTime"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private int cell(int col) {
        if (g("BoxPortView").CurrentRow() == null) {
            throw new IllegalStateException("Créez d'abord un port");
        }
        return Conv.toInt(g("BoxPortView").Cell(g("BoxPortView").CurrentRowIndex(), col));
    }

    public void BoxPortView_CellDoubleClick() {
        try {
            int id = cell(0);
            ShowDialog("NormalForm.BoxPortUpdate", r -> {
                if ("OK".equals(r.result())) {
                    ShowData();
                }
            }, "ID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Box_Noz_Set_Click() {
        try {
            int boxPort = cell(1);
            ShowDialog("NormalForm.Box_Noz_Setting", r -> { }, "ComPortID", PortID(), "Box_Port", boxPort);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DowLoad_Click() {
        try {
            NormalFunction.MainFormMessage.accept(1030, PortIndex(), cell(1));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
