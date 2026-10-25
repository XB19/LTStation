package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.MultiLanguage;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/**
 * StationCreate : assistant d'installation (station, carburants, cuves, ports, pistolets,
 * employés, clés, activation). Chaque étape exige la précédente, comme l'original.
 */
@SwWindow(value = "StationCreate", setup = true)
public class StationCreate extends SwForm {

    private String Curbutton;

    private boolean none(String table) {
        return db().first(table) == null;
    }

    /** Un seul gestionnaire pour tous les boutons de la barre d'outils (sender = bouton). */
    public void OilPrice_Click() {
        c(Curbutton == null ? "OilPrice" : Curbutton).Checked(false);
        c(sender).Checked(true);
        Curbutton = sender;
        switch (Curbutton) {
            case "OilPrice" -> ShowDialog("BaseMessage", r -> { }, "ID", 0);
            case "CardMake" -> {
                if (none("Stations")) {
                    MessageBox(L("PlSCreateStation"));
                } else {
                    ShowDialog("OilTypeAndPrice", r -> { }, "ID", 1);
                }
            }
            case "CardLost" -> {
                if (none("ComConfig")) {
                    MessageBox(L("PlsCreatePort"));
                } else {
                    ShowDialog("StationNozzleSet", r -> { }, "ID", 1);
                }
            }
            case "CardUpDate" -> {
                if (none("PriceInfos")) {
                    MessageBox(L("PlSCreateOilType"));
                } else {
                    ShowDialog("StationTankList", r -> { }, "ID", 1);
                }
            }
            case "CardChange" -> {
                if (none("GunConfig")) {
                    MessageBox(L("PlsCreateNoz"));
                } else {
                    ShowDialog("CreateEmployee", r -> { }, "ID", 0);
                }
            }
            case "PortSet" -> {
                if (none("tankConfig")) {
                    MessageBox(L("PlsCreateTank"));
                } else {
                    ShowDialog("StationPort", r -> { }, "ID", 1);
                }
            }
            case "Deposit" -> {
                if (none("Employees")) {
                    MessageBox(L("PlsCreateEmp"));
                } else {
                    ShowDialog("KeyMainBoard", r -> { }, "ID", 0);
                }
            }
            case "TransactionSearch" -> ShowDialog("Register", r -> { });
            default -> {
            }
        }
    }

    public void SaveBtn_Click() {
        try {
            if (none("Stations") || none("PriceInfos")) {
                throw new IllegalStateException(L("PlSCreateStation"));
            }
            if (none("tankConfig")) {
                throw new IllegalStateException(L("PlsCreateTank"));
            }
            if (none("ComConfig")) {
                throw new IllegalStateException(L("PlsCreatePort"));
            }
            if (none("GunConfig")) {
                throw new IllegalStateException(L("PlsCreateNoz"));
            }
            Row employees = db().first("Employees");
            if (employees == null) {
                throw new IllegalStateException(L("PlsCreateEmp"));
            }
            IniInfo.LoginName = employees.s("Name");
            IniInfo.LoginID = employees.i("EmployeeID");
            IniInfo.LoginType = 1;
            Row registerInfo = db().first("RegisterInfo");
            if (registerInfo == null) {
                throw new IllegalStateException(L("SystemNotActive"));
            }
            String input = BaseFunction.DecryptByAES(BaseFunction.DecryptByAES(registerInfo.s("SecretInfo")));
            List<String> macs = BaseFunction.GetMacAddress();
            if (macs == null || !macs.contains(input)) {
                throw new IllegalStateException(L("SystemActiveError"));
            }
            SessionLogin();
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ChineseText_Click() {
        if ("EnglishText".equals(sender)) {
            IniInfo.Language = "en-US";
            MultiLanguage.languageChange(IniInfo.Language);
            ChangeLanguage(1);
        } else if ("ChineseText".equals(sender)) {
            IniInfo.Language = "zh-CN";
            MultiLanguage.languageChange(IniInfo.Language);
            ChangeLanguage(0);
        }
    }

    private void ChangeLanguage(int id) {
        String[][] zh = {{"LanguageSwitch", "语言切换"}, {"ChineseText", "中文"}, {"EnglishText", "英语"}, {"OilPrice", "创建站点信息"},
            {"CardMake", "能源设置"}, {"CardUpDate", "罐信息配置"}, {"PortSet", "端口配置"}, {"CardLost", "枪配置"}, {"CardChange", "员工配置"},
            {"Deposit", "密钥更改"}, {"TransactionSearch", "站点激活"}};
        String[][] en = {{"LanguageSwitch", "Language"}, {"ChineseText", "Chinese"}, {"EnglishText", "English"},
            {"OilPrice", "Create Station Information"}, {"CardMake", "Create Fuel Information"}, {"CardUpDate", "Create Tank Information"},
            {"PortSet", "COM Port Configuration"}, {"CardLost", "Nozzle Configuration"}, {"CardChange", "Create Employee"},
            {"Deposit", "Key Change"}, {"TransactionSearch", "Station Activation"}};
        Title(id == 0 ? "创建站点" : "Create Station");
        for (String[] t : id == 0 ? zh : en) {
            c(t[0]).Text(t[1]);
        }
    }

    public void StationCreate_Load() {
        try {
            Row stations = db().first("Stations");
            if (stations != null) {
                IniInfo.Code = stations.s("Code");
                IniInfo.StationID = stations.i("StationID");
                IniInfo.StationName = stations.s("Name");
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
