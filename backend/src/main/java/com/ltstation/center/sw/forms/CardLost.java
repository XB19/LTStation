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
import java.util.ArrayList;
import java.util.List;

/** CardLost : mise en opposition d'une carte de client (recherche sur Center) et cartes des employés. */
@SwWindow("CardLost")
public class CardLost extends SwForm {

    public void CardLost_Load() {
        c("TypeSelect").SelectedIndex(0);
        c("BalanceChange").SelectedIndex(0);
    }

    public void SearchButton_Click() {
        try {
            if (c("TypeNo").Text().isEmpty() && c("UserName").Text().isEmpty() && c("SPhone").Text().isEmpty()) {
                throw new IllegalStateException(L("NeedQueryCondition"));
            }
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", c("TypeSelect").SelectedIndex(), "Mess",
                c("CardTypeSelect").SelectedIndex() + "|" + c("TypeNo").Text() + "|" + c("UserName").Text() + "|" + c("SPhone").Text());
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CardLostGetMess", Wire.json(obj));
            if (r.Result()) {
                List<Object> list = new ArrayList<>();
                Object parsed = Wire.parse(BaseFunction.DecryptByAES(r.Mess()));
                if (parsed instanceof List<?> l) {
                    list.addAll(l);
                }
                c("LostCardNoSelect").Items(list);
                if (!list.isEmpty()) {
                    c("LostCardNoSelect").SelectedIndex(0);
                    return;
                }
                c("LostCardNoSelect").Text("");
                c("UserCardName").Text("");
                c("CardTime").Text("");
                c("CardOpen").Text("");
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ConfirmButton_Click() {
        try {
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", c("LostCardNoSelect").Text(), "Start", IniInfo.LoginID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CardLost", Wire.json(obj));
            if (r.Result()) {
                MessageBox(L("LostSuccess"));
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Onglet « employés » : liste des cartes des employés. */
    public void BalanceChange_SelectedIndexChanged() {
        try {
            if (c(sender).SelectedIndex() == 1) {
                GetEmpLog();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowEmpLog(List<Row> list) {
        g("EmpView").RowsClear();
        for (Row item : list) {
            g("EmpView").RowsAdd(item.get("ASN"), item.get("Name"), BaseFunction.GetStationName(item.i("StationID")), item.get("IssTime"), L("Normal"));
        }
    }

    private void GetEmpLog() {
        try {
            g("EmpView").RowsClear();
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CardLostGetMessEmployee", Wire.json(obj));
            if (r.Result()) {
                ShowEmpLog(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())));
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void EmpView_CellDoubleClick() {
        ShowDialog("EmpLost", r -> {
            if (r.ok()) {
                GetEmpLog();
            }
        }, "Asn", Conv.toStr(g("EmpView").Cell(e.i("RowIndex"), 0)));
    }

    public void LostCardNoSelect_SelectedIndexChanged() {
        Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", c("LostCardNoSelect").Text());
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CardChangeGetAsnMess", Wire.json(obj));
        if (r.Result()) {
            Row m = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            c("UserCardName").Text(m.get("UserName"));
            c("UserID").Text(m.get("UserID"));
            c("CardTime").Text(m.get("IssueDateTime"));
            c("CardOpen").Text(m.get("StationName"));
            return;
        }
        throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
    }
}
