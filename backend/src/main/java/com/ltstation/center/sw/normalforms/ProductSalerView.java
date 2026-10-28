package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.BoxBase;
import java.math.BigDecimal;

/** BoxScreenView.ProductSalerView : vente d'un produit de boutique (arguments ID, BillOrderNumber) ; stock et journal mis à jour. */
@SwWindow("BoxScreenView.ProductSalerView")
public class ProductSalerView extends SwForm {

    public void ProductSalerView_Load() {
        try {
            Row product = db().first("ProductList", "{ID} = :i", p("i", argInt("ID")));
            if (product != null) {
                c("NameBox").Text(product.str("Name"));
                c("PriceBox").Text(product.dec("Price").toPlainString());
                c("GroupSelect").Text(product.str("ProductGroup"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
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

    public void QuantityBox_TextChanged() {
        try {
            Integer result = parseInt(c("QuantityBox").Text());
            if (result == null) {
                throw new IllegalStateException("数量格式错误");
            }
            BigDecimal price = parse(c("PriceBox").Text());
            c("AmountBox").Text((price == null ? BigDecimal.ZERO : price).multiply(BigDecimal.valueOf(result)).toPlainString());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CancelBtn_Click() {
        Close("Cancel");
    }

    public void ConfirmBtn_Click() {
        try {
            Integer result = parseInt(c("QuantityBox").Text());
            if (result == null || result < 0) {
                throw new IllegalStateException("数据格式错误");
            }
            BigDecimal amount = parse(c("AmountBox").Text());
            if (amount == null) {
                throw new IllegalStateException("金额格式错误");
            }
            if (parse(c("PriceBox").Text()) == null) {
                throw new IllegalStateException("单价格式错误");
            }
            Row product = db().first("ProductList", "{ID} = :i", p("i", argInt("ID")));
            if (product == null) {
                throw new IllegalStateException("Lost");
            }
            if (result > product.i("Quantity")) {
                throw new IllegalStateException("数量超标");
            }
            BoxBase.UpdateProductLog(product.i("ID"), product.str("ProductID"), amount, argStr("BillOrderNumber"), "", product.str("Name"),
                product.i("Quantity") - result, product.dec("Price"), 3, result, new String[1]);
            product.put("Quantity", product.i("Quantity") - result);
            db().update("ProductList", product);
            MessageBox("Deal Success");
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
