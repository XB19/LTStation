package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;
import java.math.BigDecimal;

/** TankCableStatue : départ d'un jaugeage de cuve (hauteur et volume de départ), signalé à la fenêtre principale (2049). */
@SwWindow("NormalForm.TankCableStatue")
public class TankCableStatue extends SwForm {

    public void skinButton1_Click() {
        Close("Cancel");
    }

    private static BigDecimal parse(String text, String error) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(error);
        }
    }

    public void QueryBtn_Click() {
        try {
            int tankId = Conv.toInt(c("TankSelect").SelectedValue());
            BigDecimal height = parse(c("StartHeight").Text(), L("CableHeightError"));
            BigDecimal volume = parse(c("StartVolume").Text(), L("CableVolumeError"));
            Row tank = db().first("NormalTank", "{ID} = :i", p("i", tankId));
            if (tank == null) {
                throw new IllegalStateException(L("TankMissing"));
            }
            if (db().any("TankCableCalculate", "{TankID} = :t", p("t", tankId))) {
                throw new IllegalStateException(L("CableExists"));
            }
            db().insert("TankCableCheck", Row.of("TankID", tankId, "TankName", tank.str("TankName"), "StartHeight", height, "StartVolume", volume,
                "CreateTime", BaseFunction.ConvertDateTimeToInt(), "CheckStatue", c("Checking").Checked() ? "Runing" : "Stop"));
            long id = Conv.toLong(db().insert("TankCableCalculate", Row.of("TankID", tankId, "TankSetID", Conv.toInt(tank.get("TankNo")),
                "CalculateIndex", -1, "CurrentHeight", height, "CurrentVolume", volume, "CreateTime", BaseFunction.ConvertDateTimeToInt())));
            NormalFunction.MainFormMessage.accept(2049, id, 0);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void TankCableStatue_Load() {
        try {
            c("TankSelect").DataSource(db().list("NormalTank", "{IsUsing} = true", null), "TankName", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
