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
import java.util.List;

/** ScoreDeal : règles de points de la compagnie (Center). */
@SwWindow("ScoreDeal")
public class ScoreDeal extends SwForm {

    @Override
    protected void init() {
        LoadData();
    }

    private void LoadData() {
        try {
            Row listPage = SW.dto().make("ListPage", "CompanyID", IniInfo.CompanyID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetScroeList", Wire.json(listPage));
            if (r.Result()) {
                ShowData(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())));
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void NextButton_Click() {
        ShowDialog("AddScoreSet", r -> {
            if (r.ok()) {
                LoadData();
            }
        });
    }

    private void ShowData(List<Row> list) {
        try {
            g("TransactionView").RowsClear();
            for (Row item : list) {
                String value = item.i("ScoreType") == 1 ? "Litres" : "Montant";
                g("TransactionView").RowsAdd(item.get("ID"), BaseFunction.GetOilNameByID(item.i("OilTypeID")), value, item.get("Rate"));
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void TransactionView_CellDoubleClick() {
        try {
            ShowDialog("UpdateScoreSet", r -> {
                if (r.ok()) {
                    LoadData();
                }
            }, "ID", Conv.toInt(g("TransactionView").Cell(e.i("RowIndex"), 0)));
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
