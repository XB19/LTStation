package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CustomerUserList : cartes des clients de la station (nom, téléphone, immatriculation, état), 11 par page. */
@SwWindow("CustomerUserList")
public class CustomerUserList extends SwForm {

    private int Index;

    @Override
    protected void init() {
        c("CardStatue").SelectedIndex(0);
    }

    /** Requête des cartes : filtres de l'original (WhereIF + In). */
    private Sugar.Page query(int pageIndex) {
        String uName = c("UserName").Text();
        String uPhone = c("Phone").Text();
        String uCar = c("CarNumber").Text();
        int sel = c("CardStatue").SelectedIndex();
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (sel == 1) {
            where.add("{Suspend} = 0");
        }
        if (sel == 2) {
            where.add("{Suspend} <> 0");
        }
        if (!(uName.isEmpty() && uPhone.isEmpty() && uCar.isEmpty())) {
            List<String> cw = new ArrayList<>();
            if (!uName.isEmpty()) {
                cw.add("{Name} = :n");
                params.put("n", uName);
            }
            if (!uPhone.isEmpty()) {
                cw.add("{Telephone} = :t");
                params.put("t", uPhone);
            }
            if (!uCar.isEmpty()) {
                cw.add("{VehicleNo} = :v");
                params.put("v", uCar);
            }
            List<Integer> ids = db().list("Customers", String.join(" AND ", cw), params).stream().map(r -> r.i("CustomerID")).toList();
            where.add("{CustomerID} IN (:ids)");
            params.put("ids", ids);
        }
        return db().page("CustomerCardInfos", where.isEmpty() ? null : String.join(" AND ", where), params, null, pageIndex, 11);
    }

    public void EmpReadButton_Click() {
        Sugar.Page page = query(1);
        if (!page.rows().isEmpty()) {
            Index = 1;
            c("Page").Text("1");
            c("TotalNumber").Text("/" + page.totalPage());
            c("Total").Text(String.valueOf(page.total()));
            LoadData(page.rows());
        }
    }

    private void LoadData(List<Row> infos) {
        try {
            g("UserView").RowsClear();
            for (Row info : infos) {
                g("UserView").RowsAdd(info.get("ASN"), info.get("Name"), BaseFunction.GetCardStatue(Conv.toInt(info.get("Suspend"))), info.get("Telephone"),
                    info.get("VehicleNo"));
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void Page_TextChanged() {
        try {
            int result;
            try {
                result = Integer.parseInt(c("Page").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("页码错误");
            }
            int result2;
            try {
                result2 = Integer.parseInt(c("TotalNumber").Text().substring(1));
            } catch (RuntimeException e) {
                result2 = 0;
            }
            if (result > result2) {
                throw new IllegalStateException("已经是最后一页");
            }
            if (result < 1) {
                throw new IllegalStateException("已经是首页");
            }
            Index = result;
            LoadData(GetList(result));
        } catch (RuntimeException ex) {
            c("Page").Text(String.valueOf(Index));
            MessageBox(ex.getMessage());
        }
    }

    private List<Row> GetList(int pageIndex) {
        try {
            return query(pageIndex).rows();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** Pages précédente (Cur) / suivante (Next) : modifie Page, ce qui déclenche Page_TextChanged. */
    public void Next_Click() {
        try {
            int result;
            try {
                result = Integer.parseInt(c("Page").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("页码错误");
            }
            if ("Next".equals(sender)) {
                c("Page").Text(String.valueOf(result + 1));
            } else if ("Cur".equals(sender)) {
                c("Page").Text(String.valueOf(result - 1));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
