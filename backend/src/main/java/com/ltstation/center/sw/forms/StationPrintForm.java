package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.time.LocalDateTime;

/** StationPrintForm : six lignes d'en-tête des tickets, envoyées à chaque pompe (commande 64). */
@SwWindow("StationPrintForm")
public class StationPrintForm extends SwForm {

    private static final String[] LINES = {"First", "Second", "Third", "Fourth", "Fiveth", "Sixth"};

    @Override
    protected void init() {
        Row p = db().first("StationMachinePrint");
        if (p == null || p.str("PrintData").isEmpty()) {
            return;
        }
        String[] array = p.str("PrintData").split("\\|", -1);
        for (int i = 0; i < array.length && i < 6; i++) {
            c(LINES[i]).Text(array[i]);
        }
    }

    public void SaveButton_Click() {
        String printMessage = GetPrintMessage();
        for (Row gun : db().list("GunConfig")) {
            db().insert("CmdList", Row.of("CmdPlatType", 3, "CmdData", printMessage, "CmdType", "64", "Deal", 0, "StationID", IniInfo.StationID,
                "NozID", gun.get("GUNCODE"), "CreateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now())));
        }
        StringBuilder t7 = new StringBuilder();
        for (String n : LINES) {
            if (!c(n).Text().isEmpty()) {
                t7.append(c(n).Text()).append('|');
            }
        }
        String data = t7.length() > 0 ? t7.substring(0, t7.length() - 1) : "";
        Row p = db().first("StationMachinePrint");
        if (p != null) {
            p.put("PrintData", data);
            db().update("StationMachinePrint", p);
        } else {
            db().insert("StationMachinePrint", Row.of("PrintData", data));
        }
        MessageBox("Success");
        Close("OK");
    }

    private String GetPrintMessage() {
        StringBuilder sb = new StringBuilder();
        for (String n : LINES) {
            StringBuilder t = new StringBuilder(c(n).Text());
            while (t.length() < 31) {
                t.append(' ');
            }
            sb.append(t);
        }
        return sb.toString();
    }
}
