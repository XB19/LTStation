package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;
import java.math.BigDecimal;

/** PriceUpdate : modification d'un prix (argument ID), envoyée à Center et aux pistolets dès qu'elle est en vigueur (1027). */
@SwWindow(value = "NormalForm.PriceUpdate", setup = true)
public class PriceUpdate extends SwForm {

    public void PriceUpdate_Load() {
        try {
            ShowGradeList();
            Row info = db().first("NormalPriceInfo", "{ID} = :i", p("i", argInt("ID")));
            if (info == null) {
                throw new IllegalStateException(L("PriceExist"));
            }
            c("GradeSelect").SelectedValue(info.get("GradeID"));
            c("GradePrice").Text(Conv.netRound(info.dec("Price"), IniInfo.Pradio).toPlainString());
            c("ValidTime").Value(BaseFunction.StampToDateTime(info.str("ValidTime")));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            Close("Cancel");
        }
    }

    private void ShowGradeList() {
        try {
            c("GradeSelect").DataSource(db().list("NormalGrade", "{IsUsing} = true ORDER BY {ID}", null), "GradeName", "ID");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void SaveButton_Click() {
        try {
            int id = argInt("ID");
            BigDecimal result;
            try {
                result = new BigDecimal(c("GradePrice").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("PriceFormatError"));
            }
            if (result.signum() <= 0) {
                throw new IllegalStateException(L("PriceFormatError"));
            }
            if (!BaseFunction.ValidMessage(c("GradePrice").Text(), "^\\d+\\.?\\d{0," + IniInfo.Pradio + "}$")) {
                throw new IllegalStateException(L("PriceRadioError") + IniInfo.Pradio);
            }
            int gradeId = c("GradeSelect").SelectedRow().i("ID");
            long valid = BaseFunction.ConvertDateTimeToInt(c("ValidTime").Value());
            if (db().any("NormalPriceInfo", "{ID} <> :i AND {GradeID} = :g", p("i", id, "g", gradeId))) {
                throw new IllegalStateException(L("PriceReapt"));
            }
            Row info = db().first("NormalPriceInfo", "{ID} = :i", p("i", id));
            if (info == null) {
                throw new IllegalStateException(L("PriceExist"));
            }
            info.put("GradeID", gradeId);
            info.put("OldPrice", info.get("Price"));
            info.put("Price", result);
            info.put("ValidTime", valid);
            info.put("UpdateTime", BaseFunction.ConvertDateTimeToInt());
            db().update("NormalPriceInfo", info);
            BaseFunction.post("ServerDeal/UpdateNormalPrice", SW.dto().make("PriceDataShow", "StationID", IniInfo.StationID, "NewPPU", result,
                "CompanyID", IniInfo.CompanyID, "GradeSpecificID", gradeId, "ID", id, "ValidateTime", String.valueOf(valid)));
            if (BaseFunction.ConvertDateTimeToInt() >= valid) {
                NormalFunction.MainFormMessage.accept(1027, info.i("ID"), 0);
            }
            // SupervisionType 4 (eTIMS Kenya) : prix envoyé par BoxBase.kenyaDeal.saveItem, pas encore porté
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CanelButton_Click() {
        Close("Cancel");
    }
}
