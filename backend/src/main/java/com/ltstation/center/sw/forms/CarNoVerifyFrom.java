package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.engine.SupervisionFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.time.LocalDateTime;

/** CarNoVerifyFrom : contrôle d'un véhicule (supervision) et autorisation de remplissage sur un pistolet. */
@SwWindow("CarNoVerifyFrom")
public class CarNoVerifyFrom extends SwForm {

    public void AddButton_Click() {
        try {
            String carNo = c("CarNoBox").Text();
            int nozID = Conv.toInt(c("NozSelect").Text());
            String[] error = {""};
            Row start = new Row();
            if (!SupervisionFunction.FoRanCheckUp(carNo, start, error)) {
                throw new IllegalStateException(error[0]);
            }
            db().insert("CmdList", Row.of("CmdPlatType", 3, "CmdType", "63", "CreateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()),
                "Deal", 0, "Dowload", true, "StationID", IniInfo.StationID, "NozID", nozID, "CmdData", Wire.json(start)));
            MessageBox("操作完成");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CarNoVerifyFrom_Load() {
        c("NozSelect").DataSource(db().list("GunConfig"), "GUNCODE", "GUNCODE");
    }
}
