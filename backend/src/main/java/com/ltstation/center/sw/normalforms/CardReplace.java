package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.Mifare;
import java.util.List;

/** Card.CardReplace : remplacement d'une carte (nouveau numéro sur Center, écrit sur une carte Mifare vierge ; annulé si l'écriture échoue). */
@SwWindow("Card.CardReplace")
public class CardReplace extends SwForm {

    private long icdev;

    @Override
    protected void init() {
        c("CardTyoeSelect").SelectedIndex(0);
    }

    public void CardReaderBtn_Click() {
        try {
            String name = c("UserNameInput").Text();
            String cno = c("CNOInput").Text();
            String phone = c("PhoneInput").Text();
            if (name.isEmpty() && cno.isEmpty() && phone.isEmpty()) {
                throw new IllegalStateException(L("PlsQueryTerms"));
            }
            CustomerRespone r = BaseFunction.post("Station/GetCardMessage", SW.dto().make("QueryCard", "CompanyID", IniInfo.CompanyID, "CNO", cno,
                "Phone", phone, "UserName", name, "StationID", IniInfo.StationID, "CardType", c("CardTyoeSelect").SelectedIndex()));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            List<Row> list = Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()));
            if (list.isEmpty()) {
                throw new IllegalStateException(L("CantGetCard"));
            }
            c("CardNumberSelect").DataSource(list, "Asn", "CustomerID");
            c("CardNumberSelect").SelectedIndex(0);
            CardNumberSelect_SelectedIndexChanged();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Confirm_Click() {
        try {
            Row selected = c("CardNumberSelect").SelectedRow();
            CustomerRespone r = BaseFunction.post("Station/UpdateCardStatue",
                SW.dto().make("CardStatueChange", "Asn", selected.str("Asn"), "CompanyID", IniInfo.CompanyID, "ChangeType", 2));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            String asn = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())).str("ASN");
            c("TagNumber").Text(asn);
            if (!Card_Deal(asn)) {
                BaseFunction.post("Station/UpdateCardStatue", SW.dto().make("CardStatueChange", "Asn", asn, "CompanyID", IniInfo.CompanyID, "ChangeType", 3));
                throw new IllegalStateException(L("CardWriteError"));
            }
            MessageBox(L("OperateSuccess"));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox("Replaced".equals(ex.getMessage()) ? L("Replaced") : ex.getMessage());
        }
    }

    public void CardNumberSelect_SelectedIndexChanged() {
        try {
            Row card = c("CardNumberSelect").SelectedRow();
            c("AccountInput").Text(card.str("CustomerID"));
            c("BalanceInput").Text(card.dec("Balance").toPlainString());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private boolean Card_Deal(String asn) {
        try {
            icdev = Mifare.rf_init(0, 9600);
            if (icdev <= 0) {
                throw new IllegalStateException(L("CardReaderDisconnect"));
            }
            Mifare.WriteCard(icdev, asn);
            Mifare.rf_exit(icdev);
            icdev = 0;
            return true;
        } catch (RuntimeException ex) {
            Mifare.rf_exit(icdev);
            icdev = 0;
            MessageBox(ex.getMessage());
            return false;
        }
    }
}
