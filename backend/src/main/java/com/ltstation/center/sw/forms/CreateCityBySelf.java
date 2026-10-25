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

/** CreateCityBySelf : nouvelle ville d'une région (assistant d'installation de la station). */
@SwWindow(value = "CreateCityBySelf", setup = true)
public class CreateCityBySelf extends SwForm {

    @Override
    protected void init() {
        On("CityCode", "TextChanged", "SkinTxt_TextChanged");
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

    public void RegionSelectCode_DropDown() {
        try {
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetRegion", "");
            if (r.Result()) {
                c("RegionSelectCode").DataSource(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())), "RegionDesc", "CodeInner");
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void RegionSelectCode_DropDownClosed() {
        String codeInner = c("RegionSelectCode").SelectedRow().str("CodeInner");
        Row obj = SW.dto().make("StationRegion", "CodeInner", codeInner);
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCity", Wire.json(obj));
        int num = 0;
        if (r.Result()) {
            List<Row> list = Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()));
            num = list.isEmpty() ? Conv.toInt(codeInner + "01") : Conv.toInt(list.get(0).get("CodeInner")) + 1;
        }
        c("CityCodeInner").Text(BaseFunction.ConverToStr(String.valueOf(num), 4));
    }

    public void CanelButton_Click() {
        Close();
    }

    public void SaveButton_Click() {
        try {
            if (c("CityCodeInner").Text().isEmpty() || c("CityCode").Text().isEmpty() || c("CityName").Text().isEmpty()) {
                throw new IllegalStateException(L("InformationLoss"));
            }
            Row obj = SW.dto().make("StationCities", "CityDesc", c("CityName").Text(), "Code", c("CityCode").Text(),
                "CodeInner", c("CityCodeInner").Text(), "RegionID", c("RegionSelectCode").SelectedRow().get("RegionID"));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CreateCity", Wire.json(obj));
            if (r.Result()) {
                Close();
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
