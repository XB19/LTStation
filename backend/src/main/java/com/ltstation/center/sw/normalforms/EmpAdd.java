package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.time.LocalDateTime;
import java.util.Map;

/** EmpAdd : nouvel employé créé sur Center (identifiant donné par Center) avec ses droits (Auth = « 1,3,… »). */
@SwWindow(value = "NormalForm.EmpAdd", setup = true)
public class EmpAdd extends SwForm {

    /** Case à cocher → code du droit (menus de la fenêtre principale). */
    private static final Map<String, String> AUTH = Map.ofEntries(
        Map.entry("BaseCheck", "1"), Map.entry("Box_Set", "2"), Map.entry("EnergySet", "3"), Map.entry("TankSet", "4"), Map.entry("ShiftSet", "5"),
        Map.entry("EmployeeLogin", "6"), Map.entry("EmpSet", "7"), Map.entry("ShiftReport", "8"), Map.entry("DayReport", "9"),
        Map.entry("MonthReport", "10"), Map.entry("TransactionQuery", "11"), Map.entry("AuthShift", "12"), Map.entry("CardMakeBtn", "13"),
        Map.entry("CardUpdateBtn", "14"), Map.entry("BalanceBtn", "15"), Map.entry("CardLostBtn", "16"), Map.entry("CardReplaceBtn", "17"),
        Map.entry("EmpManagerBtn", "18"), Map.entry("UserMessageCheck", "19"), Map.entry("PrintMessageCheck", "20"),
        Map.entry("HandShiftCheck", "21"), Map.entry("TankImportCheck", "22"), Map.entry("RetailStoreCheck", "23"));

    /** Droits cochés dans le groupe, dans l'ordre des contrôles. */
    static String authOf(EmpAddLike f, String group) {
        StringBuilder text = new StringBuilder();
        for (String name : f.controls(group)) {
            String code = AUTH.get(name);
            if (code != null && f.checked(name)) {
                text.append(code).append(',');
            }
        }
        return text.toString();
    }

    /** Accès aux cases à cocher (partagé avec EmpUpdate). */
    interface EmpAddLike {
        java.util.List<String> controls(String group);

        boolean checked(String name);
    }

    private final EmpAddLike self = new EmpAddLike() {
        @Override
        public java.util.List<String> controls(String group) {
            return Controls(group);
        }

        @Override
        public boolean checked(String name) {
            return c(name).Checked();
        }
    };

    public void SaveButton_Click() {
        try {
            String name = c("LoginName").Text();
            String password = c("Password").Text();
            String phone = c("Phone").Text();
            if (name.isEmpty() || password.isEmpty() || phone.isEmpty()) {
                throw new IllegalStateException(L("InformationLoss"));
            }
            if (db().any("Employees", "{Name} = :n OR {Telephone} = :t", p("n", name, "t", phone))) {
                throw new IllegalStateException(L("EmpRepeat"));
            }
            Integer max = db().scalar(db().sql("Employees", "select MAX(CASE WHEN {Code} ~ '^\\s*-?\\d+\\s*$' THEN trim({Code})::int END) from [Employees]"),
                null, Integer.class);
            String code = String.valueOf((max == null ? 0 : max) + 1);
            Row emp = Row.of("Name", name, "Gender", "1", "StationID", IniInfo.StationID, "Code", code, "Memo1", name, "Memo2", password,
                "Telephone", phone, "Birthday", LocalDateTime.now(), "Status", "0", "Address", "", "PostCode", "", "StationType", "Add",
                "Auth", authOf(self, "groupBox4"));
            CustomerRespone r = BaseFunction.post("ServerDeal/NormalEmpUpload", SW.dto().has("Employees") ? SW.dto().shape("Employees", emp) : emp);
            if (r.Result()) {
                // la réponse de Center n'est pas chiffrée pour cette action
                Row back = Wire.parseRow(r.Mess());
                emp.put("EmployeeID", back.i("EmployeeID"));
                emp.put("StationType", "4");
                db().insert("Employees", emp);
                MessageBox(L("OperateSuccess"));
                Close("OK");
            } else {
                MessageBox(BaseFunction.DecryptByAES(r.Mess()));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Cancel_Click() {
        Close("Cancel");
    }
}
