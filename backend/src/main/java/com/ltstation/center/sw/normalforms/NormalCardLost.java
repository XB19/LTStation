package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** Card.CardLost : recherche d'une carte sur Center (nom, n° de pièce, téléphone) et déclaration de perte. */
@SwWindow("Card.CardLost")
public class NormalCardLost extends SwForm {

    public void CardReaderBtn_Click() {
        try {
            String name = c("UserNameInput").Text();
            String cno = c("CNOInput").Text();
            String phone = c("PhoneInput").Text();
            if (name.isEmpty() && cno.isEmpty() && phone.isEmpty()) {
                throw new IllegalStateException(L("PlsQueryTerms"));
            }
            CustomerRespone r = BaseFunction.post("Station/GetCardMessage", SW.dto().make("QueryCard", "CompanyID", IniInfo.CompanyID, "CNO", cno,
                "Phone", phone, "UserName", name, "CardType", c("CardTyoeSelect").SelectedIndex(), "StationID", IniInfo.StationID));
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
            Row card = c("CardNumberSelect").SelectedRow();
            if (card.i("CardStatue") != 0) {
                MessageBox(L("CardStatueNow") + BaseFunction.GetCardStatue(card.i("CardStatue")) + "." + L("CantLost"));
                return;
            }
            CustomerRespone r = BaseFunction.post("Station/UpdateCardStatue",
                SW.dto().make("CardStatueChange", "Asn", card.str("Asn"), "CompanyID", IniInfo.CompanyID, "ChangeType", 1));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            MessageBox(L("OperateSuccess"));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CardNumberSelect_SelectedIndexChanged() {
        try {
            Row card = c("CardNumberSelect").SelectedRow();
            c("AccountInput").Text(card.str("CustomerID"));
            c("NameInput").Text(card.str("UserName"));
            c("PhonePut").Text(card.str("Phone"));
            c("BalanceInput").Text(card.dec("Balance").toPlainString());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
