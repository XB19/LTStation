package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.PrintClass;
import com.ltstation.center.sw.normal.NormalEngine;
import java.util.ArrayList;
import java.util.List;

/**
 * NormalMainForm : fenêtre principale du mode Normal (« NormalSystem »). Les fils, ports et
 * commandes sont dans {@link NormalEngine} ; la fenêtre affiche leur état et ouvre les réglages.
 */
@SwWindow("NormalForm.NormalMainForm")
public class NormalMainForm extends SwForm {

    private int shownVersion = -1;
    private String shownShift;

    /** Menus visibles selon les droits de l'employé (Auth = « 1,3,8… »). */
    private static final String[][] AUTH = {
        {"1", "BaseMessage"}, {"2", "BoxCodeSet"}, {"3", "GradeAndPrice", "OilPrice"}, {"4", "TankItem"}, {"5", "ShiftItem"},
        {"6", "EmpLoginItem"}, {"7", "EmpManager"}, {"8", "ShiftReport", "ShiftReportCheck"}, {"9", "DailyR", "Report"},
        {"10", "MonthR", "MonthReport"}, {"11", "TransactionR", "Transaction"}, {"12", "AuthShiftChange"}, {"13", "CardMake"},
        {"14", "CardUpdate"}, {"15", "BalanceDeal"}, {"16", "CardLost"}, {"17", "CardReplace"}, {"18", "EmpCardManager"},
        {"19", "UserInfo"}, {"20", "SPrint"}, {"21", "ShiftHand"}, {"22", "RInventory"}, {"23", "StationStore"},
    };

