package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** StockManager : stock des cuves (5 par page) ; double-clic = livraison si la gestion du stock est permise. */
@SwWindow("StockManager")
public class StockManager extends SwForm {

    private int TP;

    public void StockManager_Load() {
        GetList(1);
    }

    private void GetList(int PageIndex) {
        try {
            String sql = "select {tankConfig.ID} AS ID, {tankConfig.OilCode} AS OilCode, {tankConfig.Height} AS Height, {tankConfig.Vol} AS Vol,"
                + " {TankStatus.OilHeight} AS OilHeight, {TankStatus.OilVol} AS OilVol from [tankConfig] left join [TankStatus]"
                + " on {tankConfig.ID} = {TankStatus.ID}";
            Sugar.Page page = db().pageQuery(sql, null, "ID", PageIndex, 5);
            c("TPage").Text(PageIndex + "/" + page.totalPage());
            c("PageText").Text(String.valueOf(PageIndex));
            TP = page.totalPage();
            ShowData(page.rows());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static BigDecimal r3(Object v) {
        return BigDecimal.valueOf(Conv.toDouble(v)).setScale(3, RoundingMode.HALF_EVEN).stripTrailingZeros();
    }

    private void ShowData(List<Row> list) {
        g("TransactionView").RowsClear();
        for (Row item : list) {
            g("TransactionView").RowsAdd(item.get("ID"), BaseFunction.GetOilNameByCode(Conv.toInt(item.get("OilCode"))), item.get("Height"),
                r3(item.get("Vol")).toPlainString(), r3(item.get("OilVol")).toPlainString());
        }
    }

    public void DirectButton_Click() {
        try {
            int result;
            try {
                result = Integer.parseInt(c("PageText").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("PageError"));
            }
            if (result < 0 || result > TP) {
                throw new IllegalStateException(L("PageError"));
            }
            GetList(result);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void TransactionView_DoubleClick() {
        try {
            if (IniInfo.NonLevel) {
                int index = g("TransactionView").CurrentRowIndex();
                if (index < 0) {
                    throw new IllegalStateException("Object reference not set to an instance of an object.");
                }
                int tank = Integer.parseInt(Conv.toStr(g("TransactionView").Cell(index, "TankID")));
                ShowDialog("StockImport", r -> {
                    if (r.ok()) {
                        GetList(1);
                    }
                }, "TankID", tank);
                return;
            }
            throw new IllegalStateException(L("NotAllowUseStock"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
