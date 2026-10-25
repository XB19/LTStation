package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** AddStock(TandID) : livraison de carburant dans une cuve (stock et mesure). */
@SwWindow("AddStock")
public class AddStock extends SwForm {

    private int TID;

    @Override
    protected void init() {
        TID = argInt("TandID");
    }

    public void AddStock_Load() {
        try {
            Row tank = db().first("tankConfig", "{ID} = :id", p("id", TID));
            if (tank == null) {
                throw new IllegalStateException(L("TankMissing"));
            }
            c("TankID").Text(String.valueOf(TID));
            c("GradeName").Text(BaseFunction.GetOilNameByCode(tank.i("OilCode")));
            Row status = db().first("TankStatus", "{ID} = :id", p("id", TID));
            c("CurrentStock").Text(status == null ? "0" : Conv.toStr(status.get("OilVol")));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Confirm_Click() {
        var tx = db().begin();
        try {
            if (c("TankID").Text().isEmpty() || c("GradeName").Text().isEmpty() || c("CurrentStock").Text().isEmpty()) {
                throw new IllegalStateException(L("InformationLost"));
            }
            BigDecimal result;
            try {
                result = new BigDecimal(c("StockValue").Text().trim());
            } catch (NumberFormatException e) {
                result = BigDecimal.ZERO;
            }
            Row tank = db().first("tankConfig", "{ID} = :id", p("id", TID));
            if (tank == null) {
                throw new IllegalStateException(L("TankMissing"));
            }
            Row status = db().first("TankStatus", "{ID} = :id", p("id", TID));
            LocalDateTime now = LocalDateTime.now();
            if (status == null) {
                status = Row.of("ID", TID, "OilCode", tank.get("OilCode"), "OilVol", result.doubleValue(), "Time", now);
                db().insert("TankStatus", status);
            } else {
                status.put("OilVol", Conv.toDouble(status.get("OilVol")) + result.doubleValue());
                status.put("Time", now);
                db().update("TankStatus", status);
            }
            db().insert("TankMeasurements", Row.of("EmployeeID", IniInfo.LoginID, "RecSeq", 1, "SampOilDensity", result.doubleValue(),
                "OilVolume", status.get("OilVol"), "Time", now, "Upload", 0, "Shift", now.format(DateTimeFormatter.ofPattern("yyyyMMddHH")),
                "TankCode", TID));
            tx.commit();
            Close("OK");
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }
}
