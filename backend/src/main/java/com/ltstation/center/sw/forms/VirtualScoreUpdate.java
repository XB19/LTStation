package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.util.List;

/** VirtualScoreUpdate(SID) : compte de points virtuel (fiche, opérations, historique par page). */
@SwWindow("VirtualScoreUpdate")
public class VirtualScoreUpdate extends SwForm {

    public int ScoreID;
    public int TPage;

    @Override
    protected void init() {
        ScoreID = argInt("ID");
    }

    public void VirtualScoreUpdate_Load() {
        try {
            LoadUserMessage();
            GetScoreLog(1);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadUserMessage() {
        try {
            Row u = SW.dto().make("CenterVirtualUser", "ID", ScoreID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetVirtualUser", Wire.json(u));
            if (r.Result()) {
                Row u2 = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                c("UpdateUserName").Text(u2.get("UserName"));
                c("UpdatePhone").Text(u2.get("Phone"));
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void UpdateBtn_Click() {
        try {
            Row add = SW.dto().make("CenterVirtualUserAdd", "UserName", c("UpdateUserName").Text(), "Phone", c("UpdatePhone").Text(), "EID",
                IniInfo.LoginID, "ScoreID", ScoreID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpdateVirtualScore", Wire.json(add));
            if (r.Result()) {
                MessageBox("操作成功");
                GetScoreLog(1);
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void GetScoreLog(int pageIndex) {
        try {
            Row req = SW.dto().make("CenterVirtualScoreLogRequest", "PageIndex", pageIndex, "ScoreID", ScoreID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetVirtualScoreLog", Wire.json(req));
            if (r.Result()) {
                Row list = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                c("TotalPage").Text(pageIndex + "/" + list.str("TotalPage"));
                TPage = list.i("TotalPage");
                c("CurrentPage").Text(String.valueOf(pageIndex));
                ShowData(list.rows("list"));
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowData(List<Row> list) {
        try {
            g("TransactionView").RowsClear();
            for (Row item : list) {
                String text = switch (item.i("ScoreType")) {
                    case 1 -> "开户";
                    case 2 -> "信息修改";
                    case 3 -> "积分消费";
                    case 4 -> "积分增加";
                    case 5 -> "用户删除";
                    default -> "无";
                };
                g("TransactionView").RowsAdd(item.get("ScoreName"), item.get("ScorePhone"), text, item.get("ScoreValue"), item.get("FinalScore"),
                    BaseFunction.StampToDateTime(item.str("Createtime")), item.get("OperateName"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ScoreDealBtn_Click() {
        try {
            int sel = c("ScoreTypeSelect").SelectedIndex();
            BigDecimal result = null;
            try {
                result = new BigDecimal(c("ScoreValue").Text().trim());
            } catch (NumberFormatException ignored) {
                // vérifié ci-dessous
            }
            if (sel < 0 || result == null) {
                throw new IllegalStateException("信息输入错误");
            }
            Row add = SW.dto().make("CenterVirtualOperateAdd", "ScoreID", ScoreID, "ScoreType", sel, "ScoreValue", result, "EID", IniInfo.LoginID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/DealVirtualScore", Wire.json(add));
            if (r.Result()) {
                MessageBox("操作成功");
                GetScoreLog(1);
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DirectBtn_Click() {
        try {
            int result;
            try {
                result = Integer.parseInt(c("CurrentPage").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("输入页码有误");
            }
            if (result > TPage) {
                throw new IllegalStateException("输入页码过大");
            }
            if (result < 0) {
                throw new IllegalStateException("输入页码过小");
            }
            GetScoreLog(result);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void VirtualScoreUpdate_FormClosed() {
        Close("OK");
    }
}
