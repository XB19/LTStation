package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.PrintClass;
import java.util.ArrayList;
import java.util.List;

/** ScoreCheck(D) : validation manuelle du paiement d'une transaction en espèces et réimpression du ticket. */
@SwWindow("ScoreCheck")
public class ScoreCheck extends SwForm {

    private long ID;

    @Override
    protected void init() {
        ID = argInt("D");
    }

    private Row tx() {
        return db().first("OilTransactions", "{ID} = :id", p("id", ID));
    }

    public void QueryBtn_Click() {
        try {
            if (ID == 0L) {
                throw new IllegalStateException("记录不存在");
            }
            Row t = tx();
            if (t == null) {
                throw new IllegalStateException("记录不存在");
            }
            String head = t.str("Asn").substring(0, 6);
            if (head.equals("000000") || head.equals("010004")) {
                t.put("PayMentDealID", 1);
                db().update("OilTransactions", t);
                Close("OK");
                return;
            }
            throw new IllegalStateException("该记录不需要手动处理");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ScoreCheck_Load() {
        try {
            Row t = tx();
            c("IDBox").Text(t.str("ID"));
            c("NozBox").Text(t.str("POSP"));
            c("AmountBox").Text(t.str("TruelyPay"));
            c("VolBox").Text(t.str("Volume"));
            c("GradeBox").Text(BaseFunction.GetOilNameByID(t.i("GradeSpecificID")));
            c("TimeBox").Text(t.get("TrueTime"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage() == null ? "Object reference not set to an instance of an object." : ex.getMessage());
        }
    }

    public void skinButton1_Click() {
        try {
            Row t = tx();
            if (t == null) {
                throw new IllegalStateException("记录不存在");
            }
            List<String> list = new ArrayList<>();
            list.add(L("NozID") + "&" + t.str("POSP"));
            list.add(L("OilDataType") + "&" + BaseFunction.GetTType(t.i("TType"), t.str("Asn")));
            list.add(L("CarNo") + "&" + t.str("CarNumber"));
            list.add(L("Amount") + "&" + c("AmountBox").Text());
            list.add(L("Volume") + "&" + c("VolBox").Text());
            list.add(L("Price") + "&" + t.str("PPU"));
            list.add(L("Energy") + "&" + BaseFunction.GetOilNameByID(t.i("GradeSpecificID")));
            list.add(L("CardNo") + "&" + t.str("Asn"));
            list.add(L("Time") + "&" + c("TimeBox").Text().replace(" ", "."));
            PrintClass pc = new PrintClass();
            pc.DataList.addAll(list);
            PrintTicket(pc);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
