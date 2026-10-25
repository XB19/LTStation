package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.engine.SupervisionFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** CarGroupAdd : création d'un groupe de véhicules sur Center (et sur la plateforme XJ si elle est active). */
@SwWindow("CarGroupAdd")
public class CarGroupAdd extends SwForm {

    public void AddButton_Click() {
        try {
            String text = c("GroupNameBox").Text();
            String text2 = c("GroupAddressBox").Text();
            String text3 = c("GroupCreditBox").Text();
            if (text.isEmpty() || text2.isEmpty() || text3.isEmpty()) {
                throw new IllegalStateException("信息缺失,请补充");
            }
            long now = BaseFunction.ConvertDateTimeToInt(LocalDateTime.now());
            Row obj = SW.dto().make("CarGroupRequest", "CarGroupName", text, "CarGroupAddress", text2, "CarGroupCreditCode", text3,
                "CreateTime", now, "UpdateTime", now, "Balance", BigDecimal.ZERO, "UpdateType", "Add", "Phone", c("GroupPhoneBox").Text(),
                "EmpID", IniInfo.LoginID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CarGroupUpdate", Wire.json(obj));
            if (r.Result()) {
                obj = SW.dto().shape("CarGroupRequest", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
                SupervisionFunction.FoRan ret = new SupervisionFunction.FoRan(0, "", "");
                if (IniInfo.SupervisionType == 2) {
                    Row obj2 = Row.of("useCompanyName", text, "isEnterprise", 1, "useCompanyAddress", text2, "useCompanyIdentityCard", text3,
                        "regionCode", "440604", "tel", c("GroupPhoneBox").Text());
                    ret = SupervisionFunction.PostJson(IniInfo.FoRanUrl + "/sj-business/sjuseunit/v1/data", Wire.json(obj2));
                }
                if (ret.code() == 0) {
                    db().insert("CarGroup", Row.of("ID", obj.get("ID"), "Balance", obj.get("Balance"), "CarGroupAddress", obj.get("CarGroupAddress"),
                        "CarGroupCreditCode", obj.get("CarGroupCreditCode"), "CarGroupName", obj.get("CarGroupName"), "CreateTime", obj.get("CreateTime"),
                        "UpdateTime", obj.get("UpdateTime"), "Phone", obj.get("Phone")));
                    Close("OK");
                    return;
                }
                obj.put("UpdateType", "Delete");
                BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CarGroupUpdate", Wire.json(obj));
                throw new IllegalStateException(ret.msg());
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
