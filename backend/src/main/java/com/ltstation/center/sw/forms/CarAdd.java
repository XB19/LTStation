package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.engine.SupervisionFunction;
import com.ltstation.center.sw.engine.SupervisionFunction.FoRan;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** CarAdd : ajout d'un véhicule à un groupe (Center, plateforme FoRan), puis de ses bouteilles de gaz. */
@SwWindow("CarAdd")
public class CarAdd extends CylinderForm {

    private int GID;
    private int caruserID = -1;
    private String vehicleId = "";

    @Override
    protected void init() {
        GID = argInt("GroupID");
    }

    public void CarAdd_Load() {
        try {
            LoadGroupHttp();
            LoadLocalGroup();
            c("LocalGroup").SelectedValue(GID);
            LoadAsnHttp();
            LoadGrade();
            c("CarBox").Visible(IniInfo.SupervisionType == 2);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadAsnHttp() {
        try {
            String aSN = BaseFunction.GetASN(7);
            if (!aSN.isEmpty()) {
                Row info = Wire.parseRow(BaseFunction.DecryptByAES(aSN));
                BigDecimal num = Conv.toDecimal(info.get("ASN"));
                c("AsnBox").Text(BaseFunction.ConverToStr(num.add(BigDecimal.ONE).toPlainString(), 20));
                c("AccountBox").Text(String.valueOf(Conv.toLong(info.get("CustomerID")) + 1));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private Row carUserRow(Row req, String asn, String account) {
        Row r = new Row();
        for (String f : new String[] {"ID", "MobilePhone", "ApplicantUser", "Balance", "SecurityAdmin", "ApplicantUserId", "CarGroupID", "PlateNumber",
            "ReviewPassed", "StartDate", "UpdateTime", "UserName", "VehicleType"}) {
            r.put(f, req.get(f));
        }
        r.put("Asn", asn);
        r.put("CustomerAccountID", Conv.toInt(account));
        r.put("GradeID", req.get("GradeID"));
        return r;
    }

    public void AddButton_Click() {
        var tx = db().begin();
        try {
            String text = c("UserNameBox").Text();
            String text2 = c("CarNoBox").Text();
            String vehicleType = c("CarTypeSelect").Text();
            long startDate = BaseFunction.ConvertDateTimeToInt(c("STDate").Value());
            Object sel = c("GroupSelect").SelectedValue();
            String applicantUserId = sel == null ? "" : sel.toString();
            String applicantUser = c("GroupSelect").Text();
            String text3 = c("AsnBox").Text();
            String text4 = c("AccountBox").Text();
            int gradeID = Conv.toInt(c("LimitGradeCombox").SelectedValue());
            if (text.isEmpty() || text2.isEmpty()) {
                throw new IllegalStateException("Informations manquantes");
            }
            if (text3.isEmpty() || text4.isEmpty()) {
                throw new IllegalStateException("Informations du compte manquantes");
            }
            long now = BaseFunction.ConvertDateTimeToInt(LocalDateTime.now());
            Row carUserRequest = SW.dto().make("CarUserRequest", "UserName", text, "CarGroupID", GID, "PlateNumber", text2, "VehicleType", vehicleType,
                "StartDate", startDate, "SecurityAdmin", IniInfo.LoginName, "MobilePhone", "", "ApplicantUserId", applicantUserId, "ReviewPassed", 1,
                "ApplicantUser", applicantUser, "Balance", BigDecimal.ZERO, "UpdateTime", now, "UpdateType", "Add", "Asn", text3, "Account", text4, "EmpID",
                IniInfo.LoginID, "GradeID", gradeID);
            List<Row> carIDs = new ArrayList<>();
            String[] ERROR = {""};
            if (IniInfo.SupervisionType == 2) {
                SupervisionFunction.LoadCarUserHttp(Conv.toStr(carUserRequest.get("PlateNumber")), carIDs, ERROR);
            }
            if (!carIDs.isEmpty()) {
                // Véhicule déjà déclaré sur FoRan : groupe, véhicule et bouteilles repris de la plateforme
                Row car = carIDs.get(0);
                List<Row> groupQueries = new ArrayList<>();
                if (!SupervisionFunction.LoadCarGroupHttp(car.str("applicantUserId"), groupQueries, ERROR)) {
                    throw new IllegalStateException("Groupe introuvable");
                }
                Row g = groupQueries.get(0);
                Row carGroup = SW.dto().make("CarGroupRequest", "CarGroupName", g.get("useCompanyName"), "CarGroupAddress", g.get("useCompanyAddress"),
                    "CarGroupCreditCode", g.get("useCompanyIdentityCard"), "CreateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()), "UpdateTime",
                    BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()), "Balance", BigDecimal.ZERO, "UpdateType", "AddQ", "Phone", g.get("tel"), "EmpID",
                    IniInfo.LoginID);
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CarGroupUpdate", Wire.json(carGroup));
                if (r.Result()) {
                    carGroup = SW.dto().shape("CarGroupRequest", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
                    if (db().first("CarGroup", "{ID} = :id", p("id", carGroup.get("ID"))) == null) {
                        db().insert("CarGroup", Row.of("ID", carGroup.get("ID"), "Balance", carGroup.get("Balance"), "CarGroupAddress",
                            carGroup.get("CarGroupAddress"), "CarGroupCreditCode", carGroup.get("CarGroupCreditCode"), "CarGroupName",
                            carGroup.get("CarGroupName"), "CreateTime", carGroup.get("CreateTime"), "UpdateTime", carGroup.get("UpdateTime"), "Phone",
                            carGroup.get("Phone")));
                    }
                }
                Row obj = SW.dto().make("CarUserRequest", "UserName", car.get("plateNumber"), "CarGroupID", carGroup.get("ID"), "PlateNumber",
                    car.get("plateNumber"), "VehicleType", car.get("vehicleType"), "StartDate",
                    BaseFunction.ConvertDateTimeToInt(Conv.toDateTime(car.get("startDate"))), "SecurityAdmin", IniInfo.LoginName, "MobilePhone",
                    car.get("mobilePhone"), "ApplicantUserId", car.get("applicantUserId"), "ReviewPassed", 1, "ApplicantUser", carGroup.get("CarGroupName"),
                    "Balance", BigDecimal.ZERO, "UpdateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()), "UpdateType", "Add", "Asn", text3,
                    "Account", text4, "EmpID", IniInfo.LoginID, "GradeID", gradeID);
                r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CarUpdate", Wire.json(obj));
                if (!r.Result()) {
                    throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
                }
                carUserRequest = SW.dto().shape("CarUserRequest", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
                Row carUser = carUserRow(carUserRequest, text3, text4);
                db().insert("CarUser", carUser);
                List<Row> cys = new ArrayList<>();
                if (!SupervisionFunction.LoadCylinderHttp(car.str("plateNumber"), car.str("vehicleId"), cys, ERROR)) {
                    throw new IllegalStateException(ERROR[0]);
                }
                for (Row item : cys) {
                    Row obj2 = cylinderFromFoRan(item, carUser.i("ID"));
                    r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CylinderUpdate", Wire.json(obj2));
                    if (r.Result()) {
                        obj2 = SW.dto().shape("CylinderRequest", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
                        db().insert("CylinderData", cylinderRow(obj2, true));
                    }
                }
                tx.commit();
                Close("OK");
            } else {
                CustomerRespone r2 = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CarUpdate", Wire.json(carUserRequest));
                if (!r2.Result()) {
                    throw new IllegalStateException(BaseFunction.DecryptByAES(r2.Mess()));
                }
                carUserRequest = SW.dto().shape("CarUserRequest", Wire.parseRow(BaseFunction.DecryptByAES(r2.Mess())));
                FoRan ret = new FoRan(0, "", "");
                if (IniInfo.SupervisionType == 2) {
                    Row obj3 = SW.dto().make("FoRanCarUserParam", "plateNumber", text2, "vehicleType", vehicleType, "startDate",
                        c("STDate").Value().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), "securityAdmin", IniInfo.LoginName, "mobilePhone", "123",
                        "applicantUserId", applicantUserId, "applicantUser", applicantUser, "reviewPassed", 1);
                    ret = SupervisionFunction.PostJson(IniInfo.FoRanUrl + "/sj-business/sjcarrecord/v1/data", Wire.json(obj3));
                }
                if (ret.code() != 0) {
                    carUserRequest.put("UpdateType", "Delete");
                    BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CarUpdate", Wire.json(carUserRequest));
                    throw new IllegalStateException(ret.msg());
                }
                db().insert("CarUser", carUserRow(carUserRequest, text3, text4));
                caruserID = carUserRequest.i("ID");
                c("CarBox").Enabled(true);
                if (IniInfo.SupervisionType == 2) {
                    List<Row> carIDs2 = new ArrayList<>();
                    String[] ERROR2 = {""};
                    if (!SupervisionFunction.LoadCarUserHttp(carUserRequest.str("PlateNumber"), carIDs2, ERROR2)) {
                        throw new IllegalStateException(ERROR2[0]);
                    }
                    vehicleId = carIDs2.get(0).str("vehicleId");
                    LoadCylinderHttp();
                    LoadDielectricHttp();
                    LoadManufactorHttp();
                }
                MessageBox("Envoi réussi : renseignez maintenant les bouteilles de gaz du véhicule");
                tx.commit();
            }
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    public void UploadBtn_Click() {
        try {
            UploadCylinder(vehicleId, caruserID, true);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
