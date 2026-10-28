package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.BoxBase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** BoxScreenView.InternalUserView : lignes de facture filtrées par pistolet, produit et poste (0 = tous). */
@SwWindow("BoxScreenView.InternalUserView")
public class InternalUserView extends SwForm {

    private static int selected(Object v) {
        return v == null ? 0 : Conv.toInt(v);
    }

    public void OpenAllPumpBtn_Click() {
        int noz = selected(c("NozSelectCom").SelectedValue());
        int grade = selected(c("GradeSelectCom").SelectedValue());
        int shift = selected(c("ShiftSelectCom").SelectedValue());
        StringBuilder where = new StringBuilder("1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (noz != 0) {
            where.append(" AND {NozID} = :n");
            params.put("n", noz);
        }
        if (grade != 0) {
            where.append(" AND {GradeID} = :g");
            params.put("g", grade);
        }
        if (shift != 0) {
            where.append(" AND {BoxShift} = :s");
            params.put("s", shift);
        }
        ShowData(db().list("BillOrder", where.toString(), params));
    }

    private void ShowData(List<Row> list) {
        g("OilDataView").RowsClear();
        try {
            for (Row item : list) {
                g("OilDataView").RowsAdd(item.get("ID"), item.str("BillOrderNumber"), BaseFunction.StampToDateTime(item.str("ConfirmTime")),
                    item.get("TransactionID"), item.str("GradeName"), item.get("POSID"), item.get("NozID"), item.get("Volume"), item.get("Price"),
                    item.get("Amount"), BoxBase.GetCashPayMent(item.i("BillPayMent")));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void skinButton1_Click() {
        Close();
    }

    public void InternalUserView_Load() {
        LoadNozSelect();
        LoadGradeSelect();
        LoadShiftSelect();
    }

    private static List<Row> all() {
        List<Row> list = new ArrayList<>();
        list.add(Row.of("ID", 0, "Value", "All"));
        return list;
    }

    private void LoadNozSelect() {
        try {
            List<Row> list = all();
            for (Row item : db().list("NormalNoz", "ORDER BY {NozID}", null)) {
                list.add(Row.of("ID", item.get("NozID"), "Value", item.str("NozID")));
            }
            c("NozSelectCom").DataSource(list, "Value", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadGradeSelect() {
        try {
            List<Row> list = all();
            for (Row item : db().list("NormalGrade")) {
                list.add(Row.of("ID", item.get("ID"), "Value", item.str("GradeName")));
            }
            c("GradeSelectCom").DataSource(list, "Value", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadShiftSelect() {
        try {
            List<Row> list = all();
            for (Row item : db().list("BoxShiftList", "ORDER BY {BoxShiftID} DESC", null)) {
                long end = Conv.toLong(item.get("EndTime"));
                list.add(Row.of("ID", item.get("BoxShiftID"), "Value", item.str("BoxShiftID") + "---"
                    + BaseFunction.StampToDateTime(item.str("StartTime")).format(Conv.STD) + "-"
                    + (end == 0 ? "" : BaseFunction.StampToDateTime(String.valueOf(end)).format(Conv.STD))));
            }
            c("ShiftSelectCom").DataSource(list, "Value", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
