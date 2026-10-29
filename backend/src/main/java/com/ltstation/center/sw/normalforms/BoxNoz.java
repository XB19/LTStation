package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.BaseInfo;
import com.ltstation.center.sw.normal.IBoxClientParam;
import com.ltstation.center.sw.normal.LTNozStatuet;
import com.ltstation.center.sw.normal.NormalEngine;
import com.ltstation.center.sw.normal.NormalFunction;
import com.ltstation.center.sw.normal.NozSerial;
import com.ltstation.center.sw.normal.WirelessCmd.CmdList;
import java.util.List;

/**
 * BoxNoz : port d'un boîtier (arguments UpdateType « Add » / « Update », ID = Box_Set, PortID = boîtier) avec ses
 * pistolets, lecteurs RFID ou appareils (port 5) ; vérification et mise à jour des versions (vue NozVerUpdate du moteur).
 */
@SwWindow(value = "NormalForm.BoxNoz", setup = true)
public class BoxNoz extends SwForm implements NormalEngine.NozVerView {

    private int ID;

    private int PortID() {
        return argInt("PortID");
    }

    @Override
    protected void init() {
        ID = argInt("ID");
        boolean add = "Add".equals(argStr("UpdateType"));
        c("PortSelect").Enabled(add);
        if (!add) {
            NormalEngine.NozVerUpdate = this;
            Poll(1000);
        }
    }

    /** ComboBox.SelectedItem = valeur (élément de la liste). */
    private void select(String ctl, String value) {
        int i = c(ctl).Items().indexOf(value);
        if (i >= 0) {
            c(ctl).SelectedIndex(i);
        }
    }

    public void BoxNoz_Load() {
        if (!"Update".equals(argStr("UpdateType"))) {
            c("DeleteBtn").Visible(false);
            return;
        }
        c("DeleteBtn").Visible(true);
        Row set = db().first("Box_Set", "{ID} = :i", p("i", ID));
        if (set != null) {
            select("PortSelect", set.str("Box_PortID"));
            select("BaudSelect", set.str("Box_Baud"));
            select("TimeSelect", set.str("Box_IntervalTime"));
            c("AgreeMentSelect").SelectedIndex(set.i("Box_CommunicationTypeID"));
            AgreeMentSelect_SelectedIndexChanged();
            PortSelect_SelectedIndexChanged();
            c("DeviceSelect").SelectedIndex(set.i("Box_DeviceType"));
            c("StopSelect").SelectedIndex(set.i("Box_Stop"));
            c("ParitySelect").SelectedIndex(set.i("Box_Parity"));
            if (set.i("Box_PortID") == 5) {
                ShowDeviceData();
            } else if (set.i("Box_CommunicationTypeID") == 6) {
                ShowRFIDData(set.i("ID"));
            } else {
                ShowNozData(set.i("ID"));
            }
        }
    }

