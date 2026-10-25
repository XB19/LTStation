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

/** CreateOilPrice : prix d'un carburant de la station (assistant d'installation). */
@SwWindow(value = "CreateOilPrice", setup = true)
public class CreateOilPrice extends SwForm {

    private final Row CustomerParam = SW.dto().make("CustomerParam");
    private Row stations;

    @Override
    protected void init() {
        stations = db().first("Stations");
    }

    public void ID_DropDown() {
        c("ID").DataSource(db().list("GradeSpecifics"), "Name", "GradeSpecificID");
    }

    public void ID_SelectionChangeCommitted() {
        Row g = c("ID").SelectedRow();
        c("Code").Text(g.get("Code"));
        c("OilTypeName").Text(g.get("Name"));
    }

    public void SaveButton_Click() {
        var tx = db().begin();
        try {
            if (c("Code").Text().isEmpty() || c("OilTypeName").Text().isEmpty() || c("OilPrice").Text().isEmpty() || c("DensityT").Text().isEmpty()) {
                throw new IllegalStateException(L("InformationLoss"));
            }
            String num = "^-?\\d+$|^(-?\\d+)(\\.\\d+)?$";
            if (c("OilPrice").Text().matches(num) && c("DensityT").Text().matches(num)) {
                if (new BigDecimal(c("OilPrice").Text()).signum() < 0) {
                    throw new IllegalStateException(L("FormatError"));
                }
                Row price = Row.of("Code", c("Code").Text(), "CurrentDensity", 0.0, "CurrentPPU", Conv.toDecimal(c("OilPrice").Text()),
                    "Newdensity", Conv.toDouble(c("DensityT").Text()), "NewPPU", Conv.toDecimal(c("OilPrice").Text()), "StationID", stations.get("StationID"),
                    "ValidateTime", Conv.toDateTime(c("ValidTime").Text().isEmpty() ? c("ValidTime").Value() : c("ValidTime").Text()));
                Row existing = db().first("PriceInfos", "{Code} = :c", p("c", price.get("Code")));
                BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpOilPrice", Wire.json(SW.dto().shape("PriceInfos", price)));
                if (existing == null) {
                    db().insert("PriceInfos", price);
                    Close();
                    tx.commit();
                    return;
                }
                throw new IllegalStateException(L("EnergyExist"));
            }
            throw new IllegalStateException(L("PriceFormatError"));
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    public void AddOiLType_Click() {
        ShowDialog("CreateOilType", r -> DowOilType());
    }

    private void DowOilType() {
        if (stations == null) {
            return;
        }
        var tx = db().begin();
        try {
            CustomerParam.put("Code", stations.get("Code"));
            CustomerParam.put("Start", 0);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetOilType", Wire.json(CustomerParam));
            if (r.Result()) {
                db().delete("GradeSpecifics", null, null);
                for (Row item : Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()))) {
                    db().insert("GradeSpecifics", item);
                }
            }
            tx.commit();
        } catch (RuntimeException e) {
            tx.commit();
        }
    }

    public void CanelButton_Click() {
        Close();
    }
}
