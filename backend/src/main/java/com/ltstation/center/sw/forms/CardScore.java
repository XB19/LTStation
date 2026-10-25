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
import java.util.List;

/** CardScore : points des cartes des clients (nom, téléphone, immatriculation), par page. */
@SwWindow("CardScore")
public class CardScore extends SwForm {

    private int PageIndex = 1;
    private int TotalPage = 1;

    @Override
    protected void init() {
        LoadData(PageIndex);
    }

    public void LoadData(int pageIndex) {
        try {
            Row obj = SW.dto().make("QueryListPage", "CompanyID", IniInfo.CompanyID, "PageIndex", pageIndex, "QCarNo", c("QueryCarNo").Text(),
                "QName", c("QueryName").Text(), "QPhone", c("QueryPhone").Text());
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCardScoreUser", Wire.json(obj));
            if (r.Result()) {
                Row list = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                TotalPage = list.i("TotalPage");
                c("label4").Text(pageIndex + "/" + TotalPage);
                ShowData(list.rows("list"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ShowData(List<Row> list) {
        try {
            g("TransactionView").RowsClear();
            for (Row item : list) {
                g("TransactionView").RowsAdd(item.get("Asn"), item.get("Name"), item.get("Phone"), item.get("Score"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private String currentId() {
        int index = g("TransactionView").CurrentRowIndex();
        if (index < 0) {
            throw new IllegalStateException("Object reference not set to an instance of an object.");
        }
        return Conv.toStr(g("TransactionView").Cell(index, "ID"));
    }

    public void TransactionView_CellDoubleClick() {
        try {
            ShowDialog("CardScoreMess", r -> { }, "ID", currentId());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void QueryBtn_Click() {
        PageIndex = 1;
        LoadData(PageIndex);
    }

    /** Pages précédente (PreBtn) / suivante (NextBtn). */
    public void NextBtn_Click() {
        try {
            if ("NextBtn".equals(sender)) {
                if (PageIndex >= TotalPage) {
                    throw new IllegalStateException("已处于最后一页");
                }
                PageIndex++;
            } else if ("PreBtn".equals(sender)) {
                if (PageIndex == 1) {
                    throw new IllegalStateException("当前是第一页");
                }
                PageIndex--;
            }
            LoadData(PageIndex);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void TransactionView_CellDoubleClick_1() {
        try {
            ShowDialog("CardScoreMess", r -> {
                if (r.ok()) {
                    PageIndex = 1;
                    LoadData(PageIndex);
                }
            }, "ID", currentId());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
