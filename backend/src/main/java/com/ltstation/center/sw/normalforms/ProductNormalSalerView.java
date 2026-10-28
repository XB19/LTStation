package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.BaseFunction.Holder;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.Mifare;
import com.ltstation.center.sw.normal.BoxBase;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BoxScreenViewV2.ProductNormalSalerView : vente d'un produit (argument ID) payée par une carte client Mifare,
 * sur le solde (opération 4) ou les points (5) ; validée par Center (Station/DealStationSaler).
 */
@SwWindow("BoxScreenViewV2.ProductNormalSalerView")
public class ProductNormalSalerView extends SwForm {

    private long icdev;
    private Row customerCardInfos;
    private Row customerAccounts;

    private Row product() {
        return db().first("ProductList", "{ID} = :i", p("i", argInt("ID")));
    }

    public void CardReadBtn_Click() {
        try {
            icdev = Mifare.rf_init(0, 9600);
            if (icdev <= 0) {
                throw new IllegalStateException(L("ReadCardFailed"));
            }
            Mifare.Select(icdev);
            if (Mifare.rf_authentication_key(icdev, 0, 20, Mifare.Key()) != 0) {
                throw new IllegalStateException(L("ReadCardFailed"));
            }
            String asn = "00000000" + Mifare.rf_read_hex(icdev, 20).substring(0, 12);
            Mifare.rf_exit(icdev);
            icdev = 0;
            Holder h = BaseFunction.GetCustomerMess(asn);
            if (!h.ok()) {
                return;
            }
            customerCardInfos = h.b();
            customerAccounts = h.c();
            if (customerCardInfos.i("Suspend") != 0) {
                throw new IllegalStateException(L("CardStatueError"));
            }
            String pa = customerCardInfos.str("CipherFlg");
            if (!pa.isEmpty() && !"9999".equals(pa)) {
                ShowDialog("Card.PasswordInput", r -> {
                    if ("OK".equals(r.result())) {
                        CardRead(asn);
                    } else {
                        MessageBox(L("PasswordError"));
                    }
                }, "PA", pa);
                return;
            }
            CardRead(asn);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            Mifare.rf_exit(icdev);
            icdev = 0;
        }
    }

    private void CardRead(String asn) {
        c("CardNumberBox").Text(asn);
        if (c("ConsumSelect").SelectedIndex() == 0) {
            ShowBalance(asn, customerAccounts.dec("Balance").setScale(3, RoundingMode.HALF_EVEN), 4);
        } else {
            ShowBalance(asn, customerCardInfos.dec("Score"), 5);
        }
    }

