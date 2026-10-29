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
import com.ltstation.center.sw.hw.Mifare;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Card.Card_Balance_Deal : lecture d'une carte de groupe (Mifare bloc 20) puis crédit / débit du solde sur Center. */
@SwWindow("Card.Card_Balance_Deal")
public class Card_Balance_Deal extends SwForm {

    private long icdev;
    private Row card_Deal;

    public void CardReadBtn_Click() {
        try {
            String asn = null;
            icdev = Mifare.rf_init(0, 9600);
            if (icdev > 0) {
                Mifare.Select(icdev);
                if (Mifare.rf_authentication_key(icdev, 0, 20, Mifare.Key()) != 0) {
                    throw new IllegalStateException("Read Card Error");
                }
                asn = "00000000" + Mifare.rf_read_hex(icdev, 20).substring(0, 12);
                Mifare.rf_exit(icdev);
                icdev = 0;
            }
            CustomerRespone r = BaseFunction.post("ExtendUpdate/GetCardMessage", SW.dto().make("UserPublicData", "ASN", asn, "CompanyID", IniInfo.CompanyID));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            Row deal = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            card_Deal = deal;
            if (deal.i("GroupID") != 100000001) {
                throw new IllegalStateException(L("CardCantdoit"));
            }
            String pa = deal.str("Password");
            if (!pa.isEmpty() && !"9999".equals(pa)) {
                ShowDialog("Card.PasswordInput", res -> {
                    if ("OK".equals(res.result())) {
                        ShowCard(deal);
                    } else {
                        MessageBox(L("PasswordError"));
                    }
                }, "PA", pa);
                return;
            }
            ShowCard(deal);
        } catch (RuntimeException ex) {
            Mifare.rf_exit(icdev);
            icdev = 0;
            MessageBox(ex.getMessage());
        }
    }

    private void ShowCard(Row deal) {
        c("CardNoBox").Text(deal.str("Asn"));
        c("UserNameBox").Text(deal.str("UserName"));
        c("BalanceBox").Text(Conv.netRound(deal.dec("Balance"), IniInfo.Mradio).toPlainString());
        c("ScoreBal").Text(deal.dec("ScoreBal").setScale(0, RoundingMode.HALF_EVEN).toPlainString());
    }

    public void ConfirmBtn_Click() {
        try {
            if (card_Deal == null) {
                throw new IllegalStateException(L("PlsReadCard"));
            }
            if (card_Deal.i("GroupID") != 100000001) {
                throw new IllegalStateException(L("CardCantdoit"));
            }
            BigDecimal result;
            try {
                result = new BigDecimal(c("AmountBox").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Amount Format Error");
            }
            if (result.signum() < 0) {
                throw new IllegalStateException("Amount Format Error");
            }
            CustomerRespone r = BaseFunction.post("ExtendUpdate/CardBalanceChange", SW.dto().make("Card_Balance_Deal", "Asn", c("CardNoBox").Text(),
                "C_Type", c("OperateBox").SelectedIndex(), "Amount", result, "DisAmount", BigDecimal.ZERO, "EmpID", IniInfo.LoginID,
                "CompanyID", IniInfo.CompanyID, "StationID", IniInfo.StationID, "LoginType", 1));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            ClearInput();
            MessageBox("Opération réussie");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ClearInput() {
        c("CardNoBox").Text("");
        c("UserNameBox").Text("");
        c("BalanceBox").Text("");
        c("AmountBox").Text("");
    }
}
