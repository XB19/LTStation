package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;

/** DevicePrint : lignes du ticket imprimé par les boîtiers (DevicePrintSet), envoyées aux boîtiers (message 1045). */
@SwWindow("NormalForm.DevicePrint")
public class DevicePrint extends SwForm {

    /** Contrôle de la fenêtre → colonne de DevicePrintSet. */
    private static final String[][] LINES = {
        {"FirstLine", "DeviceFirstLine"}, {"SecondLine", "DeviceSecondLine"}, {"ThirdLine", "DeviceThirdLine"}, {"FourthLine", "DeviceFouthLine"},
        {"FifthLine", "DeviceFifthLine"}, {"SixthLine", "DeviceSixthLine"}, {"NozLine", "DeviceNozID"}, {"AmountLine", "DeviceAmount"},
        {"VolumeLine", "DeviceVolume"}, {"PriceLine", "DevicePrice"}, {"EleventhLine", "DeviceSevenLine"}, {"TimeLine", "DeviceTime"},
        {"ThirteenthLine", "DeviceEighthLine"}, {"SerialNumberLine", "DeviceNinthLine"}, {"FifteenthLine", "DeviceTenthLine"},
    };

    public void SaveButton_Click() {
        try {
            Row d = db().first("DevicePrintSet");
            boolean exists = d != null;
            if (!exists) {
                d = Row.of("CreateTime", BaseFunction.ConvertDateTimeToInt());
            }
            for (String[] l : LINES) {
                d.put(l[1], c(l[0]).Text());
            }
            if (exists) {
                db().update("DevicePrintSet", d);
            } else {
                db().insert("DevicePrintSet", d);
            }
            NormalFunction.MainFormMessage.accept(1045, 0, 0);
            MessageBox(L("OperateSuccess"));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DevicePrint_Load() {
        try {
            Row d = db().first("DevicePrintSet");
            if (d != null) {
                for (String[] l : LINES) {
                    c(l[0]).Text(d.str(l[1]));
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
