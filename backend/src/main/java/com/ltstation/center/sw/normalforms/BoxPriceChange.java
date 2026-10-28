package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** BoxScreenView.BoxPriceChange : prix en service ; un clic ouvre BoxPriceUpdate sur la ligne. */
@SwWindow("BoxScreenView.BoxPriceChange")
public class BoxPriceChange extends SwForm {

    private List<Row> prices() {
        return db().list("NormalPriceInfo", "{IsUsing} = true ORDER BY {ID}", null);
    }

    public void BoxPriceChange_Load() {
        try {
            ShowData(prices());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowData(List<Row> list) {
        try {
            g("OilDataView").RowsClear();
            for (Row item : list) {
                Row grade = db().first("NormalGrade", "{ID} = :g", p("g", item.get("GradeID")));
                // Sans produit, l'original ajoute une ligne réduite à la seule colonne ID
                if (grade == null) {
                    g("OilDataView").RowsAdd(item.get("ID"));
                    continue;
                }
                g("OilDataView").RowsAdd(item.get("ID"), grade.str("GradeName"), Conv.netRound(item.dec("Price"), IniInfo.Pradio), item.get("Discount"),
                    item.get("AnnouncementPrice"), item.get("ShippingCost"), item.get("RateOfChange"), item.get("TaxCost"),
                    BaseFunction.StampToDateTime(item.str("ValidTime")).format(Conv.STD));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void OilDataView_CellClick() {
        try {
            int index = g("OilDataView").CurrentRowIndex();
            int id = Integer.parseInt(String.valueOf(g("OilDataView").Cell(index, 0)));
            ShowDialog("BoxScreenView.BoxPriceUpdate", r -> {
                if ("OK".equals(r.result())) {
                    ShowData(prices());
                }
            }, "ID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
