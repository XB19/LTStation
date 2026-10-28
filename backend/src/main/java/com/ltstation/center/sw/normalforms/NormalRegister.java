package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** NormalRegister : assistant d'installation du mode Normal (station, employés, activation, produits, cuves, boîtiers). */
@SwWindow(value = "NormalForm.NormalRegister", setup = true)
public class NormalRegister extends SwForm {

    private static final String[] BUTTONS = {"StationCreate", "EmployeeCreate", "StationActive", "GradeSet", "TankSet", "PortSet"};

    private static void needStation() {
        if (IniInfo.CompanyID == 0 || IniInfo.StationID == 0) {
            throw new IllegalStateException("站点未创建");
        }
    }

    public void OilPrice_Click() {
        try {
            for (String b : BUTTONS) {
                c(b).Checked(b.equals(sender));
            }
            switch (sender) {
                case "StationCreate" -> ShowDialog("NormalForm.NormalStationCreate", r -> { });
                case "EmployeeCreate" -> {
                    needStation();
                    ShowDialog("NormalForm.EmpForms", r -> { });
                }
                case "StationActive" -> {
                    needStation();
                    ShowDialog("Register", r -> { });
                }
                case "GradeSet" -> {
                    needStation();
                    ShowDialog("NormalForm.GradeSet", r -> { });
                }
                case "TankSet" -> {
                    needStation();
                    ShowDialog("NormalForm.TankSet", r -> { });
                }
                case "PortSet" -> {
                    needStation();
                    ShowDialog("NormalForm.BoxCommunicatSet", r -> { });
                }
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Fin de l'installation : station activée sur ce poste (adresse MAC), station et premier employé créés. */
    public void SaveButton_Click() {
        try {
            boolean flag = false;
            List<String> macs = BaseFunction.GetMacAddress();
            for (Row item : db().list("RegisterInfo")) {
                String input = BaseFunction.DecryptByAES(BaseFunction.DecryptByAES(item.str("SecretInfo")));
                if (macs.contains(input)) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                throw new IllegalStateException("站点未激活");
            }
            if (db().first("Stations") == null) {
                throw new IllegalStateException("站点未创建");
            }
            Row emp = db().first("Employees");
            if (emp == null) {
                throw new IllegalStateException("员工未创建");
            }
            IniInfo.LoginName = emp.str("Name");
            IniInfo.LoginID = emp.i("EmployeeID");
            IniInfo.LoginType = 1;
            IniInfo.Auth = emp.str("Auth");
            SessionLogin();
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void NormalRegister_Load() {
        try {
            Row s = db().first("Stations");
            if (s != null) {
                IniInfo.StationID = s.i("StationID");
                IniInfo.Code = s.str("Code");
                IniInfo.CompanyID = Conv.toInt(s.get("SendMailServer"));
                IniInfo.StationName = s.str("Name");
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