    private void Switch_Auth() {
        try {
            for (String[] a : AUTH) {
                for (int i = 1; i < a.length; i++) {
                    c(a[i]).Visible(false);
                }
            }
            String[] array = IniInfo.Auth != null && !IniInfo.Auth.isEmpty() ? IniInfo.Auth.split(",") : new String[0];
            for (String code : array) {
                for (String[] a : AUTH) {
                    if (a[0].equals(code)) {
                        for (int i = 1; i < a.length; i++) {
                            c(a[i]).Visible(true);
                        }
                    }
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void NormalMainForm_Load() {
        try {
            Switch_Auth();
            NormalEngine.LoadStation();
            c("LoginName").Text(L("LoginEmployee") + ":" + IniInfo.LoginName);
            // GunAndTankTab.TabPages.Remove(RFIDShow)
            c("RFIDShow").Visible(false);
            c("GunAndTankTab").SelectedIndex(0);
            Embed(NormalEngine.nozshow, "GunPage");
            Embed(NormalEngine.tankShow, "TankShow");
            Embed(NormalEngine.RFIDForm, "RFIDShow");
            Embed(NormalEngine.PLCForm, "PLCShow");
            // Comme l'original, les fils du mode Normal ne tournent que pour une station en mode Normal
            // (sinon les téléchargements de Center écraseraient les données du mode IC, ex. Employees.Memo1)
            if (IniInfo.SystemType == 1 || Boolean.getBoolean("ltstation.normal.force")) {
                NormalEngine.Start();
            }
            Timer("RefreshView", 1000);
            RefreshView();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Affichage de l'état du moteur (BeginInvoke de l'original) : transactions, poste, erreurs, RFID. */
    public void RefreshView() {
        int version = NormalEngine.DataVersion;
        if (version != shownVersion) {
            shownVersion = version;
            List<Row> rows = NormalEngine.OilData;
            g("OilDataView").RowsClear();
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                g("OilDataView").RowsAdd(r.get("ID"), r.get("NozID"), r.get("Volume"), r.get("Amount"), r.get("CreateTime"));
                if (r.i("Settlement") != 0) {
                    g("OilDataView").RowBackColor(i, "Red");
                }
            }
            g("ToatlGrade").DataSource(NormalEngine.GradeTotal);
            c("TransactionUp").Text("Transaction:" + NormalEngine.UnUploadCount);
        }
        String shift = NormalEngine.CurrentShiftText;
        if (!shift.equals(shownShift)) {
            shownShift = shift;
            c("CurrentShift").Text(shift);
            c("LoginName").Text(L("LoginEmployee") + ":" + IniInfo.LoginName);
        }
        String err = NormalEngine.ErrorText;
        c("ErrorMessage").Visible(err != null);
        if (err != null) {
            c("ErrorMessage").Text(err);
        }
        // ShowRFIDForm n'est jamais appelé par l'original (délégué créé mais non invoqué) : RFIDDataView reste vide
    }

    public void NormalMainForm_FormClosed() {
        IniInfo.ThreadStop = false;
        NormalEngine.Stop();
    }

    // ------------------------------------------------------------ menus

    public void PortItem_Click() {
        switch (sender) {
            case "PortItem" -> ShowDialog("NormalForm.PortSet", r -> { });
            case "TankItem" -> ShowDialog("NormalForm.TankSet", r -> { });
            case "ExitItem" -> {
                IniInfo.ThreadStop = false;
                NormalEngine.Stop();
                Close();
            }
            case "EmpLoginItem" -> ShowDialog("Login", r -> Switch_Auth());
            // EFD / eTIMS (fil KenyaTransactionUp) relèvent de BoxBase, pas encore porté
            case "superviseSet" -> ShowDialog("Station.SuperviseForm", r -> { });
            case "GradeAndPrice" -> ShowDialog("NormalForm.GradeSet", r -> { });
            case "NozItem" -> ShowDialog("NormalForm.NozSet", r -> { });
            case "BaseMessage" -> ShowDialog("NormalForm.NormalStationCreate", r -> { });
            case "ShiftItem" -> ShowDialog("ShiftItemForm", r -> { });
            case "BoxCodeSet" -> ShowDialog("NormalForm.BoxCommunicatSet", r -> { });
            case "TankVolumeItem" -> ShowDialog("NormalForm.TankCable", r -> { });
            default -> {
            }
        }
    }

    private static final String[] TOOL_BUTTONS = {
        "OilPrice", "Transaction", "ShiftReportCheck", "Report", "MonthReport", "EmpCardManager", "CardMake", "CardUpdate", "CardLost",
        "CardReplace", "BalanceDeal", "UserInfo", "SPrint", "ShiftHand", "RInventory", "StationStore",
    };

    public void OilPrice_Click() {
        try {
            for (String b : TOOL_BUTTONS) {
                c(b).Checked(b.equals(sender));
            }
            switch (sender) {
                case "OilPrice" -> ShowDialog("NormalForm.GradeSet", r -> { });
                case "CardMake" -> ShowDialog("Card.CardBuild", r -> { });
                case "CardLost" -> ShowDialog("Card.CardLost", r -> { });
                case "UserInfo" -> ShowDialog("Card.UserInfoForm", r -> { });
                case "Transaction" -> ShowDialog("NormalForm.NormalTransaction", r -> { });
                case "MonthReport" -> ShowDialog("NormalForm.MonthReport", r -> { });
                case "BalanceDeal" -> ShowDialog("Card.Card_Balance_Deal", r -> { });
                case "CardReplace" -> ShowDialog("Card.CardReplace", r -> { });
                case "Report" -> ShowDialog("NormalForm.DailyReport", r -> { });
                case "SPrint" -> ShowDialog("NormalForm.StationPrint", r -> { });
                case "CardUpdate" -> ShowDialog("Card.Card_Update", r -> { });
                case "RInventory" -> ShowDialog("NormalForm.TankStockRecored", r -> { });
                case "ShiftReportCheck" -> ShowDialog("NormalForm.DutyReport", r -> { });
                case "ShiftHand" -> ShowDialog("NormalForm.HandShiftReport", r -> { });
                case "EmpCardManager" -> ShowDialog("Card.EmpCardManager", r -> { });
                case "StationStore" -> ShowDialog("BoxScreenView.OtherProductView", r -> { }, "Type", "BillRun");
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void AuthShiftChange_Click() {
        try {
            switch (sender) {
                case "AuthShiftChange" -> ShowDialog("NormalForm.AuthShiftBox", r -> { });
                case "StationPrintSet" -> ShowDialog("NormalForm.DevicePrint", r -> { });
                case "StationConfigSet" -> ShowDialog("NormalForm.StationMessageSet", r -> { });
                case "ExtendSerialPort" -> ShowDialog("NormalForm.ExtendMachine", r -> { });
                case "ComStatue" -> ShowDialog("NormalForm.PortStatueChange", r -> { });
                case "TransactionR" -> ShowDialog("NormalForm.NormalTransaction", r -> { });
                case "TankCableCheck" -> ShowDialog("NormalForm.TankCableCheckForm", r -> { });
                case "CreateCable" -> BaseFunction.CreateTankCable(1, new String[1]);
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ShiftReport_Click() {
        try {
            switch (sender) {
                case "ShiftReport" -> ShowDialog("NormalForm.DutyReport", r -> { });
                case "DailyR" -> ShowDialog("NormalForm.DailyReport", r -> { });
                case "MonthR" -> ShowDialog("NormalForm.MonthReport", r -> { });
                case "TransactionR" -> ShowDialog("NormalForm.NormalTransaction", r -> { });
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void EmpManager_Click() {
        ShowDialog("NormalForm.EmpForms", r -> { });
    }

    public void RFIDItem_Click() {
        switch (sender) {
            case "RFIDItem" -> ShowDialog("RFIDForm", r -> { });
            case "DisplayButton" -> ShowDialog("NormalForm.PasswordWrite", r -> { });
            case "OverBtn" -> ShowDialog("NormalForm.OverCountForm", r -> { });
            case "ScreenShow" -> ShowDialog("NormalForm.BoxScreenV2", r -> { });
            case "OtherConfig" -> ShowDialog("NormalForm.StationOtherConfig", r -> { });
            case "DeviceConfig" -> ShowDialog("NormalForm.StationDeviceConfig", r -> { });
            // tESTToolStripMenuItem : essai d'étalonnage de la cuve 2 (TankCalibrationEngine) laissé par les développeurs
            default -> {
            }
        }
    }

    public void toolStripMenuItem4_Click() {
    }

    private int currentTransactionId() {
        Row r = g("OilDataView").CurrentRow();
        if (r == null) {
            throw new IllegalStateException(L("PleaseSelect"));
        }
        return Conv.toInt(g("OilDataView").Cell(g("OilDataView").CurrentRowIndex(), 0));
    }

    /** Impression du ticket de la transaction sélectionnée (StationPrint : lignes d'en-tête et de pied). */
    public void PrintMenu_Click() {
        try {
            int id = currentTransactionId();
            Row t = db().first("NormalTransaction", "{ID} = :i", p("i", id));
            if (t == null) {
                throw new IllegalStateException("Transaction Lost");
            }
            List<String> list = new ArrayList<>();
            Row sp = db().first("StationPrint");
            list.add(L("Transaction"));
            Row d = null;
            if (sp != null) {
                d = Wire.parseRow(sp.str("PrintData"));
                for (String k : new String[] {"First", "Second", "Third", "Fouth", "Fifth", "Sixth", "Seven"}) {
                    if (!d.str(k).isEmpty()) {
                        list.add(d.str(k));
                    }
                }
            }
            list.add("-------------------------");
            list.add(L("SlipNo") + "&" + t.str("ID"));
            list.add(L("NozID") + "&" + t.str("NozID"));
            list.add(L("Amount") + "&" + Conv.netRound(t.dec("Amount"), IniInfo.Mradio).toPlainString());
            list.add(L("Volume") + "&" + Conv.netRound(t.dec("Volume"), IniInfo.Vradio).toPlainString());
            list.add(L("Price") + "&" + Conv.netRound(t.dec("Price"), IniInfo.Pradio).toPlainString());
            list.add(L("Productgrade") + "&" + t.str("GradeName"));
            if (d != null && !d.str("CarNo").isEmpty()) {
                list.add(L("CarNo") + "&" + d.str("CarNo"));
            }
            list.add(L("Time") + "&" + BaseFunction.StampToDateTime(t.str("CreateTime")).format(Conv.STD));
            list.add(L("ID") + "&" + t.str("ID"));
            list.add(L("Operator") + "&" + IniInfo.LoginName);
            if (d != null) {
                for (String k : new String[] {"Eight", "Nin", "Ten", "Eleventh", "Twelfth", "Thirteenth", "Fourteenth", "Fifteenth", "Sixteenth"}) {
                    if (!d.str(k).isEmpty()) {
                        list.add(d.str(k));
                    }
                }
            }
            PrintClass pc = new PrintClass();
            pc.DataList.addAll(list);
            if (!pc.PrintExtend(sp.str("PrintMachine"))) {
                Print(pc.lines());
            }
        } catch (RuntimeException ex) {
            // comme l'original : erreur ignorée
        }
    }

    public void OilDataView_CellDoubleClick() {
        try {
            int id = currentTransactionId();
            ShowDialog("NormalForm.TransactionShow", r -> {
                if ("OK".equals(r.result())) {
                    NormalEngine.ShowTransaction();
                }
            }, "ID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SettleMentBtn_Click() {
        OilDataView_CellDoubleClick();
    }
}
