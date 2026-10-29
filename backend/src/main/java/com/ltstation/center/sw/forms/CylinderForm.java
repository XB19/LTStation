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
import com.ltstation.center.sw.form.SwForm;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Code commun à CarAdd et CarUpdateMess : listes FoRan et bouteilles de gaz (CylinderData). */
abstract class CylinderForm extends SwForm {

    static final String[] CYLINDER_FIELDS = {"ID", "CarUserID", "VehicleID", "CylinderTypeId", "CylinderType", "ProductName", "MediumId", "Medium",
        "WorkPressure", "Cubage", "WallThickness", "ManufactureId", "ManufactureName", "ProductionDate", "ProductNo", "OwnedNo", "MjDate", "XjDate",
        "BfDate", "CreateTime", "UpdateTime"};

    /** BaseFunction.PostJson(url, "", "GET") vers la plateforme FoRan ; liste en DataSource du combo. */
    void LoadFoRan(String path, String combo, String display, String value) {
        try {
            FoRan r = SupervisionFunction.GetJson(IniInfo.FoRanUrl + path, Map.of());
            if (r.code() == 0) {
                c(combo).DataSource(Wire.parseRows(Wire.json(r.data())), display, value);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    void LoadGroupHttp() {
        if (IniInfo.SupervisionType == 2) {
            LoadFoRan("/sj-business/sjuseunitinfo/all", "GroupSelect", "useCompanyName", "applicantUserId");
        }
    }

    void LoadCylinderHttp() {
        LoadFoRan("/sj-business/sjcylindertype/all", "CylinderBox", "dataName", "dataKey");
    }

    void LoadDielectricHttp() {
        LoadFoRan("/sj-business/sjmediumtype/all", "DielectricBox", "dataName", "dataKey");
    }

    void LoadManufactorHttp() {
        LoadFoRan("/sj-business/sjmanufactureinfo/all", "ManufactorBox", "dataName", "dataKey");
    }

    void LoadGrade() {
        try {
            c("LimitGradeCombox").DataSource(db().list("GradeSpecifics", "ORDER BY {GradeSpecificID}", null), "Name", "GradeSpecificID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    void LoadLocalGroup() {
        try {
            c("LocalGroup").DataSource(db().list("CarGroup"), "CarGroupName", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    static Row cylinderRow(Row req, boolean withCylinderId) {
        Row r = new Row();
        for (String f : CYLINDER_FIELDS) {
            r.put(f, req.get(f));
        }
        if (withCylinderId) {
            r.put("CylinderId", req.get("CylinderId"));
        }
        return r;
    }

    private static BigDecimal tryDecimal(String s, String error) {
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(error);
        }
    }

    /** CylinderRequest construit à partir d'une bouteille lue sur FoRan (téléchargement). */
    static Row cylinderFromFoRan(Row item, int carUserId) {
        return SW.dto().make("CylinderRequest", "VehicleID", item.get("vehicleId"), "BfDate", item.get("bfDate"), "XjDate", item.get("xjDate"), "MjDate",
            item.get("mjDate"), "CylinderTypeId", item.get("cylinderTypeId"), "CylinderType", item.get("cylinderType"), "ProductName",
            item.get("productName"), "Medium", item.get("medium"), "MediumId", item.get("mediumId"), "WorkPressure", item.get("workPressure"), "Cubage",
            item.get("cubage"), "WallThickness", Conv.toDecimal(item.get("wallThickness")), "ManufactureId", item.get("manufactureId"), "ManufactureName",
            item.get("manufactureName"), "ProductionDate", item.get("productionDate"), "ProductNo", item.get("productNo"), "OwnedNo", item.get("ownedNo"),
            "CreateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()), "CarUserID", carUserId, "UpdateType", "Add", "CylinderId",
            item.get("cylinderId"));
    }

    /**
     * UploadBtn_Click : bouteille saisie envoyée à Center puis à FoRan ; enregistrée localement si FoRan l'accepte,
     * sinon supprimée de Center. failMess : message levé dans ce cas (null = aucun, comme CarUpdateMess).
     */
    void UploadCylinder(String vehicleId, int carUserId, boolean throwOnFail) {
        if (vehicleId.isEmpty()) {
            throw new IllegalStateException("Le véhicule n'est pas encore créé");
        }
        String text = c("CylinderBox").SelectedValue().toString();
        String cylinderType = c("CylinderBox").Text();
        String text2 = c("PDName").Text();
        if (text2.isEmpty()) {
            throw new IllegalStateException("Saisissez le nom du produit");
        }
        String text3 = c("DielectricBox").SelectedValue().toString();
        String medium = c("DielectricBox").Text();
        BigDecimal result = tryDecimal(c("WorkPressureBox").Text(), "Format de la pression de remplissage incorrect");
        BigDecimal result2 = tryDecimal(c("CubageBox").Text(), "Format du volume incorrect");
        BigDecimal result3 = tryDecimal(c("WallThicknessBox").Text(), "Format de l'épaisseur de paroi incorrect");
        String text4 = c("ManufactorBox").SelectedValue().toString();
        String manufactureName = c("ManufactorBox").Text();
        LocalDateTime pd = c("PDDate").Value();
        String text5 = c("PDCode").Text();
        if (text5.isEmpty()) {
            throw new IllegalStateException("Saisissez le code produit");
        }
        String text6 = c("OWNoBox").Text();
        if (text6.isEmpty()) {
            throw new IllegalStateException("Saisissez le code-barres");
        }
        LocalDateTime mj = c("MJDateBox").Value();
        LocalDateTime xj = c("XjDateBox").Value();
        LocalDateTime bf = c("BfDateBox").Value();
        if (text.isEmpty() || text3.isEmpty() || text4.isEmpty() || text2.isEmpty()) {
            throw new IllegalStateException("Informations manquantes, veuillez compléter");
        }
        Row obj = SW.dto().make("CylinderRequest", "VehicleID", vehicleId, "BfDate", String.valueOf(BaseFunction.ConvertDateTimeToInt(bf)), "XjDate",
            String.valueOf(BaseFunction.ConvertDateTimeToInt(xj)), "MjDate", String.valueOf(BaseFunction.ConvertDateTimeToInt(mj)), "CylinderTypeId", text,
            "CylinderType", cylinderType, "ProductName", text2, "Medium", medium, "MediumId", text3, "WorkPressure", result, "Cubage", result2,
            "WallThickness", result3, "ManufactureId", text4, "ManufactureName", manufactureName, "ProductionDate",
            String.valueOf(BaseFunction.ConvertDateTimeToInt(pd)), "ProductNo", text5, "OwnedNo", text6, "CreateTime",
            BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()), "CarUserID", carUserId, "UpdateType", "Add");
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CylinderUpdate", Wire.json(obj));
        if (!r.Result()) {
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        }
        obj = SW.dto().shape("CylinderRequest", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
        DateTimeFormatter ym = DateTimeFormatter.ofPattern("yyyy-MM");
        Row obj2 = SW.dto().make("CylinderParam", "vehicleId", obj.get("VehicleID"), "bfDate", bf.format(ym), "xjDate", xj.format(ym), "mjDate",
            mj.format(ym), "cylinderTypeId", obj.get("CylinderTypeId"), "cylinderType", obj.get("CylinderType"), "productName", obj.get("ProductName"),
            "medium", obj.get("Medium"), "mediumId", obj.get("MediumId"), "workPressure", obj.get("WorkPressure"), "cubage", obj.get("Cubage"),
            "wallThickness", obj.get("WallThickness"), "manufactureId", obj.get("ManufactureId"), "manufactureName", obj.get("ManufactureName"),
            "productionDate", pd.format(ym), "productNo", obj.get("ProductNo"), "ownedNo", obj.get("OwnedNo"), "reviewPassed", 1);
        if (SupervisionFunction.PostJson(IniInfo.FoRanUrl + "/sj-business/sjcylinder/v1/data", Wire.json(obj2)).code() == 0) {
            db().insert("CylinderData", cylinderRow(obj, false));
            Close("OK");
            return;
        }
        obj.put("UpdateType", "Delete");
        r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CylinderUpdate", Wire.json(obj));
        if (throwOnFail) {
            throw new IllegalStateException(r.Mess());
        }
    }
}