    private void ShowBalance(String CardNumber, BigDecimal Balance, int Op) {
        try {
            if (customerAccounts == null || customerCardInfos == null) {
                return;
            }
            BigDecimal sum = db().scalar(db().sql("NormalTransaction",
                "select coalesce(sum({Amount}), 0) from [NormalTransaction] where {CardNo} = :c and {CreateTime} > :t"),
                p("c", CardNumber, "t", BaseFunction.ConvertDateTimeToInt(customerAccounts.dt("UpdateDatetime"))), BigDecimal.class);
            BigDecimal sum2 = db().scalar(db().sql("OilSalerTemp",
                "select coalesce(sum({Amount}), 0) from [OilSalerTemp] where {CardNumber} = :c and {UpdateTime} > :t and {Operater} = :o"),
                p("c", CardNumber, "t", customerCardInfos.dt("UpdateDatetime"), "o", Op), BigDecimal.class);
            c("BalanceBox").Text((Op == 4 ? Balance.subtract(sum).subtract(sum2) : Balance.subtract(sum2)).toPlainString());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ProductNormalSalerView_Load() {
        try {
            Row product = product();
            if (product != null) {
                c("NameBox").Text(product.str("Name"));
                c("PriceBox").Text(product.dec("Price").toPlainString());
            }
            // SelectedIndex = 0 déclenche ConsumSelect_SelectedIndexChanged
            c("ConsumSelect").SelectedIndex(0);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ConsumSelect_SelectedIndexChanged() {
        try {
            Row product = product();
            int num;
            if (c("ConsumSelect").SelectedIndex() == 0) {
                c("ScoreBalanceLabel").Text(L("Balance"));
                c("BalanceBox").Text(customerAccounts == null ? "0" : customerAccounts.dec("Balance").toPlainString());
                c("ScorePriceLabel").Text(L("UnitPrice"));
                c("PriceBox").Text(product.dec("Price").toPlainString());
                num = 4;
            } else {
                c("ScoreBalanceLabel").Text(L("Score"));
                c("BalanceBox").Text(customerCardInfos == null ? "0" : customerCardInfos.dec("Score").toPlainString());
                c("ScorePriceLabel").Text(L("ScorePrice"));
                c("PriceBox").Text(product.dec("Score").toPlainString());
                num = 5;
            }
            Integer q = parseInt(c("QuantityBox").Text());
            BigDecimal price = parse(c("PriceBox").Text());
            if (q != null && price != null) {
                c("AmountBox").Text(price.multiply(BigDecimal.valueOf(q)).toPlainString());
            }
            if (customerCardInfos != null && customerAccounts != null) {
                ShowBalance(customerCardInfos.str("ASN"), num == 4 ? customerAccounts.dec("Balance") : customerCardInfos.dec("Score"), num);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CancelBtn_Click() {
        Close("Cancel");
    }

    private static BigDecimal parse(String t) {
        try {
            return new BigDecimal(t.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String t) {
        try {
            return Integer.parseInt(t.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void ConfirmBtn_Click() {
        Sugar.Tx tx = db().begin();
        try {
            Integer result = parseInt(c("QuantityBox").Text());
            if (result == null || result < 0) {
                throw new IllegalStateException(L("FormatError"));
            }
            BigDecimal result2 = parse(c("AmountBox").Text());
            if (result2 == null) {
                throw new IllegalStateException(L("AmountFormatError"));
            }
            BigDecimal result3 = parse(c("PriceBox").Text());
            if (result3 == null) {
                throw new IllegalStateException(L("PriceFormatError"));
            }
            Row productList = product();
            if (productList == null) {
                throw new IllegalStateException(L("ProductInexistence"));
            }
            if (result > productList.i("Quantity")) {
                throw new IllegalStateException(L("QuantityOver"));
            }
            BigDecimal result4 = parse(c("BalanceBox").Text());
            if ((result4 == null ? BigDecimal.ZERO : result4).compareTo(result2) < 0) {
                throw new IllegalStateException(L("BalanceNotEnough"));
            }
            String text = c("CardNumberBox").Text();
            int selectedIndex = c("ConsumSelect").SelectedIndex();
            int num = selectedIndex == 1 ? 5 : 4;
            if (text.isEmpty() || customerCardInfos == null) {
                throw new IllegalStateException(L("PlsReadCard"));
            }
            String[] error = new String[1];
            BoxBase.UpdateProductLog(productList.i("ID"), productList.str("ProductID"), result2, "", text, productList.str("Name"),
                productList.i("Quantity") - result, result3, num, result, error);
            productList.put("Quantity", productList.i("Quantity") - result);
            db().update("ProductList", productList);
            BoxBase.AddCardLogTemp(text, num, result2, error);
            CustomerRespone r = BaseFunction.post("Station/DealStationSaler", SW.dto().make("StationSalerDeal", "SelectIndex", selectedIndex,
                "StationID", IniInfo.StationID, "CompanyID", IniInfo.CompanyID, "CardNumber", text, "Amount", result2, "Operater", IniInfo.LoginName,
                "OperateType", num, "Price", result3, "ProductName", c("NameBox").Text(), "Quantity", result));
            if (!r.Result()) {
                throw new IllegalStateException(L("ServerRefuse"));
            }
            tx.commit();
            MessageBox(L("OperateSuccess"));
            Close("OK");
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    public void QuantityBox_TextChanged() {
        try {
            Integer result = parseInt(c("QuantityBox").Text());
            if (result == null) {
                throw new IllegalStateException(L("FormatError"));
            }
            BigDecimal price = parse(c("PriceBox").Text());
            c("AmountBox").Text((price == null ? BigDecimal.ZERO : price).multiply(BigDecimal.valueOf(result)).toPlainString());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
