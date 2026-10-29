package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/** UpdateScoreSet(SD) : modification / suppression d'une règle de points (Center). */
@SwWindow("UpdateScoreSet")
public class UpdateScoreSet extends SwForm {

    private int SID;
    private int GID;
    private int OID;
    private BigDecimal SR = BigDecimal.ZERO;

    @Override
    protected void init() {
        SID = argInt("ID");
        LoadData();
    }

    private void LoadData() {
        try {
            Row obj = SW.dto().make("GradeScore", "ID", SID, "SOID", 3);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/ScoreRuleDeal", Wire.json(obj));
            if (r.Result()) {
                Row rate = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                GID = rate.i("OilTypeID");
                OID = rate.i("ScoreType");
                SR = Conv.toDecimal(rate.get("Rate"));
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadGradeType() {
        try {
            c("GradeType").DataSource(db().list("GradeSpecifics"), "Name", "GradeSpecificID");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void UpdateScoreSet_Load() {
        LoadGradeType();
        c("GradeType").SelectedValue(GID);
        c("ScoreType").SelectedIndex(OID);
        c("ScoreRate").Text(SR.toPlainString());
    }

    public void UpdateBtn_Click() {
        try {
            if (SID == 0) {
                throw new IllegalStateException("Aucun enregistrement de points");
            }
            BigDecimal result;
            try {
                result = new BigDecimal(c("ScoreRate").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Taux de conversion des points incorrect");
            }
            Row obj = SW.dto().make("GradeScore", "GradeID", c("GradeType").SelectedRow().i("GradeSpecificID"), "OperateID",
                c("ScoreType").SelectedIndex(), "Score", result, "StationID", IniInfo.StationID, "ID", SID, "SOID", 0);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/ScoreRuleDeal", Wire.json(obj));
            if (r.Result()) {
                MessageBox("Opération réussie");
                Close("OK");
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DelBtn_Click() {
        Row obj = SW.dto().make("GradeScore", "StationID", IniInfo.StationID, "ID", SID, "SOID", 1);
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/ScoreRuleDeal", Wire.json(obj));
        if (r.Result()) {
            MessageBox("Opération réussie");
            Close("OK");
            return;
        }
        throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
    }
}
