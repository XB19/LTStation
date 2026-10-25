package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** StockImport(TankID) : mouvements de stock d'une cuve (15 par page) et livraison. */
@SwWindow("StockImport")
public class StockImport extends SwForm {

    public int TID;
    public int TP;

    @Override
    protected void init() {
        TID = argInt("TankID");
    }

    public void StockImport_Load() {
        try {
            Row tank = db().first("tankConfig", "{ID} = :id", p("id", TID));
            if (tank == null) {
                throw new IllegalStateException(L("TankMissing"));
            }
            c("TankID").Text(String.valueOf(TID));
            c("GradeName").Text(BaseFunction.GetOilNameByCode(Conv.toInt(tank.get("OilCode"))));
            GetList(1);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void GetList(int pageIndex) {
        try {
            Sugar.Page page = db().page("TankMeasurements", "{TankCode} = :t", p("t", TID), "{Time} DESC", pageIndex, 15);
            c("TPage").Text(pageIndex + "/" + page.totalPage());
            c("CurrentPage").Text(String.valueOf(pageIndex));
            TP = page.totalPage();
            ShowData(page.rows());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowData(List<Row> list) {
        try {
            g("TransactionView").RowsClear();
            for (Row item : list) {
                g("TransactionView").RowsAdd(TID, ShowOperateByRecSeq(item.i("RecSeq")), Conv.num(Conv.round(Conv.toDouble(item.get("SampOilDensity")), 3)),
                    Conv.num(Conv.round(Conv.toDouble(item.get("OilVolume")), 3)), Conv.toDateTime(item.get("Time")).format(Conv.STD));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private String ShowOperateByRecSeq(int recSeq) {
        return switch (recSeq) {
            case 0 -> L("OilOutput");
            case 1 -> L("OilStock");
            default -> "";
        };
    }

    public void AddImoort_Click() {
        ShowDialog("AddStock", r -> {
            if (r.ok()) {
                GetList(1);
            }
        }, "TandID", TID);
    }

    public void DirectButton_Click() {
        try {
            int result = Integer.parseInt(c("CurrentPage").Text().trim());
            if (result < 0 || result > TP) {
                return;
            }
            GetList(result);
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
