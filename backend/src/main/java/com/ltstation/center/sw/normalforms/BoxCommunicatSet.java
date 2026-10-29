package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.BaseInfo;
import com.ltstation.center.sw.normal.IBoxClientParam;
import com.ltstation.center.sw.normal.NormalEngine;
import com.ltstation.center.sw.normal.NormalFunction;
import com.ltstation.center.sw.normal.NozSerial;

/**
 * BoxCommunicatSet : boîtiers et leurs ports (protocole, type d'appareil), version et mise à jour du programme.
 * La fenêtre est la vue BoxSet du moteur : versions et progression du téléchargement lui sont envoyées.
 */
@SwWindow(value = "NormalForm.BoxCommunicatSet", setup = true)
public class BoxCommunicatSet extends SwForm implements NormalEngine.BoxSetView {

    private int Port_ID;

    @Override
    protected void init() {
        NormalEngine.BoxSet = this;
        // mises à jour envoyées par les fils (version, progression)
        Poll(1000);
    }

    public void BoxPortView_CellDoubleClick() {
        try {
            int id = Conv.toInt(g("BoxPortView").Cell(g("BoxPortView").CurrentRowIndex(), 0));
            ShowDialog("NormalForm.BoxNoz", r -> {
                if ("OK".equals(r.result())) {
                    ShowPortList();
                }
            }, "UpdateType", "Update", "ID", id, "PortID", Port_ID);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static String parity(int p) {
        return switch (p) {
            case 0 -> L("None");
            case 1 -> L("Even");
            case 2 -> L("Odd");
            default -> "";
        };
    }

    private static String protocol(int t) {
        return switch (t) {
            case 0 -> L("None");
            case 1 -> L("BlueSky");
            case 2 -> L("Tatsuno");
            case 3 -> L("Japan");
            case 4 -> L("Dart");
            case 5 -> L("ATG");
            case 6 -> "RFID";
            case 7 -> L("Eaglestar");
            case 8 -> L("HongYang");
            case 9 -> "SS-Lan";
            case 10 -> "BlueSkyPlus";
            case 11 -> "CNG PLC";
            case 12 -> L("PriceShow");
            default -> "";
        };
    }

    /** Type d'appareil affiché selon le protocole (664 / 886, sonde, RFID, PLC, afficheur de prix). */
    private static String device(Row item) {
        int t = item.i("Box_CommunicationTypeID");
        int d = item.i("Box_DeviceType");
        if (t >= 1 && t <= 4 || t >= 7 && t < 11) {
            return d == 0 ? "664" : "886";
        }
        return switch (t) {
            case 6 -> "RFID";
            case 5 -> switch (d) {
                case 0 -> L("ATG");
                case 1 -> L("KangYuProbe");
                case 2 -> L("MAGNEHELIC");
                default -> "";
            };
            case 11 -> "CNG PLC";
            case 12 -> L("PriceShow");
            default -> "";
        };
    }

    private void ShowPortList() {
        try {
            Row selected = c("BoxSelect").SelectedRow();
            if (selected == null) {
                throw new IllegalStateException(L("PLSSelectBox"));
            }
            Row box = db().first("BoxList", "{ID} = :i", p("i", selected.get("ID")));
            if (box == null) {
                throw new IllegalStateException(L("BoxDeviceLost"));
            }
            g("BoxPortView").RowsClear();
            for (Row item : db().list("Box_Set", "{Port_ID} = :p", p("p", box.get("ID")))) {
                g("BoxPortView").RowsAdd(item.get("ID"), item.get("Box_PortID"), item.get("Box_Baud"), item.get("Box_Stop"), parity(item.i("Box_Parity")),
                    protocol(item.i("Box_CommunicationTypeID")), device(item), item.get("Box_IntervalTime"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadBoxList() {
        try {
            c("BoxSelect").DataSource(db().list("BoxList"), "BoxName", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void BoxCommunicatSet_Load() {
        LoadBoxList();
        BoxSelect_SelectedIndexChanged();
    }

    public void SaveButton_Click() {
        try {
            if (c("BoxSelect").SelectedItem() == null) {
                throw new IllegalStateException("Object reference not set to an instance of an object.");
            }
            MessageBox(L("OperateSuccess"));
            NormalFunction.MainFormMessage.accept(1044, 0, 0);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** BoxPortView_MouseDoubleClick : double-clic hors d'une ligne = nouveau port (événement non relié dans la mise en page). */
    public void BoxPortView_MouseDoubleClick() {
        AddPort_Click();
    }

    public void Cancel_Click() {
        Close("Cancel");
    }

    public void AddPort_Click() {
        ShowDialog("NormalForm.BoxNoz", r -> {
            if ("OK".equals(r.result())) {
                ShowPortList();
            }
        }, "UpdateType", "Add", "ID", 0, "PortID", Port_ID);
    }

    public void VersionCheck_Click() {
        try {
            if (IniInfo.WifiMode == 1) {
                int id = Conv.toInt(c("BoxSelect").SelectedValue());
                IBoxClientParam box = BaseInfo.BoxClientList.stream().filter(t -> t.ID == id).findFirst().orElse(null);
                if (box != null) {
                    box.VersionIsCheck = 1;
                }
                NormalFunction.MainFormMessage.accept(1281, box.ID, 0);
            } else {
                NormalFunction.MainFormMessage.accept(1281, Port_ID, 0);
            }
        } catch (RuntimeException ex) {
            SetErrorTip(true, ex.getMessage());
        }
    }

    /** Version du programme du boîtier série ; proposition de mise à jour si Center en a une autre. */
    @Override
    public void Box_ShowVersion(String version) {
        try {
            SetText(version);
            NozSerial port = NormalEngine.Ports.stream().filter(t -> t.ID == Port_ID).findFirst().orElse(null);
            if (port == null) {
                throw new IllegalStateException("Boîtier de communication introuvable");
            }
            if (port.CurrentProgram != -1) {
                return;
            }
            String[] center = {""};
            String[] error = {""};
            if (!BaseFunction.GetProgramVersionCenter(0, version, center, error)) {
                throw new IllegalStateException(error[0]);
            }
            if (!center[0].equals(version)) {
                int portId = Port_ID;
                ShowDialog("NormalForm.TipForm", r -> {
                    if ("OK".equals(r.result())) {
                        NormalFunction.MainFormMessage.accept(1537, 0, portId);
                    }
                }, "version", center[0]);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Version du programme d'un boîtier sans fil (après VersionCheck) ; mise à jour proposée. */
    @Override
    public void Box_ShowTCPVersion(int ID) {
        try {
            IBoxClientParam box = BaseInfo.BoxClientList.stream().filter(t -> t.ID == ID).findFirst().orElse(null);
            if (box == null) {
                throw new IllegalStateException("Box Lost");
            }
            if (box.BoxVersion != null && !box.BoxVersion.isEmpty()) {
                SetText(box.BoxVersion);
            }
            if (Port_ID != ID) {
                return;
            }
            String[] center = {""};
            String[] error = {""};
            if (!BaseFunction.GetProgramVersionCenter(0, box.BoxVersion, center, error)) {
                throw new IllegalStateException(error[0]);
            }
            if (center[0].equals(box.BoxVersion) || box.VersionIsCheck != 1) {
                return;
            }
            box.VersionIsCheck = 0;
            Ask(center[0] + " " + L("VersionUpdate"), yes -> {
                if (yes) {
                    NormalFunction.MainFormMessage.accept(1537, 0, box.ID);
                }
            });
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    @Override
    public void Box_UpdateProgress(int MaxNumber, int PageIndex, int PorgressType) {
        SetProgress(MaxNumber, PageIndex, PorgressType);
    }

    public void SetText(String str) {
        c("VersionCode").Text(str);
    }

    /** Barre de progression du téléchargement du programme (0 = page envoyée, 1 = valeur seule). */
    public void SetProgress(int totalPage, int IndexPage, int PorgressType) {
        switch (PorgressType) {
            case 0 -> {
                c("PB").Maximum(totalPage + 1);
                c("PB").Value(IndexPage);
                if (totalPage == IndexPage) {
                    SetErrorTip(false, "");
                }
            }
            case 1 -> c("PB").Value(IndexPage);
            default -> {
            }
        }
    }

    public void SetErrorTip(boolean show, String str) {
        c("ErrorTip").Visible(show);
        c("ErrorTip").Text(str);
    }

    public void DowloadFile_Click() {
        try {
            NormalFunction.MainFormMessage.accept(1282, 0, 0);
        } catch (RuntimeException ex) {
            SetErrorTip(true, ex.getMessage());
        }
    }

    public void AddBox_Click() {
        ShowDialog("NormalForm.BoxCommunication", r -> LoadBoxList());
    }

    public void BoxSelect_SelectedIndexChanged() {
        try {
            Port_ID = Conv.toInt(c("BoxSelect").SelectedValue());
            Row box = db().first("BoxList", "{ID} = :i", p("i", Port_ID));
            if (box != null) {
                c("VersionCode").Text(box.str("BoxVersion"));
                ShowPortList();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
