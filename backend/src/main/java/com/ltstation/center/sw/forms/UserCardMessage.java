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

/** UserCardMessage : recherche des cartes des clients sur Center (type, nom, téléphone), par page. */
@SwWindow("UserCardMessage")
public class UserCardMessage extends SwForm {

    public void QuerySearch_Click() {
        try {
            GotData(1, c("CardTypeSelect").SelectedIndex() + 1, c("NameQuery").Text(), c("PhoneQuery").Text());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void GotData(int pageIndex, int cardType, String name, String phone) {
        try {
            Row obj = SW.dto().make("CardGotRequest", "PageIndex", pageIndex, "CardType", cardType, "Name", name, "Phone", phone);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCardMessagePage", Wire.json(obj));
            if (r.Result()) {
                Row list = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                c("Page").Text(pageIndex + "/" + list.str("TotalPage"));
                ShowData(list.rows("data"));
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowData(List<Row> cards) {
        try {
            g("UserView").RowsClear();
            for (Row card : cards) {
                g("UserView").RowsAdd(card.get("CustomerID"), card.get("Name"), card.get("Asn"), BaseFunction.GetCardType(card.str("Asn")),
                    card.get("Phone"), card.get("CarNo"), card.get("Balance"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Pages précédente (Cur) / suivante (Next). */
    public void Next_Click() {
        try {
            String[] array = c("Page").Text().split("/", -1);
            int num = Conv.toInt(array[0]);
            int num2 = Conv.toInt(array[1]);
            if ("Next".equals(sender)) {
                if (num == num2) {
                    throw new IllegalStateException("当前为最后一页");
                }
                num++;
            } else if ("Cur".equals(sender)) {
                if (num == 1) {
                    throw new IllegalStateException("当前为第一页");
                }
                num--;
            }
            GotData(num, c("CardTypeSelect").SelectedIndex() + 1, c("NameQuery").Text(), c("PhoneQuery").Text());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
