package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWebForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** ICTank(ID) : jauge d'une cuve (web/tank/tank.html). */
@SwWindow("ICTank")
public class ICTank extends SwWebForm {

    public int TankID;
    private String GradeName = "";
    private BigDecimal TankHeight = BigDecimal.ZERO;
    private BigDecimal TankVolume = BigDecimal.ZERO;

    @Override
    protected String Url() {
        return "web/tank/tank.html";
    }

    @Override
    protected void init() {
        TankID = argInt("ID");
        Poll(1000);
    }

    @Override
    public void LoadHandler_OnLoadEnd() {
        TankSet(TankID);
    }

    public void TankSet(int ID) {
        TankID = ID;
        Row tank = db().first("tankConfig", "{ID} = :id", p("id", TankID));
        if (tank != null) {
            TankHeight = Conv.toDecimal(tank.get("Height"));
            TankVolume = Conv.toDecimal(tank.get("Vol"));
            Row g = db().first("GradeSpecifics", "{Code} = :c", p("c", tank.str("OilCode")));
            if (g != null) {
                GradeName = g.str("Name");
            }
        }
        ExecuteJavascript("TankSet('" + GradeName + "','" + TankHeight.toPlainString() + "','" + TankVolume.toPlainString() + "')");
    }

    public void TankUpdate(BigDecimal Height, BigDecimal Volume, BigDecimal Temp, BigDecimal WHeight, BigDecimal WVolume) {
        try {
            BigDecimal num = Height.divide(TankHeight, 10, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal num2 = WHeight.divide(TankHeight, 10, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_EVEN);
            ExecuteJavascript("TankUpdate('" + num.toPlainString() + "', '" + Height.toPlainString() + "', '" + Volume.toPlainString() + "', '"
                + Temp.toPlainString() + "','" + num2.toPlainString() + "', '" + WHeight.toPlainString() + "', '')");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
