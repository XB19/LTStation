package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.WorkOrderData;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** EquipmentEdit(ID) : création (ID = 0) ou modification d'un équipement. */
@SwWindow("EquipmentEdit")
public class EquipmentEdit extends SwForm {

    private int id;

    @Override
    protected void init() {
        id = argInt("ID");
        c("CategoryBox").Items(WorkOrderData.CATEGORIES);
        List<Row> grades = new ArrayList<>();
        grades.add(Row.of("Code", "", "Name", ""));
        for (Row g : db().list("GradeSpecifics")) {
            grades.add(Row.of("Code", String.valueOf(g.get("Code")), "Name", g.get("Name")));
        }
        c("GradeBox").DataSource(grades, "Name", "Code");
        c("StatusBox").Items(List.of(WorkOrderData.STATUS.get(1), WorkOrderData.STATUS.get(2), WorkOrderData.STATUS.get(3)));
        c("StatusBox").SelectedIndex(0);
        Title(id == 0 ? "Ajouter un équipement" : "Modifier l'équipement");
        if (id != 0) {
            Map<String, Object> e = WorkOrderData.equipment(id);
            c("NameBox").Text(WorkOrderData.str(e.get("name")));
            c("WorkOrderBox").Text(WorkOrderData.str(e.get("work_order_no")));
            c("CategoryBox").Text(WorkOrderData.str(e.get("category")));
            c("BrandBox").Text(WorkOrderData.str(e.get("brand")));
            c("ModelBox").Text(WorkOrderData.str(e.get("model")));
            c("SerialBox").Text(WorkOrderData.str(e.get("serial_no")));
            c("FleetBox").Text(WorkOrderData.str(e.get("fleet_no")));
            c("GradeBox").SelectedValue(WorkOrderData.str(e.get("grade_code")));
            c("CapacityBox").Text(e.get("tank_capacity") == null ? "" : ((BigDecimal) e.get("tank_capacity")).stripTrailingZeros().toPlainString());
            c("StatusBox").SelectedIndex(((Number) e.get("status")).intValue() - 1);
            c("MemoBox").Text(WorkOrderData.str(e.get("memo")));
        }
    }

    public void SaveBtn_Click() {
        try {
            BigDecimal capacity = null;
            String cap = c("CapacityBox").Text().trim().replace(" ", "").replace(',', '.');
            if (!cap.isEmpty()) {
                try {
                    capacity = new BigDecimal(cap);
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Capacité du réservoir invalide : " + cap);
                }
            }
            Object grade = c("GradeBox").SelectedValue();
            WorkOrderData.Equipment f = WorkOrderData.clean(new WorkOrderData.Equipment(c("NameBox").Text(), c("WorkOrderBox").Text(),
                c("CategoryBox").Text(), c("BrandBox").Text(), c("ModelBox").Text(), c("SerialBox").Text(), c("FleetBox").Text(),
                grade == null ? null : grade.toString(), capacity, c("StatusBox").SelectedIndex() + 1, c("MemoBox").Text()), id == 0 ? null : id);
            if (id == 0) {
                WorkOrderData.insert(f);
            } else {
                int moved = WorkOrderData.update(id, f);
                if (moved > 0) {
                    MessageBox(moved + " carte(s) rattachée(s) passent au nouvel ordre de travail " + f.workOrderNo()
                        + ". L'ancien reste dans leur historique.");
                }
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CancelBtn_Click() {
        Close();
    }
}
