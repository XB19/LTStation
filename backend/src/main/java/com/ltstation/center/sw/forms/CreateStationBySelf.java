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
import java.time.LocalDateTime;

/** CreateStationBySelf : création de la station sur Center (code, région, ville, coordonnées, contact). */
@SwWindow(value = "CreateStationBySelf", setup = true)
public class CreateStationBySelf extends SwForm {

    private int Code;

    public void CreateStationBySelf_Load() {
        try {
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetStationMax", "");
            Row st = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            String code = st == null ? null : st.str("Code");
            Code = code == null || code.isEmpty() || code.equals("0") ? 1000001 : Conv.toInt(code) + 1;
            c("StationCode").Text(String.valueOf(Code));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage() == null ? "Object reference not set to an instance of an object." : ex.getMessage());
        }
    }

    public void CreateRegion_Click() {
        ShowDialog("CreateRegionBySelf", r -> { });
    }

    /** Ouverture de la liste des régions ou des villes (sender = liste). */
    public void RegionSelect_DropDown() {
        if ("RegionSelect".equals(sender)) {
            try {
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetRegion", "");
                if (r.Result()) {
                    c("RegionSelect").DataSource(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())), "RegionDesc", "CodeInner");
                    c("CitySelect").Text("");
                }
            } catch (RuntimeException ignored) {
                // comme l'original
            }
        }
        if (!"CitySelect".equals(sender)) {
            return;
        }
        if (c("RegionSelect").Text().isEmpty()) {
            MessageBox(L("PLSSelectRegion"));
        }
        Row region = c("RegionSelect").SelectedRow();
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCity", Wire.json(region));
        if (r.Result()) {
            c("CitySelect").DataSource(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())), "CityDesc", "CodeInner");
        }
    }

    public void CreateCity_Click() {
        ShowDialog("CreateCityBySelf", r -> { });
    }

    private boolean SaveStation() {
        var tx = db().begin();
        try {
            for (String n : new String[] {"StationCode", "RegionSelect", "CitySelect", "StationName", "StationLongitude", "StationLatitude"}) {
                if (c(n).Text().isEmpty()) {
                    throw new IllegalStateException(L("InformationLoss"));
                }
            }
            Row region = c("RegionSelect").SelectedRow();
            Row city = c("CitySelect").SelectedRow();
            Row stations = Row.of("Code", c("StationCode").Text(), "RegionCodeInner", region.get("CodeInner"), "RegionCode", region.get("Code"),
                "RegionID", region.get("RegionID"), "CityCode", city.get("Code"), "CityCodeInner", city.get("CodeInner"), "CityID", city.get("CityID"),
                "Address", c("StationAddress").Text(), "Name", c("StationName").Text(), "Longitude", Conv.toDecimal(c("StationLongitude").Text()),
                "Latitude", Conv.toDecimal(c("StationLatitude").Text()), "Manager", c("StationManager").Text(), "Tel", c("Phone").Text(),
                "email", c("Email").Text(), "SuperCode", "0001", "MeasureTime", LocalDateTime.now().format(Conv.STD));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CreateStation", Wire.json(SW.dto().shape("Stations", stations)));
            if (r.Result()) {
                db().delete("Stations", null, null);
                stations.put("StationID", Conv.toInt(BaseFunction.DecryptByAES(r.Mess())));
                db().insert("Stations", stations);
                tx.commit();
                IniInfo.Code = stations.str("Code");
                IniInfo.StationID = stations.i("StationID");
                IniInfo.StationName = stations.str("Name");
                return true;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
            return false;
        }
    }

    public void NextButton_Click() {
        if (SaveStation()) {
            Close("OK");
        }
    }
}
