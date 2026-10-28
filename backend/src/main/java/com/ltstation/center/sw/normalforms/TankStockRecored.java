package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.util.List;

/** TankStockRecored : dépotages d'une cuve sur une période (NormalTankStock) et volume total. */
@SwWindow("NormalForm.TankStockRecored")
public class TankStockRecored extends SwForm {

    public void TankStockRecored_Load() {
        try {
            c("TankSelect").DataSource(db().list("NormalTank", "ORDER BY {ID}", null), "TankName", "ID");
            TankSelect_SelectedIndexChanged();
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void TankSelect_SelectedIndexChanged() {
        try {
            Row tank = c("TankSelect").SelectedRow();
            if (tank != null) {
                c("TankIDBox").Text(tank.str("TankNo"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void QueryBtn_Click() {
        try {
            long start = BaseFunction.ConvertDateTimeToInt(c("StartTimeSelect").Value());
            long end = BaseFunction.ConvertDateTimeToInt(c("EndTimeSelect").Value());
            int tankId = c("TankSelect").SelectedRow().i("ID");
            List<Row> stocks = db().list("NormalTankStock", "{TankID} = :t AND {CreateTime} >= :s AND {CreateTime} <= :e ORDER BY {CreateTime} DESC",
                p("t", tankId, "s", start, "e", end));
            BigDecimal sum = stocks.stream().map(s -> s.dec("StockVolume")).reduce(BigDecimal.ZERO, BigDecimal::add);
            c("SumVolume").Text(sum.toPlainString());
            ShowData(stocks);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowData(List<Row> stocks) {
        try {
            g("StockView").RowsClear();
            for (Row s : stocks) {
                g("StockView").RowsAdd(s.str("TankName"), s.get("TankID"), s.get("StockVolume"), s.get("StartVolume"), s.get("FinalVolume"),
                    BaseFunction.StampToDateTime(s.str("StartTime")).format(Conv.STD), BaseFunction.StampToDateTime(s.str("EndTime")).format(Conv.STD));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
