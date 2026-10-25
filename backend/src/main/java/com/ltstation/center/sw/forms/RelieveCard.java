package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.CardWrite;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** RelieveCard : déverrouillage d'une carte grise (transaction interrompue) en ligne. */
@SwWindow("RelieveCard")
public class RelieveCard extends SwForm {

    /** Code PIN saisi dans PWDInput (BtnName « ReadBtn »). */
    public String Pwd = "9999";

    public void ReadBtn_Click() {
        try {
            String[] res = {""};
            if (CardWrite.ReadPinID(res)) {
                if (res[0].equals("01")) {
                    ShowDialog("PWDInput", r -> ReadGrey(), "BtnName", "ReadBtn");
                    return;
                }
                ReadGrey();
                return;
            }
            throw new IllegalStateException(L("ReadCardFailed"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Suite de ReadBtn_Click après la saisie éventuelle du code PIN. */
    private void ReadGrey() {
        try {
            CardWrite.UserPublicData[] user = {null};
            CardWrite.GreyCardRead[] grey = {null};
            CardWrite.ReadGrey(Pwd, user, grey, new String[1]);
            CardWrite.GreyCardRead g = grey[0];
            c("RelieveAsn").Text(user[0].ASN);
            c("GreyBalance").Text(g.Balance.toPlainString());
            String t = g.m_sGTime;
            c("GrayTime").Text(t.substring(0, 4) + "-" + t.substring(4, 6) + "-" + t.substring(6, 8) + " " + t.substring(8, 10) + ":" + t.substring(10, 12)
                + ":" + t.substring(12, 14));
            c("TidBox").Text(g.m_sTID);
            c("CTCBox").Text(g.m_sCTC);
            if (g.isGrey) {
                c("RelieveBtn").Enabled(true);
                Row obj = Row.of("Result", false, "Mess", user[0].ASN + "|" + g.m_sGTime + "|" + g.m_sTID + "|" + g.m_sCTC, "TotalCount", 0);
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetGreyCard", Wire.json(obj));
                if (r.Result()) {
                    Row oil = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                    c("Amount").Text(oil.str("TruelyPay"));
                    return;
                }
                throw new IllegalStateException(L("NoGrey"));
            }
            c("RelieveBtn").Enabled(false);
            throw new IllegalStateException(L("NotGreyCard"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage() == null ? "Index was outside the bounds of the array." : ex.getMessage());
        }
    }

    public void RelieveBtn_Click() {
        try {
            if (c("Amount").Text().isEmpty()) {
                if (Confirm(L("DealGreyFore"))) {
                    List<String> list = new ArrayList<>();
                    CardWrite.ReadKeyFromDBNotChange(list);
                    if (!CardWrite.WriteGreyUnlock("0", c("TidBox").Text(), Pwd, list.get(15), new String[1])) {
                        throw new IllegalStateException("Failed");
                    }
                    MessageBox("Success");
                }
            } else {
                try {
                    new BigDecimal(c("Amount").Text().trim());
                } catch (NumberFormatException e) {
                    throw new IllegalStateException(L("AmountError"));
                }
                List<String> list2 = new ArrayList<>();
                CardWrite.ReadKeyFromDBNotChange(list2);
                if (!CardWrite.WriteGreyUnlock(c("Amount").Text(), c("TidBox").Text(), Pwd, list2.get(15), new String[1])) {
                    throw new IllegalStateException("Failed");
                }
                MessageBox("Success");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
        ClearAll();
    }

    private void ClearAll() {
        for (String n : List.of("RelieveAsn", "UserName", "CardAccount", "GrayTime", "TidBox", "CTCBox", "GreyBalance", "Amount")) {
            c(n).Text("");
        }
    }
}
