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

/**
 * BaseMessage(id) : informations de la station. id 0 = assistant (code de la station existante sur
 * Center ou création), id 2 = modification depuis la fenêtre principale.
 */
@SwWindow(value = "BaseMessage", setup = true)
public class BaseMessage extends SwForm {

    private int sID;
    private Row stations = new Row();

    @Override
    protected void init() {
        sID = argInt("ID");
        if (sID == 2) {
            c("CreateStation").Visible(false);
        }
    }

    public void BaseMessage_Load() {
        try {
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetRegion", "");
            if (r.Result()) {
                c("RegionSelect").DataSource(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())), "RegionDesc", "CodeInner");
            }
            r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCity", Wire.json(SW.dto().make("StationRegion", "Code", "203045")));
            if (r.Result()) {
                c("CitySelect").DataSource(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())), "CityDesc", "CodeInner");
            }
            if (sID == 2) {
                c("NextButton").Text(L("Confirm"));
                Row st = db().first("Stations", "{StationID} = :id", p("id", IniInfo.StationID));
                if (st != null) {
                    c("StationCode").Text(st.get("Code"));
                }
                EnableChange();
                stations.put("Code", st.get("Code"));
                stations.put("CityCodeInner", st.get("CityCodeInner"));
                stations.put("RegionCodeInner", st.get("RegionCodeInner"));
                c("CitySelect").SelectedValue(st.get("CityCodeInner"));
                c("RegionSelect").SelectedValue(st.get("RegionCodeInner"));
                c("StationName").Text(st.get("Name"));
                c("StationAddress").Text(st.get("Address"));
                c("StationManager").Text(st.get("Manager"));
                c("Phone").Text(st.get("Tel"));
                c("Email").Text(st.get("email"));
            } else {
                r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetControl", "");
                if (!r.Result()) {
                    c("CreateStation").Visible(true);
                }
                On("StationCode", "TextChanged", "SkinTxt_TextChanged");
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void EnableChange() {
        for (String n : new String[] {"RegionSelect", "CitySelect", "StationName", "StationAddress", "StationManager", "Phone", "Email"}) {
            c(n).Enabled(true);
        }
        c("StationCode").Enabled(false);
    }

    /** Saisie du code : la station est lue sur Center. */
    public void SkinTxt_TextChanged() {
        String code = c(sender).Text();
        try {
            stations = SW.dto().make("Stations", "Code", code);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetStation", Wire.json(stations));
            if (r.Result()) {
                stations = SW.dto().shape("Stations", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
                c("RegionSelect").SelectedValue(stations.get("RegionCodeInner"));
                c("CitySelect").SelectedValue(stations.get("CityCodeInner"));
                c("StationName").Text(stations.get("Name"));
                c("StationAddress").Text(stations.get("Address"));
                c("StationManager").Text(stations.get("Manager"));
                c("Phone").Text(stations.get("Tel"));
                c("Email").Text(stations.get("email"));
                stations.put("MeasureTime", LocalDateTime.now().format(Conv.STD));
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void CreateStation_Click() {
        ShowDialog("CreateStationBySelf", r -> Close(r.result()));
    }

    public void NextButton_Click() {
        var tx = db().begin();
        try {
            if (stations.get("Code") == null || stations.get("CityCodeInner") == null || stations.get("RegionCodeInner") == null) {
                MessageBox(L("InformationLoss"));
                tx.rollback();
                return;
            }
            if (sID == 0) {
                db().delete("Stations", null, null);
                db().insert("Stations", stations);
            }
            if (sID == 2) {
                Row region = c("RegionSelect").SelectedRow();
                Row city = c("CitySelect").SelectedRow();
                stations.put("Name", c("StationName").Text());
                stations.put("Address", c("StationAddress").Text());
                stations.put("Manager", c("StationManager").Text());
                stations.put("Tel", c("Phone").Text());
                stations.put("email", c("Email").Text());
                stations.put("RegionID", region.get("RegionID"));
                stations.put("RegionCodeInner", region.get("CodeInner"));
                stations.put("CityID", city.get("CityID"));
                stations.put("CityCodeInner", city.get("CodeInner"));
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpdateStation", Wire.json(SW.dto().shape("Stations", stations)));
                if (!r.Result()) {
                    throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
                }
                Row local = db().first("Stations");
                boolean add = local == null;
                if (add) {
                    local = new Row();
                }
                for (String k : new String[] {"Address", "Manager", "Name", "Tel", "email"}) {
                    local.put(k, stations.get(k));
                }
                local.put("RegionCode", region.get("Code"));
                local.put("RegionCodeInner", region.get("CodeInner"));
                local.put("RegionID", region.get("RegionID"));
                local.put("CityID", city.get("CityID"));
                local.put("CityCode", city.get("CodeInner"));
                local.put("CityCodeInner", city.get("CodeInner"));
                if (add) {
                    db().insert("Stations", local);
                } else {
                    db().update("Stations", local);
                }
                tx.commit();
                Row obj = db().first("Stations");
                IniInfo.Code = obj.str("Code");
                IniInfo.StationID = obj.i("StationID");
                IniInfo.StationName = obj.str("Name");
                Close();
            } else {
                IniInfo.Code = stations.str("Code");
                IniInfo.StationID = stations.i("StationID");
                IniInfo.StationName = stations.str("Name");
                // L'original garde la transaction ouverte pendant l'assistant : elle est validée avant l'étape suivante
                tx.commit();
                ShowDialog("OilTypeAndPrice", r -> Close(r.result()), "ID", 0);
            }
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }
}
