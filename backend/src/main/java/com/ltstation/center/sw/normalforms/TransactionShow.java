package com.ltstation.center.sw.normalforms;

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
import java.util.Comparator;
import java.util.List;

/** TransactionShow : détail d'une transaction (argument ID) et son règlement (Settlement = 1). */
@SwWindow("NormalForm.TransactionShow")
public class TransactionShow extends SwForm {

    private List<Row> list = new ArrayList<>();

    @Override
    protected void init() {
        GetGroup();
        ShowData(argInt("ID"));
    }

    private void GetGroup() {
        try {
            CustomerRespone r = BaseFunction.post("ExtendUpdate/ExtendGetGroup",
                SW.dto().make("Card_Login_Return", "CompanyID", IniInfo.CompanyID, "EmpID", IniInfo.LoginID));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            list = new ArrayList<>(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())));
            list.add(Row.of("GroupID", 100000001, "Name", L("Person")));
            list.sort(Comparator.comparingInt(t -> t.i("GroupID")));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowData(int ID) {
        try {
            Row t = db().first("NormalTransaction", "{ID} = :i", p("i", ID));
            if (t == null) {
                throw new IllegalStateException(L("TransactionLost"));
            }
            c("NozBox").Text(t.str("NozID"));
            c("GradeBox").Text(t.str("GradeName"));
            c("AmountBox").Text(t.dec("Amount").toPlainString());
            c("VolumeBox").Text(t.dec("Volume").toPlainString());
            c("PriceBox").Text(t.dec("Price").toPlainString());
            c("AmountTotalBox").Text(t.dec("AmountTotal").toPlainString());
            c("VolumeTotalBox").Text(t.dec("VolumeTotal").toPlainString());
            c("TimeBox").Text(BaseFunction.StampToDateTime(t.str("CreateTime")).format(Conv.STD));
            c("UserNameBox").Text(t.str("CardName"));
            c("CarNumberBox").Text(t.str("CarNumber"));
            if (t.i("CardGroup") > 100000001) {
                c("GroupBox").Text(list.stream().filter(g -> g.i("GroupID") == t.i("CardGroup")).map(g -> g.str("Name")).findFirst().orElse(""));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Confirm_Click() {
        try {
            Row t = db().first("NormalTransaction", "{ID} = :i", p("i", argInt("ID")));
            if (t == null) {
                throw new IllegalStateException(L("TransactionLost"));
            }
            if (t.i("Settlement") != 0) {
                throw new IllegalStateException(L("SettlementComplete"));
            }
            t.put("Settlement", 1);
            db().update("NormalTransaction", t);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
