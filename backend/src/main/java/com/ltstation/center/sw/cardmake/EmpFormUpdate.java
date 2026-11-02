package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** EmpFormUpdate : employé et droits de CardMake (cases numérotées 1 à 12 dans IssueAuth). */
@SwWindow("CardMake.EmpFormUpdate")
public class EmpFormUpdate extends CardMakeForm {

    private static final List<String> CHECKS = List.of("CardKeyCheck", "CardBuildCheck", "CardUpdateCheck", "CardTransformCheck", "DepositCheck", "ViewReportCheck",
        "EmpManagerCheck", "ReverseCheck", "CardReplaceCheck", "PasswordChangeCheck", "CardStatueCheck", "UserDepositCheck");

    private boolean exists() {
        return arg("EmployeeID") != null;
    }

    public void EmpFormUpdate_Load() {
        c("StationNameBox").Text(CM.StationName);
        if (!exists()) {
            return;
        }
        c("NameBox").Text(argStr("Name"));
        c("PhoneBox").Text(argStr("Phone"));
        c("PasswordBox").Text(argStr("Password"));
        CHECKS.subList(0, 7).forEach(n -> c(n).Checked(false));
        for (String a : argStr("IssueAuth").split(",")) {
            if (a.matches("[0-9]+") && Integer.parseInt(a) >= 1 && Integer.parseInt(a) <= CHECKS.size()) {
                c(CHECKS.get(Integer.parseInt(a) - 1)).Checked(true);
            }
        }
    }

    public void ConfirmBtn_Click() {
        try {
            StringBuilder text = new StringBuilder();
            for (String n : Controls("groupBox2")) {
                if (c(n).Checked() && CHECKS.contains(n)) {
                    text.append(CHECKS.indexOf(n) + 1).append(',');
                }
            }
            Row e = Row.of("Name", c("NameBox").Text(), "StationID", CM.StationID, "Phone", c("PhoneBox").Text(), "Password", c("PasswordBox").Text(),
                "IssueAuth", text.toString());
            if (exists()) {
                e.put("EmployeeID", argInt("EmployeeID"));
            }
            send("ExtendEmployeeUpdate", e);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
