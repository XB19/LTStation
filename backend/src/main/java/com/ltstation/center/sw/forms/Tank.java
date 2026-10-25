package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWebForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Tank : jauges des cuves (web/tank/tank.html), intégrée dans la fenêtre principale et mise à jour par la sonde. */
@SwWindow("Tank")
public class Tank extends SwWebForm {

    public int TankID;
    public String TankNo = "";
    public String UpdateTime = "";
    public String TankName = "";
    private String GradeName = "";
    private BigDecimal TankHeight = BigDecimal.ZERO;
    private BigDecimal TankVolume = BigDecimal.ZERO;
    public final List<Row> TankMessList = new ArrayList<>();

    @Override
    protected String Url() {
        return "web/tank/tank.html";
    }

    @Override
    protected void init() {
        Poll(1000);
    }

    @Override
    public void LoadHandler_OnLoadEnd() {
        for (Row item : db().list("tankConfig", "ORDER BY {ID}", null)) {
            Row g = db().first("GradeSpecifics", "{Code} = :c", p("c", item.str("OilCode")));
            if (g != null) {
                TankMessList.add(Row.of("ID", item.get("ID"), "TankHeight", Conv.toDecimal(item.get("Height")), "TankVolume",
                    Conv.toDecimal(item.get("Vol")), "CurrentHeight", BigDecimal.ZERO, "CurrentVolume", BigDecimal.ZERO, "UpdateTime",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH"))));
                ExecuteJavascript("CreateTank('" + item.str("ID") + "', '" + g.str("Name") + "','" + item.str("Height") + "','" + item.str("Vol") + "','"
                    + L("TotalTankHeightStr") + "','" + L("TotalTankVolumeStr") + "','" + L("CurrentHeightStr") + "','" + L("CurrentVolumeStr") + "','"
                    + L("CurrentWaterStr") + "','" + L("CurrentTempStr") + "')");
            }
        }
    }

    public void TankSet(int ID) {
        TankID = ID;
        Row tank = db().first("NormalTank", "{ID} = :id", p("id", TankID));
        if (tank != null) {
            TankNo = tank.str("TankNo");
            TankName = tank.str("TankName");
            TankHeight = Conv.toDecimal(tank.get("TankHeight"));
            TankVolume = Conv.toDecimal(tank.get("TankVolume"));
            Row grade = db().first("NormalGrade", "{ID} = :id", p("id", tank.get("GradeID")));
            if (grade != null) {
                GradeName = grade.str("GradeName");
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

    /** Mise à jour d'une jauge (numéro d'ordre de la cuve, hauteur, volume, eau, température). */
    public void TankUpdateView(int OldTank, int TankID, BigDecimal CurrentHeight, BigDecimal CurrentVolume, BigDecimal CurrentWaterHeight,
                               BigDecimal CurrentTemp) {
        List<Row> tanks = db().list("tankConfig");
        if (tanks.size() >= OldTank && OldTank >= 1) {
            Row t = tanks.get(OldTank - 1);
            Row g = db().first("GradeSpecifics", "{Code} = :c", p("c", t.str("OilCode")));
            if (g != null) {
                BigDecimal vol = Conv.toDecimal(t.get("Vol"));
                BigDecimal height = Conv.toDecimal(t.get("Height"));
                BigDecimal num = vol.signum() == 0 ? BigDecimal.ZERO
                    : CurrentVolume.divide(vol, 10, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_EVEN);
                BigDecimal num2 = height.signum() == 0 ? BigDecimal.ZERO
                    : CurrentWaterHeight.divide(height, 10, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_EVEN);
                ExecuteJavascript("TankUpdate('" + OldTank + "', '" + TankID + "', '" + g.str("Name") + "','" + t.str("Height") + "', '" + t.str("Vol")
                    + "', '" + CurrentHeight.toPlainString() + "', '" + CurrentVolume.toPlainString() + "', '" + CurrentWaterHeight.toPlainString()
                    + "', '" + CurrentTemp.toPlainString() + "','" + num.toPlainString() + "','" + num2.toPlainString() + "','" + L("TotalTankHeightStr")
                    + "','" + L("TotalTankVolumeStr") + "','" + L("CurrentHeightStr") + "','" + L("CurrentVolumeStr") + "','" + L("CurrentWaterStr")
                    + "','" + L("CurrentTempStr") + "')");
            }
        }
    }
}
