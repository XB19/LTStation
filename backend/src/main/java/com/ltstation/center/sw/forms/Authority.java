package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;
import java.util.Map;

/** Authority : droits d'un profil (cases 1 à 24), enregistrés dans AuthorityMess.AuthorityContent. */
@SwWindow("Authority")
public class Authority extends SwForm {

    /** Numéro de droit → case à cocher. */
    private static final Map<String, String> BOXES = Map.ofEntries(Map.entry("1", "PriceBox"), Map.entry("4", "MakeCardBox"),
        Map.entry("7", "CardUpdateBox"), Map.entry("10", "CardLostBox"), Map.entry("13", "CardChangeBox"), Map.entry("16", "DepositBox"),
        Map.entry("2", "OilTransactionBox"), Map.entry("5", "EmpBox"), Map.entry("8", "ShiftBox"), Map.entry("11", "DailyBox"),
        Map.entry("14", "MonthBox"), Map.entry("17", "CardSearchBox"), Map.entry("3", "SocreBox"), Map.entry("6", "DisCountBox"),
        Map.entry("9", "KeyManagementBox"), Map.entry("12", "PSAMBox"), Map.entry("15", "BusinessBox"), Map.entry("18", "PasswodLostBox"),
        Map.entry("19", "SystemSet"), Map.entry("20", "StationManager"), Map.entry("21", "Communication"), Map.entry("22", "ExtendManager"),
        Map.entry("23", "UserCheck"));

    public void Authority_Load() {
        c("AuthoritySelect").ItemsClear();
        c("AuthoritySelect").DataSource(db().list("AuthorityMess"), "Name", "ID");
    }

    public void AuthoritySelect_SelectedIndexChanged() {
        try {
            ClearCheckBox();
            Row a = c("AuthoritySelect").SelectedRow();
            for (String s : a.str("AuthorityContent").split(",", -1)) {
                String box = BOXES.get(s);
                if (box != null) {
                    c(box).Checked(true);
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage() == null ? "Object reference not set to an instance of an object." : ex.getMessage());
        }
    }

    private void ClearCheckBox() {
        for (String box : List.of("PriceBox", "MakeCardBox", "CardUpdateBox", "CardLostBox", "CardChangeBox", "DepositBox", "OilTransactionBox", "EmpBox",
            "ShiftBox", "DailyBox", "MonthBox", "CardSearchBox", "SocreBox", "DisCountBox", "KeyManagementBox", "PSAMBox", "BusinessBox", "PasswodLostBox",
            "SystemSet", "StationManager", "Communication", "ExtendManager", "UserCheck", "AuthorityBox")) {
            c(box).Checked(false);
        }
    }

    public void ShiftClose_Click() {
        Close();
    }

    public void Confirm_Click() {
        StringBuilder text = new StringBuilder();
        Row a = c("AuthoritySelect").SelectedRow();
        for (String n : Controls("AuthorithBox")) {
            if (TypeOf(n).endsWith("CheckBox") && c(n).Checked()) {
                String t = c(n).Text();
                text.append(t, 0, t.indexOf('.')).append(',');
            }
        }
        a.put("AuthorityContent", text.toString());
        db().update("AuthorityMess", a);
        MessageBox(L("OperateSuccess"));
    }

    public void AddAuthority_Click() {
        ShowDialog("AddAuthority", r -> { });
    }
}
