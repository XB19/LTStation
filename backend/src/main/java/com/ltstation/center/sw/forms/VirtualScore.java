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

/** VirtualScore : comptes de points virtuels de la compagnie (Center). */
@SwWindow("VirtualScore")
public class VirtualScore extends SwForm {

    public int TPage;

    public void VirtualScore_Load() {
        try {
            GetUserScoreList(1);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void GetUserScoreList(int pageIndex) {
        try {
            Row req = SW.dto().make("CenterVirtualRequest", "PageIndex", pageIndex, "Name", c("QueryName").Text(), "Phone", c("QueryPhone").Text(),
                "CompanyID", IniInfo.CompanyID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetScoreList", Wire.json(req));
            if (r.Result()) {
                Row ret = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                if (ret != null) {
                    TPage = ret.i("ToatlPage");
                    List<Row> data = ret.rows("data");
                    if (!data.isEmpty()) {
                        c("label3").Text(pageIndex + "/" + ret.str("ToatlPage"));
                        ShowData(data);
                    }
                    return;
                }
            }
            throw new IllegalStateException("Error");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ShowData(List<Row> data) {
        try {
            g("TransactionView").RowsClear();
            for (Row d : data) {
                g("TransactionView").RowsAdd(d.get("ID"), d.get("UserName"), d.get("Phone"), d.get("Score"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void QueryButton_Click() {
        GetUserScoreList(1);
    }

    public void AddButton_Click() {
        ShowDialog("AddVirtualScore", r -> {
            if (r.ok()) {
                GetUserScoreList(1);
            }
        });
    }

    public void TransactionView_CellDoubleClick() {
        int index = g("TransactionView").CurrentRowIndex();
        if (index < 0) {
            return;
        }
        ShowDialog("VirtualScoreUpdate", r -> {
            if (r.ok()) {
                GetUserScoreList(1);
            }
        }, "ID", Integer.parseInt(Conv.toStr(g("TransactionView").Cell(index, "ID"))));
    }

    public void DiretToButton_Click() {
        try {
            int result;
            try {
                result = Integer.parseInt(c("CurrentPage").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("页码格式错误");
            }
            if (result < 0) {
                throw new IllegalStateException("输入页码过小");
            }
            if (result > TPage) {
                throw new IllegalStateException("输入页码过大");
            }
            GetUserScoreList(result);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CardScore_Click() {
        ShowDialog("CardScore", r -> { });
    }
}
