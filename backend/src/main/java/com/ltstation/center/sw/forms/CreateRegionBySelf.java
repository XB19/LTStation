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

/** CreateRegionBySelf : nouvelle région (assistant d'installation de la station). */
@SwWindow(value = "CreateRegionBySelf", setup = true)
public class CreateRegionBySelf extends SwForm {

    private int Code;

    @Override
    protected void init() {
        On("RegionCode", "TextChanged", "SkinTxt_TextChanged");
    }

    public void SkinTxt_TextChanged() {
        String text = c(sender).Text();
        if (text.isEmpty()) {
            return;
        }
        if (!text.matches("^[0-9A-Za-z]*$")) {
            MessageBox(L("OneWordOnly"));
            c(sender).Text("");
        }
    }

    public void CreateRegionBySelf_Load() {
        try {
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetRegionMax", "");
            Row region = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            Code = !region.str("CodeInner").isEmpty() ? Conv.toInt(region.get("CodeInner")) + 1 : 1;
            c("RegionCodeInner").Text(BaseFunction.ConverToStr(String.valueOf(Code), 2));
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void SaveButton_Click() {
        if (c("RegionCodeInner").Text().isEmpty() || c("RegionCode").Text().isEmpty() || c("RegionName").Text().isEmpty()) {
            MessageBox(L("InformationLoss"));
            return;
        }
        Row obj = SW.dto().make("StationRegion", "CodeInner", c("RegionCodeInner").Text(), "Code", c("RegionCode").Text(),
            "RegionDesc", c("RegionName").Text());
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CreateRegion", Wire.json(obj));
        if (!r.Result()) {
            MessageBox(BaseFunction.DecryptByAES(r.Mess()));
        } else {
            Close();
        }
    }

    public void CanelButton_Click() {
        Close();
    }
}
