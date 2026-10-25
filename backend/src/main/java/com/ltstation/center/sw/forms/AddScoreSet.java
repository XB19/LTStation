package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/** AddScoreSet : nouvelle règle de points par carburant (envoyée à Center). */
@SwWindow("AddScoreSet")
public class AddScoreSet extends SwForm {

    @Override
    protected void init() {
        try {
            c("GradeType").DataSource(db().list("GradeSpecifics"), "Name", "GradeSpecificID");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void ConfirmBtn_Click() {
        try {
            BigDecimal result;
            try {
                result = new BigDecimal(c("ScoreRate").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("积分转换率输入错误");
            }
            int gradeId = c("GradeType").SelectedRow().i("GradeSpecificID");
            Row gs = SW.dto().make("GradeScore", "GradeID", gradeId, "OperateID", c("ScoreType").SelectedIndex(), "Score", result, "SOID", 2,
                "StationID", IniInfo.StationID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/ScoreRuleDeal", Wire.json(gs));
            if (r.Result()) {
                MessageBox("操作成功");
                Close("OK");
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
