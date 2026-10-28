package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** GradeAdd : nouveau produit, créé d'abord sur Center (identifiant et code donnés par Center). */
@SwWindow(value = "NormalForm.GradeAdd", setup = true)
public class GradeAdd extends SwForm {

    public void SaveButton_Click() {
        try {
            String name = c("GradeName").Text();
            if (name.isEmpty()) {
                throw new IllegalStateException(L("GradeEmpty"));
            }
            CustomerRespone r = BaseFunction.post("ServerDeal/UpdateNormalGrade", SW.dto().make("GradeSpecifics", "Name", name, "APPNumber", IniInfo.CompanyID));
            if (!r.Result()) {
                throw new IllegalStateException(r.Mess());
            }
            Row grade = db().first("NormalGrade", "{GradeName} = :n", p("n", name));
            if (grade != null) {
                if (grade.b("IsUsing")) {
                    throw new IllegalStateException(L("EnergyExist"));
                }
                grade.put("IsUsing", true);
                db().update("NormalGrade", grade);
            } else {
                Row spec = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                db().insert("NormalGrade", Row.of("GradeName", spec.str("Name"), "Createtime", BaseFunction.ConvertDateTimeToInt(),
                    "UpdateTime", BaseFunction.ConvertDateTimeToInt(), "Code", spec.str("Code"), "ID", spec.i("GradeSpecificID"), "IsUsing", true));
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CanelButton_Click() {
        Close("Cancel");
    }
}