    private void ShowNozData(int boxPortId) {
        try {
            g("NozView").RowsClear();
            for (Row item : db().list("NormalNoz", "{Box_Port_ID} = :b", p("b", boxPortId))) {
                g("NozView").RowsAdd(item.get("ID"), item.get("NozID"), item.get("Address"), BaseFunction.GetNormalTankName(item.i("TankID")),
                    BaseFunction.GetNormalGradeByID(item.i("GradeID")), item.get("Price"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowRFIDData(int boxPortId) {
        try {
            g("RFIDView").RowsClear();
            for (Row item : db().list("RFIDConfig", "{Box_Port} = :b", p("b", boxPortId))) {
                g("RFIDView").RowsAdd(item.get("ID"), item.get("RFIDNo"), item.get("Address"), item.get("NozID"));
            }
        } catch (RuntimeException ex) {
            MessageBox("RFID : " + ex.getMessage());
        }
    }

    private void ShowDeviceData() {
        try {
            g("DeviceView").RowsClear();
            for (Row item : db().list("StationDeviceSet", "{BoxID} = :b ORDER BY {ID}", p("b", PortID()))) {
                String type = switch (item.i("StationDeviceType")) {
                    case 0 -> L("None");
                    case 1 -> L("Printer");
                    case 2 -> L("ATG");
                    case 3 -> L("PriceShow");
                    default -> "";
                };
                // comme l'original : 1 et 2 sont tous deux affichés « Even »
                String parity = switch (item.i("DeviceParity")) {
                    case 0 -> L("None");
                    case 1, 2 -> L("Even");
                    default -> "";
                };
                g("DeviceView").RowsAdd(item.get("ID"), type, item.get("DeviceAddress"), BaseFunction.Buads[item.i("DeviceBuad")],
                    80 + 10 * item.i("DeviceInterval"), item.get("DeviceStop"), parity);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void SaveButton_Click() {
        try {
            int boxPortId = Integer.parseInt(c("PortSelect").Text().trim());
            int baud = Integer.parseInt(c("BaudSelect").Text().trim());
            int interval = Integer.parseInt(c("TimeSelect").Text().trim());
            int protocol = c("AgreeMentSelect").SelectedIndex();
            int device = c("DeviceSelect").SelectedIndex();
            int stop = c("StopSelect").SelectedIndex();
            int parity = c("ParitySelect").SelectedIndex();
            if (protocol == -1 || device == -1 || stop == -1 || parity == -1) {
                throw new IllegalStateException(L("InformationLost"));
            }
            if (db().any("Box_Set", "{ID} <> :i AND {Box_PortID} = :b AND {Port_ID} = :p", p("i", ID, "b", boxPortId, "p", PortID()))) {
                throw new IllegalStateException(L("PortExists"));
            }
            Row values = Row.of("Box_PortID", boxPortId, "Box_Baud", baud, "Box_IntervalTime", interval, "Box_CommunicationTypeID", protocol,
                "Box_DeviceType", device, "Box_Stop", stop, "Box_Parity", parity, "Port_ID", PortID());
            if ("Update".equals(argStr("UpdateType"))) {
                Row set = db().first("Box_Set", "{ID} = :i", p("i", ID));
                if (set == null) {
                    throw new IllegalStateException(L("DevicePortError"));
                }
                set.putAll(values);
                db().update("Box_Set", set);
            } else {
                ID = Conv.toInt(db().insert("Box_Set", values));
            }
            NormalFunction.MainFormMessage.accept(1030, 0, ID);
            MessageBox(L("OperateSuccess"));
            Close("OK");
        } catch (NumberFormatException ex) {
            MessageBox(L("FormatError"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DeleteBtn_Click() {
        try (var tx = db().begin()) {
            Row port = db().first("Box_Set", "{ID} = :i", p("i", ID));
            if (port == null) {
                throw new IllegalStateException(L("DevicePortError"));
            }
            int type = port.i("Box_CommunicationTypeID");
            if (port.i("Box_PortID") == 5) {
                db().delete("StationDeviceSet", null, null);
            } else if (type == 6) {
                List<Row> list = db().list("RFIDConfig", "{Box_Port} = :b", p("b", port.i("ID")));
                db().delete("RFIDConfig", "{Box_Port} = :b", p("b", port.i("ID")));
                for (Row item : list) {
                    NormalFunction.MainFormMessage.accept(1033, item.i("ID"), 0);
                }
            } else if (type >= 1 && type <= 4 || type >= 7) {
                List<Row> list = db().list("NormalNoz", "{Box_Port_ID} = :b", p("b", port.i("ID")));
                for (Row item : list) {
                    BaseFunction.post("ServerDeal/NormalNozUpload", SW.dto().make("StationNozMess", "StationID", IniInfo.StationID, "NNozID", item.i("NozID"),
                        "ONozID", item.i("NozID"), "UpdateType", "Delete"));
                }
                db().delete("NormalNoz", "{Box_Port_ID} = :b", p("b", port.i("ID")));
                for (Row item : list) {
                    NormalFunction.MainFormMessage.accept(1029, item.i("NozID"), 0);
                }
            }
            db().deleteEntity("Box_Set", port);
            tx.commit();
            MessageBox(L("OperateSuccess"));
            if (IniInfo.WifiMode == 1) {
                IBoxClientParam box = BaseInfo.BoxClientList.stream().filter(t -> t.ID == port.i("Port_ID")).findFirst().orElse(null);
                if (box != null) {
                    CmdList c = new CmdList();
                    c.CmdType = "A3";
                    c.NozID = port.i("Box_PortID");
                    c.DeviceNo = box.NoHead;
                    box.cmdNormal.add(c);
                }
            } else {
                NozSerial serial = NormalEngine.Ports.stream().filter(t -> t.ID == port.i("Port_ID")).findFirst().orElse(null);
                if (serial != null) {
                    LTNozStatuet l = new LTNozStatuet();
                    l.Statue = 163;
                    l.NozID = port.i("Box_PortID");
                    l.PreType = -99;
                    serial.LTStatue.add(l);
                }
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void refresh(Runnable show) {
        if (db().first("Box_Set", "{ID} = :i", p("i", ID)) != null) {
            show.run();
        }
    }

    public void NozView_CellDoubleClick() {
        try {
            int id = Conv.toInt(g("NozView").Cell(g("NozView").CurrentRowIndex(), 0));
            ShowDialog("NormalForm.BoxNozDetial", r -> {
                if ("OK".equals(r.result())) {
                    refresh(() -> ShowNozData(ID));
                }
            }, "UpdateType", "Update", "ID", id, "PortID", ID, "Dowload", "");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Menu_Add_Click() {
        try {
            if (ID == 0) {
                throw new IllegalStateException(L("DevicePortError"));
            }
            if (c("PortSelect").SelectedIndex() == 4) {
                ShowDialog("Port.BoxDeviceSet", r -> {
                    if ("OK".equals(r.result())) {
                        ShowDeviceData();
                    }
                }, "UpdateType", "Add", "ID", PortID());
                return;
            }
            int a = c("AgreeMentSelect").SelectedIndex();
            if (a >= 1 && a <= 4 || a >= 7 && a <= 10) {
                ShowDialog("NormalForm.BoxNozDetial", r -> {
                    if ("OK".equals(r.result())) {
                        refresh(() -> ShowNozData(ID));
                    }
                }, "UpdateType", "Add", "ID", 0, "PortID", ID, "Dowload", "");
                return;
            }
            if (a == 6) {
                ShowDialog("NormalForm.BoxRFIDConfig", r -> {
                    if ("OK".equals(r.result())) {
                        refresh(() -> ShowRFIDData(ID));
                    }
                }, "UpdateType", "Add", "ID", 0, "PortID", ID, "Dowload", "");
                return;
            }
            throw new IllegalStateException(L("PortNoNeedDevice"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Types d'appareil proposés selon le protocole, et cadres affichés (pistolets, RFID, appareils). */
    public void AgreeMentSelect_SelectedIndexChanged() {
        try {
            if (c("PortSelect").SelectedIndex() == 4) {
                return;
            }
            List<Object> items = switch (c("AgreeMentSelect").SelectedIndex()) {
                case 0, 1, 2, 3, 4, 7, 8, 9, 10 -> List.of("664", "886");
                case 5 -> List.of(L("ATG"), L("KangYuProbe"), L("MAGNEHELIC"));
                case 6 -> List.of("RFID");
                case 11 -> List.of("CNG PLC");
                case 12 -> List.of(L("PriceShow"));
                default -> null;
            };
            if (items == null) {
                return;
            }
            boolean rfid = c("AgreeMentSelect").SelectedIndex() == 6;
            c("DeviceSelect").Items(items);
            c("groupBox2").Visible(!rfid);
            c("groupBox3").Visible(rfid);
            c("groupBox4").Visible(false);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void RFIDView_CellDoubleClick() {
        try {
            int id = Conv.toInt(g("RFIDView").Cell(g("RFIDView").CurrentRowIndex(), 0));
            ShowDialog("NormalForm.BoxRFIDConfig", r -> {
                if ("OK".equals(r.result())) {
                    refresh(() -> ShowRFIDData(ID));
                }
            }, "UpdateType", "Update", "ID", id, "PortID", ID, "Dowload", "");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void VersionCheck_Click() {
        try {
            Row set = db().first("Box_Set", "{ID} = :i", p("i", ID));
            if (set == null) {
                throw new IllegalStateException(L("BoxLost"));
            }
            NormalFunction.MainFormMessage.accept(1043, set.i("Box_PortID"), set.i("Port_ID"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Version attendue (Center) des pistolets ou lecteurs RFID du port ; "" si elle est à jour. */
    private String outdated(int portId, int boxId) {
        Row set = db().first("Box_Set", "{Box_PortID} = :p AND {Port_ID} = :b", p("p", portId, "b", boxId));
        String[] version = {""};
        String[] error = {""};
        if (set.i("Box_CommunicationTypeID") == 6) {
            BaseFunction.GetProgramVersionCenter(1, "", version, error);
            for (Row item : db().list("RFIDConfig", "{Box_Port} = :b", p("b", set.i("ID")))) {
                if (!version[0].equals(item.str("Version"))) {
                    return version[0];
                }
            }
            return "";
        }
        Row noz = db().first("NormalNoz", "{Box_Port_ID} = :b ORDER BY {NozID}", p("b", set.i("ID")));
        int type = -1;
        if (noz != null) {
            String head = noz.str("Version").substring(0, 2);
            type = head.equals("06") ? 2 : head.equals("11") ? 3 : -1;
        }
        if (type != -1) {
            BaseFunction.GetProgramVersionCenter(type, "", version, error);
            if (!version[0].equals(noz.str("Version"))) {
                return version[0];
            }
        }
        return "";
    }

    /** Version des pistolets d'un boîtier série : mise à jour proposée (TipForm, message 1538). */
    @Override
    public void CheckVersion(int portID, int boxID) {
        try {
            String version = outdated(portID, boxID);
            if (!version.isEmpty()) {
                ShowDialog("NormalForm.TipForm", r -> {
                    if ("OK".equals(r.result())) {
                        NormalFunction.MainFormMessage.accept(1538, portID, boxID);
                    }
                }, "version", version);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** Version des pistolets d'un boîtier sans fil : mise à jour proposée (message 1538). */
    @Override
    public void CheckTcpVersion(int id, int portID) {
        try {
            String version = outdated(portID, id);
            if (!version.isEmpty()) {
                Ask(version + " " + L("VersionUpdate"), yes -> {
                    if (yes) {
                        NormalFunction.MainFormMessage.accept(1538, portID, id);
                    }
                });
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void PortSelect_SelectedIndexChanged() {
        boolean devices = c("PortSelect").SelectedIndex() == 4;
        if (devices) {
            c("groupBox2").Visible(false);
            c("groupBox3").Visible(false);
        }
        c("groupBox4").Visible(devices);
    }

    public void DeviceView_CellDoubleClick() {
        try {
            int id = Conv.toInt(g("DeviceView").Cell(g("DeviceView").CurrentRowIndex(), 0));
            ShowDialog("Port.BoxDeviceSet", r -> {
                if ("OK".equals(r.result())) {
                    ShowDeviceData();
                }
            }, "UpdateType", "Update", "ID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
