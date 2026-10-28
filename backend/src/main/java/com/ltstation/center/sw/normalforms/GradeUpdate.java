package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalEngine;

/** GradeUpdate : nom d'un produit (argument ID), repris sur les pistolets et envoyé à Center. */
@SwWindow(value = "NormalForm.GradeUpdate", setup = true)
public class GradeUpdate extends SwForm {

    public void GradeUpdate_Load() {
        try {
            Row grade = db().first("NormalGrade", "{ID} = :i", p("i", argInt("ID")));
            if (grade == null) {
                throw new IllegalStateException(L("GradeNone"));
            }
            c("GradeName").Text(grade.str("GradeName"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            Close("Cancel");
        }
    }

    public void SaveButton_Click() {
        try {
            int id = argInt("ID");
            String name = c("GradeName").Text();
            if (name.isEmpty()) {
                throw new IllegalStateException(L("GradeEmpty"));
            }
            if (db().any("NormalGrade", "{ID} <> :i AND {GradeName} = :n", p("i", id, "n", name))) {
                throw new IllegalStateException(L("EnergyExist"));
            }
            Row grade = db().first("NormalGrade", "{ID} = :i", p("i", id));
            grade.put("GradeName", name);
            db().update("NormalGrade", grade);
            for (Row item : db().list("NormalNoz", "{GradeID} = :g", p("g", id))) {
                NormalEngine.UpdateNozMess(item.i("NozID"), item.str("NozID"), name, 9);
            }
            BaseFunction.post("ServerDeal/UpdateNormalGrade", SW.dto().make("GradeSpecifics", "GradeSpecificID", id, "Name", name, "Code", grade.str("Code")));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CanelButton_Click() {
        Close("Cancel");
    }
}
