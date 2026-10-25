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

/** CreateOilType : nouveau type de carburant sur Center (code proposé = dernier code + 1). */
@SwWindow(value = "CreateOilType", setup = true)
public class CreateOilType extends SwForm {

    private final Row CustomerParam = SW.dto().make("CustomerParam");

    @Override
    protected void init() {
        Row stations = db().first("Stations");
        CustomerParam.put("Code", stations != null ? stations.get("Code") : IniInfo.Code);
        c("OilCode").Text(DowOilType());
    }

    private String DowOilType() {
        int num = 1001;
        try {
            CustomerParam.put("Start", 0);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetOilType", Wire.json(CustomerParam));
            if (r.Result()) {
                List<Row> list = Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()));
                if (!list.isEmpty()) {
                    num = Conv.toInt(list.get(0).get("Code")) + 1;
                }
            }
            return String.valueOf(num);
        } catch (RuntimeException e) {
            return String.valueOf(num);
        }
    }

    public void CanelButton_Click() {
        Close();
    }

    public void SaveButton_Click() {
        var tx = db().begin();
        try {
            if (!c("OilCode").Text().isEmpty() && !c("OilName").Text().isEmpty()) {
                Row obj = SW.dto().make("GradeSpecifics", "Code", c("OilCode").Text(), "Name", c("OilName").Text());
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CreateOilType", Wire.json(obj));
                if (r.Result()) {
                    Close();
                    tx.commit();
                    return;
                }
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            throw new IllegalStateException(L("InformationLoss"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            tx.rollback();
        }
    }
}
