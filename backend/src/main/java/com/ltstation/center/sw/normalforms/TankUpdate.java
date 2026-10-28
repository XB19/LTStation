package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.BaseInfo;
import com.ltstation.center.sw.normal.NormalEngine;
import com.ltstation.center.sw.normal.NormalFunction;
import java.math.BigDecimal;

/** TankUpdate : modification d'une cuve (argument ID), signalée à Center ; changement de produit repris sur ses pistolets. */
@SwWindow(value = "NormalForm.TankUpdate", setup = true)
public class TankUpdate extends SwForm {

    private static final String[][] FIELDS = {
        {"TankHeightMess", "TankHeight"}, {"TankVolumeMess", "TankVolume"}, {"OverHeightMess", "OverHeight"}, {"LowerHeightMess", "LowerHeight"},
        {"OverWaterMess", "OverWaterHeight"}, {"LowerWaterMess", "LowerWaterHeight"}, {"OverTempMess", "OverTemp"}, {"LowerTempMess", "LowerTemp"},
        {"OverImport", "OverImportLevel"},
    };

    public void TankUpdate_Load() {
        c("GradeSelect").DataSource(db().list("NormalGrade", "{IsUsing} = true ORDER BY {ID}", null), "GradeName", "ID");
        ShowTankMess();
    }

    private void ShowTankMess() {
        try {
            Row tank = db().first("NormalTank", "{ID} = :i", p("i", argInt("ID")));
            if (tank == null) {
                throw new IllegalStateException(L("TankEmpty"));
            }
            c("TankNameMess").Text(tank.str("TankName"));
            c("GradeSelect").SelectedValue(tank.get("GradeID"));
            for (String[] f : FIELDS) {
                c(f[0]).Text(tank.dec(f[1]).toPlainString());
            }
            c("TankNo").Text(tank.str("TankNo"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CanelButton_Click() {
        Close("Cancel");
    }

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
            int id = argInt("ID");
            String tankName = c("TankNameMess").Text();
            Row grade = c("GradeSelect").SelectedRow();
            if (tankName.isEmpty() || c("TankHeightMess").Text().isEmpty() || c("TankVolumeMess").Text().isEmpty()) {
                throw new IllegalStateException(L("InformationLoss"));
            }
            BigDecimal result = opt(c("TankHeightMess").Text(), L("TankHeightFormatError"));
            BigDecimal result2 = opt(c("TankVolumeMess").Text(), L("TankVolumeFormatError"));
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
            Row limits = Row.of("GradeID", grade.get("ID"), "LowerHeight", result4, "LowerTemp", result8, "LowerWaterHeight", result6,
                "OverHeight", result3, "OverTemp", result7, "OverWaterHeight", result5, "TankHeight", result, "TankVolume", result2,
                "OverImportLevel", result10, "UpdateTime", BaseFunction.ConvertDateTimeToInt());
            Row other = db().first("NormalTank", "({TankName} = :n OR {TankNo} = :no) AND {ID} <> :i", p("n", tankName, "no", tankNo, "i", id));
            if (other != null) {
                if (other.b("IsUsing")) {
                    throw new IllegalStateException(L("TankNoRepeat"));
                }
                // comme l'original : la cuve retirée de même nom ou numéro est réactivée avec ces valeurs
                other.putAll(limits);
                other.put("IsUsing", true);
                db().update("NormalTank", other);
                Close("OK");
                return;
            }
            Row tank = db().first("NormalTank", "{ID} = :i", p("i", id));
            if (tank == null) {
                throw new IllegalStateException(L("TankEmpty"));
            }
            Row obj = SW.dto().make("StationTankMess", "StationID", IniInfo.StationID, "CompanyID", IniInfo.CompanyID, "UpdateType", "Update",
                "NTankNo", result9, "OTankNo", Conv.toInt(tank.get("TankNo")), "TankName", tankName, "LowerHeight", result4, "LowerTemp", result8,
                "LowerWaterHeight", result6, "OverHeight", result3, "OverTemp", result7, "OverWaterHeight", result5, "TankHeight", result,
                "TankVolume", result2, "GradeID", grade.get("ID"), "OverImportLevel", result10);
            if (grade.i("ID") != tank.i("GradeID")) {
                Row info = db().first("NormalPriceInfo", "{GradeID} = :g", p("g", grade.get("ID")));
                if (info != null) {
                    db().exec(db().sql("NormalNoz", "update [NormalNoz] set {GradeID} = :g, {Price} = :p where {TankID} = :t"),
                        p("g", grade.get("ID"), "p", info.dec("Price"), "t", tank.i("ID")));
                    NormalFunction.MainFormMessage.accept(1027, info.i("ID"), 0);
                }
            }
            tank.putAll(limits);
            tank.put("TankName", tankName);
            tank.put("TankNo", tankNo);
            // SupervisionType 4 : article eTIMS (KenyaCode) par BoxBase.kenyaDeal.saveItem, pas encore porté
            db().update("NormalTank", tank);
            BaseFunction.post("ServerDeal/NormalTankUpdate", obj);
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
