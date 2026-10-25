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
import java.math.RoundingMode;
import java.util.List;

/** OilTypeAndPrice(i) : carburants et prix de la station (rechargés depuis Center), prix modifiable dans le tableau. */
@SwWindow(value = "OilTypeAndPrice", setup = true)
public class OilTypeAndPrice extends SwForm {

    private int sid;
    private final Row CustomerParam = SW.dto().make("CustomerParam");
    private Row stations;

    @Override
    protected void init() {
        stations = db().first("Stations");
        sid = argInt("ID");
        DowOilType();
        DowOilPrice();
        LoadData();
        if (sid == 1) {
            c("NextButton").Text(L("Confirm"));
        }
    }

    private void DowOilType() {
        if (stations == null) {
            if (sid != 0) {
                MessageBox(L("PLSCreateStation"));
            }
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

    private void DowOilPrice() {
        if (stations == null) {
            if (sid != 0) {
                MessageBox(L("PLSCreateStation"));
            }
            return;
        }
        var tx = db().begin();
        try {
            CustomerParam.put("Code", stations.get("Code"));
            CustomerParam.put("Start", 0);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetPrice", Wire.json(CustomerParam));
            if (r.Result()) {
                List<Row> list = Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()));
                if (!list.isEmpty()) {
                    db().delete("PriceInfos", null, null);
                    for (Row item : list) {
                        db().insert("PriceInfos", item);
                    }
                }
            }
            tx.commit();
        } catch (RuntimeException e) {
            tx.commit();
        }
    }

    private void LoadData() {
        var grid = g("OilPriceView");
        grid.RowsClear();
        if (grid.columns().size() > 1) {
            grid.ComboColumn(grid.columns().get(1).name(), db().list("GradeSpecifics"), "Name", "Code");
        }
        String sql = "SELECT {GradeSpecifics.GradeSpecificID} AS ID, {GradeSpecifics.Code} AS Code, {PriceInfos.NewPPU} AS Price,"
            + " {PriceInfos.ValidateTime} AS ValidTime FROM [GradeSpecifics] JOIN [PriceInfos] ON {GradeSpecifics.Code} = {PriceInfos.Code}";
        for (Row item : db().query(sql, null)) {
            BigDecimal price = Conv.toDecimal(item.get("Price"));
            grid.RowsAdd(item.get("Code"), item.str("Code"), price.scale() > IniInfo.Pradio ? price.setScale(IniInfo.Pradio, RoundingMode.HALF_EVEN) : price,
                Conv.toDateTime(item.get("ValidTime")).format(Conv.STD));
        }
    }

    /** Ligne courante : carburant et prix (jointure GradeSpecifics / PriceInfos). */
    private Row priceOf(int id) {
        Row g = db().first("GradeSpecifics", "{GradeSpecificID} = :id", p("id", id));
        return g == null ? null : db().first("PriceInfos", "{Code} = :c", p("c", g.get("Code")));
    }

    public void OilPriceView_CellEndEdit() {
        var tx = db().begin();
        try {
            int index = g("OilPriceView").CurrentRowIndex();
            int col = g("OilPriceView").CurrentCellColumnIndex();
            String text = Conv.toStr(g("OilPriceView").Cell(index, col));
            int id = Integer.parseInt(Conv.toStr(g("OilPriceView").Cell(index, "ID")));
            Row price = priceOf(id);
            if (col == 2) {
                if (!text.matches("^-?\\d+$|^(-?\\d+)(\\.\\d+)?$")) {
                    throw new IllegalStateException(L("NumberOrDecimal"));
                }
                price.put("CurrentPPU", Conv.toDecimal(price.get("NewPPU")));
                price.put("NewPPU", Conv.toDecimal(text));
                db().update("PriceInfos", price);
            }
            BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpOilPrice", Wire.json(SW.dto().shape("PriceInfos", price)));
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage() == null ? "Object reference not set to an instance of an object." : ex.getMessage());
            LoadData();
        }
    }

    public void DeleteButton_Click() {
        int index = g("OilPriceView").CurrentRowIndex();
        if (index < 0) {
            return;
        }
        int id = Integer.parseInt(Conv.toStr(g("OilPriceView").Cell(index, "ID")));
        if (db().first("tankConfig", "{OilCode} = :c", p("c", id)) != null) {
            MessageBox(L("IICACBD"));
            return;
        }
        Row price = db().first("PriceInfos", "{Code} = :c", p("c", String.valueOf(id)));
        if (price != null && Confirm(L("OperationExecution"))) {
            db().deleteEntity("PriceInfos", price);
        }
        LoadData();
    }

    public void Add_Click() {
        ShowDialog("CreateOilPrice", r -> LoadData());
    }

    public void NextButton_Click() {
        if (sid == 0) {
            ShowDialog("StationTankList", r -> Close(r.result()), "ID", 0);
        } else {
            Close();
        }
    }

    public void OilPriceView_CellDoubleClick() {
        int index = g("OilPriceView").CurrentRowIndex();
        if (index < 0) {
            return;
        }
        int id = Integer.parseInt(Conv.toStr(g("OilPriceView").Cell(index, "ID")));
        Row g = db().first("GradeSpecifics", "{Code} = :c", p("c", String.valueOf(id)));
        if (g != null) {
            ShowDialog("PriceShow", r -> {
                if (r.ok()) {
                    LoadData();
                }
            }, "Code", g.str("Code"));
        }
    }
}
