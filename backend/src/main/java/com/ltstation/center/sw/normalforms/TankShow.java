package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwWebForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** TankShow : jauges des cuves du mode Normal (web/tank/tank.html), intégrée dans la fenêtre principale. */
@SwWindow("NormalForm.TankShow")
public class TankShow extends SwWebForm {

    public final List<Row> TankMessList = new CopyOnWriteArrayList<>();

    @Override
    protected String Url() {
        return "web/tank/tank.html";
    }

    @Override
    public void LoadHandler_OnLoadEnd() {
        ClearPendingScripts();
        Tank_Add();
    }

    private static String pct(BigDecimal part, BigDecimal total) {
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_EVEN).toPlainString();
    }

    private String labels() {
        return "','" + L("TotalTankHeightStr") + "','" + L("TotalTankVolumeStr") + "','" + L("CurrentHeightStr") + "','" + L("CurrentVolumeStr")
            + "','" + L("CurrentWaterStr") + "','" + L("CurrentTempStr") + "'";
    }

    public void Tank_ViewUpdate(int OldTank, int TankID, BigDecimal CurrentHeight, BigDecimal CurrentVol, BigDecimal CurrentWaterHeight,
        BigDecimal CurrentTemp) {
        try {
            Row tank = db().first("NormalTank", "{IsUsing} = true AND {TankNo} = :n", p("n", BaseFunction.SetStrLen(String.valueOf(TankID), 2)));
            if (tank == null) {
                return;
            }
            Row grade = db().first("NormalGrade", "{IsUsing} = true AND {ID} = :g", p("g", tank.get("GradeID")));
            if (grade != null) {
                BigDecimal height = tank.dec("TankHeight");
                ExecuteJavascript("TankUpdate('" + tank.str("ID") + "', '" + tank.str("ID") + "', '" + grade.str("GradeName") + "','"
                    + height.toPlainString() + "', '" + tank.dec("TankVolume").toPlainString() + "', '" + CurrentHeight.toPlainString() + "', '"
                    + CurrentVol.toPlainString() + "', '" + CurrentWaterHeight.toPlainString() + "', '" + CurrentTemp.toPlainString() + "','"
                    + pct(CurrentHeight, height) + "','" + pct(CurrentWaterHeight, height) + labels() + ")");
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void Tank_Clear() {
        ExecuteJavascript("ClearLoad()");
    }

    public void Tank_Add() {
        try {
            TankMessList.clear();
            for (Row item : db().list("NormalTank", "{IsUsing} = true ORDER BY {TankNo}", null)) {
                Row grade = db().first("NormalGrade", "{ID} = :g AND {IsUsing} = true", p("g", item.get("GradeID")));
                if (grade == null) {
                    continue;
                }
                TankMessList.add(Row.of("ID", item.get("ID"), "TankHeight", item.dec("TankHeight"), "TankVolume", item.dec("TankVolume"),
                    "CurrentHeight", item.dec("CurrentHeight"), "CurrentVolume", item.dec("CurrentVolume"),
                    "UpdateTime", BaseFunction.StampToDateTime(item.str("UpdateTime")).format(com.ltstation.center.legacy.Conv.STD),
                    "CurrentWater", item.dec("CurrentWater")));
                BigDecimal height = item.dec("TankHeight");
                ExecuteJavascript("CreateTank('" + item.str("ID") + "', '" + grade.str("GradeName") + "','" + height.toPlainString() + "','"
                    + item.dec("TankVolume").toPlainString() + labels() + ",'" + item.dec("CurrentHeight").toPlainString() + "','"
                    + item.dec("CurrentVolume").toPlainString() + "','" + item.dec("CurrentWater").toPlainString() + "','"
                    + item.dec("CurrentTemp").toPlainString() + "','" + pct(item.dec("CurrentHeight"), height) + "','"
                    + pct(item.dec("CurrentWater"), height) + "')");
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
