package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.BaseInfo;
import com.ltstation.center.sw.normal.NormalEngine;
import java.math.BigDecimal;

/** TankAdd : nouvelle cuve (ou réactivation d'une cuve retirée de même nom), signalée à Center et aux ports. */
@SwWindow(value = "NormalForm.TankAdd", setup = true)
public class TankAdd extends SwForm {

    public void TankAdd_Load() {
        ShowGradeList();
    }

    private void ShowGradeList() {
        c("GradeSelect").DataSource(db().list("NormalGrade", "{IsUsing} = true ORDER BY {ID}", null), "GradeName", "ID");
    }

    public void CanelButton_Click() {
        Close("Cancel");
    }

    /** decimal.TryParse : texte vide = 0, texte invalide = erreur. */
    private static BigDecimal opt(String text, String error) {
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(error);
        }
    }

    public void SaveButton_Click() {
        try {
            String tankName = c("TankNameMess").Text();
            Row grade = c("GradeSelect").SelectedRow();
            String height = c("TankHeightMess").Text();
            String volume = c("TankVolumeMess").Text();
            if (tankName.isEmpty() || height.isEmpty() || volume.isEmpty()) {
                throw new IllegalStateException(L("InformationLoss"));
            }
            BigDecimal result = opt(height, L("TankHeightFormatError"));
            BigDecimal result2 = opt(volume, L("TankVolumeFormatError"));
            BigDecimal result3 = opt(c("OverHeightMess").Text(), L("TankHeightLevelAlarmFormatError"));
            BigDecimal result4 = opt(c("LowerHeightMess").Text(), L("TankLowLevelAlarmFormatError"));
            BigDecimal result5 = opt(c("OverWaterMess").Text(), L("WaterHeightFormatError"));
            BigDecimal result6 = opt(c("LowerWaterMess").Text(), L("WaterLowFormatError"));
            BigDecimal result7 = opt(c("OverTempMess").Text(), L("HighTempFormatError"));
            BigDecimal result8 = opt(c("LowerTempMess").Text(), L("LowTempFormatError"));
            String tankNo = c("TankNo").Text();
            if (tankNo.isEmpty()) {
                throw new IllegalStateException(L("TankNoEmpty"));
            }
            int result9;
            try {
                result9 = Integer.parseInt(tankNo.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("TankNoFormatError"));
            }
            if (result9 <= 0) {
                throw new IllegalStateException(L("TankNoFormatError"));
            }
            BigDecimal result10 = opt(c("OverImport").Text(), L("OverImprotVolumeError"));
            if (db().any("NormalTank", "{TankNo} = :n", p("n", tankNo))) {
                throw new IllegalStateException(L("TankNoRepeat"));
            }
            Row values = Row.of("GradeID", grade.get("ID"), "LowerHeight", result4, "LowerTemp", result8, "LowerWaterHeight", result6,
                "OverHeight", result3, "OverTemp", result7, "OverWaterHeight", result5, "TankHeight", result, "TankVolume", result2, "TankNo", tankNo,
                "OverImportLevel", result10, "UpdateTime", BaseFunction.ConvertDateTimeToInt());
            Row old = db().first("NormalTank", "{TankName} = :n", p("n", tankName));
            if (old != null) {
                if (old.b("IsUsing")) {
                    throw new IllegalStateException(L("TankNameRepeat"));
                }
                old.putAll(values);
                old.put("IsUsing", true);
                db().update("NormalTank", old);
                Close("OK");
                return;
            }
            Row tank = values.copy();
            tank.put("CreateTime", BaseFunction.ConvertDateTimeToInt());
            tank.put("IsUsing", true);
            tank.put("TankName", tankName);
            BaseFunction.post("ServerDeal/NormalTankUpdate", SW.dto().make("StationTankMess", "StationID", IniInfo.StationID, "CompanyID", IniInfo.CompanyID,
                "UpdateType", "Add", "NTankNo", result9, "LowerHeight", result4, "LowerTemp", result8, "LowerWaterHeight", result6, "OverHeight", result3,
                "OverTemp", result7, "OverWaterHeight", result5, "TankHeight", result, "TankName", tankName, "TankVolume", result2,
                "GradeID", grade.get("ID"), "OverImportLevel", result10));
            if (IniInfo.SupervisionType == 4) {
                String code = KenyaItems.newTankItem(grade);
                if (code != null) {
                    tank.put("KenyaCode", code);
                }
            }
            db().insert("NormalTank", tank);
            if (IniInfo.WifiMode == 1) {
                BaseInfo.ResetTank(new String[1]);
            } else {
                NormalEngine.UpdateNozMess(0, "0", "0", 10);
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
