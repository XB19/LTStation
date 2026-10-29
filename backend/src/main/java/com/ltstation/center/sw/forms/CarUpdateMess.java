package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.engine.SupervisionFunction;
import com.ltstation.center.sw.form.SwWindow;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** CarUpdateMess : modification d'un véhicule et de ses bouteilles de gaz (Center et plateforme FoRan). */
@SwWindow("CarUpdateMess")
public class CarUpdateMess extends CylinderForm {


    private int ID = -1;
    private String vehicleId = "";
    private int PageIndex = 1;
    private int PageCount = 1;
    private List<Row> Cy = new ArrayList<>();

    @Override
    protected void init() {
        ID = argInt("ID");
    }

    public void CarUpdateMess_Load() {
        try {
            c("CarBox").Visible(IniInfo.SupervisionType == 2);
            c("Cur").Enabled(true);
            c("Next").Enabled(true);
            if (ID == -1) {
                throw new IllegalStateException("Informations de l'appareil manquantes");
            }
            LoadGroupHttp();
            LoadLocalGroup();
            if (IniInfo.SupervisionType == 2) {
                LoadCylinderHttp();
                LoadDielectricHttp();
                LoadManufactorHttp();
            }
            LoadGrade();
            Row carUser = db().first("CarUser", "{ID} = :id", p("id", ID));
            if (carUser != null) {
                c("LocalGroup").SelectedValue(carUser.get("CarGroupID"));
                c("UserNameBox").Text(carUser.str("UserName"));
                c("CarNoBox").Text(carUser.str("PlateNumber"));
                switch (carUser.str("VehicleType")) {
                    case "公交车" -> c("CarTypeSelect").SelectedIndex(0);
                    case "出租车" -> c("CarTypeSelect").SelectedIndex(1);
                    case "其他车辆", "其他" -> c("CarTypeSelect").SelectedIndex(2);
                    default -> {
                    }
                }
                c("STDate").Value(BaseFunction.StampToDateTime(carUser.str("StartDate")));
                c("GroupSelect").SelectedValue(carUser.get("ApplicantUserId"));
                c("AsnBox").Text(carUser.str("Asn"));
                c("AccountBox").Text(carUser.str("CustomerAccountID"));
                c("LimitGradeCombox").SelectedValue(carUser.get("GradeID"));
                Cy = db().list("CylinderData", "{CarUserID} = :id", p("id", carUser.i("ID")));
                if (!Cy.isEmpty()) {
                    PageCount = Cy.size();
                    ShowCylinder();
                } else {
                    c("CarBox").Enabled(true);
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            Close("Cancel");
        }
    }

    private void ShowCylinder() {
        Row cy = Cy.get(PageIndex - 1);
        c("CylinderBox").SelectedValue(cy.get("CylinderTypeId"));
        c("PDName").Text(cy.str("ProductName"));
        c("DielectricBox").SelectedValue(cy.get("MediumId"));
        c("WorkPressureBox").Text(Conv.toDecimal(cy.get("WorkPressure")).toPlainString());
        c("CubageBox").Text(Conv.toDecimal(cy.get("Cubage")).toPlainString());
        c("WallThicknessBox").Text(Conv.toDecimal(cy.get("WallThickness")).toPlainString());
        c("ManufactorBox").SelectedValue(cy.get("ManufactureId"));
        c("PDDate").Value(Conv.toDateTime(cy.get("ProductionDate")));
        c("PDCode").Text(cy.str("ProductNo"));
        c("OWNoBox").Text(cy.str("OwnedNo"));
        c("MJDateBox").Value(Conv.toDateTime(cy.get("MjDate")));
        c("XjDateBox").Value(Conv.toDateTime(cy.get("XjDate")));
        c("BfDateBox").Value(Conv.toDateTime(cy.get("BfDate")));
    }

    public void UploadBtn_Click() {
        try {
            List<Row> carIDs = new ArrayList<>();
            String[] ERROR = {""};
            if (!SupervisionFunction.LoadCarUserHttp(c("CarNoBox").Text(), carIDs, ERROR)) {
                throw new IllegalStateException(ERROR[0]);
            }
            vehicleId = carIDs.get(0).str("vehicleId");
            UploadCylinder(vehicleId, ID, false);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Cur_Click() {
        if (sender.equals("Cur")) {
            PageIndex = PageIndex <= 1 ? 1 : PageIndex - 1;
        } else if (sender.equals("Next")) {
            PageIndex = PageIndex == PageCount ? PageCount : PageIndex + 1;
        }
        if (!Cy.isEmpty()) {
            ShowCylinder();
        }
    }

    public void CyDowload_Click() {
        try {
            List<Row> carIDs = new ArrayList<>();
            String[] ERROR = {""};
            if (!SupervisionFunction.LoadCarUserHttp(c("CarNoBox").Text(), carIDs, ERROR)) {
                throw new IllegalStateException(ERROR[0]);
            }
            vehicleId = carIDs.get(0).str("vehicleId");
            List<Row> cys = new ArrayList<>();
            if (!SupervisionFunction.LoadCylinderHttp(carIDs.get(0).str("plateNumber"), carIDs.get(0).str("vehicleId"), cys, ERROR)) {
                throw new IllegalStateException(ERROR[0]);
            }
            for (Row item : cys) {
                Row cyrequest = cylinderFromFoRan(item, ID);
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CylinderUpdate", Wire.json(cyrequest));
                if (r.Result()) {
                    cyrequest = SW.dto().shape("CylinderRequest", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
                    Row data = cylinderRow(cyrequest, true);
                    Row cylinderData = db().first("CylinderData", "{ID} = :id", p("id", cyrequest.get("ID")));
                    if (cylinderData != null) {
                        data.remove("ID");
                        cylinderData.putAll(data);
                        db().update("CylinderData", cylinderData);
                    } else {
                        db().insert("CylinderData", data);
                    }
                }
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void AddButton_Click() {
        try {
            Row obj = SW.dto().make("CarUserRequest", "ID", ID, "CarGroupID", Conv.toInt(c("LocalGroup").SelectedValue()), "UserName",
                c("UserNameBox").Text(), "PlateNumber", c("CarNoBox").Text(), "VehicleType", c("CarTypeSelect").Text(), "SecurityAdmin", IniInfo.LoginName,
                "UpdateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()), "UpdateType", "Update", "Asn", c("AsnBox").Text(), "Account",
                c("AccountBox").Text(), "EmpID", IniInfo.LoginID, "GradeID", Conv.toInt(c("LimitGradeCombox").SelectedValue()));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CarUpdate", Wire.json(obj));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            Row request = SW.dto().shape("CarUserRequest", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
            Row carUser = db().first("CarUser", "{ID} = :id", p("id", request.get("ID")));
            if (carUser == null) {
                throw new NullPointerException();
            }
            for (String f : new String[] {"CarGroupID", "PlateNumber", "UpdateTime", "UserName", "VehicleType", "GradeID"}) {
                carUser.put(f, request.get(f));
            }
            db().update("CarUser", carUser);
            MessageBox("Success");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
