package com.ltstation.center.sw.forms;

import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** SuperviseSelect : choix de la plateforme de supervision (aucune, QY, XJ). */
@SwWindow("SuperviseSelect")
public class SuperviseSelect extends SwForm {

    public void AccecptRadio_CheckedChanged() {
        switch (sender) {
            case "AccecptRadio" -> {
                IniInfo.SupervisionType = 1;
                c("groupBox1").Enabled(true);
            }
            case "RefusedRadio" -> {
                IniInfo.SupervisionType = 0;
                c("groupBox1").Enabled(false);
            }
            case "QYRadio" -> IniInfo.SupervisionType = 1;
            case "XJRadio" -> IniInfo.SupervisionType = 2;
            default -> {
            }
        }
        BaseFunction.SetConfigValue("SupervisionType", String.valueOf(IniInfo.SupervisionType));
    }

    public void SuperviseSelect_Load() {
        switch (IniInfo.SupervisionType) {
            case 0 -> {
                c("RefusedRadio").Checked(true);
                c("groupBox1").Enabled(false);
            }
            case 1 -> {
                c("AccecptRadio").Checked(true);
                c("groupBox1").Enabled(true);
                c("QYRadio").Checked(true);
            }
            case 2 -> {
                c("AccecptRadio").Checked(true);
                c("groupBox1").Enabled(true);
                c("XJRadio").Enabled(true);
                c("XJRadio").Checked(true);
            }
            default -> {
            }
        }
    }
}
