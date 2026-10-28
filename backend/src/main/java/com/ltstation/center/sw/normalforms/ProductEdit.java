package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.BoxBase;
import java.math.BigDecimal;

/** BoxScreenView.ProductEdit : création (argument ID = 0) ou modification d'un produit de boutique, avec journal de stock. */
@SwWindow("BoxScreenView.ProductEdit")
public class ProductEdit extends SwForm {

    private int id() {
        return arg("ID") == null ? 0 : argInt("ID");
    }

    public void ProductEdit_Load() {
        try {
            if (id() != 0) {
                Row product = db().first("ProductList", "{ID} = :i", p("i", id()));
                if (product != null) {
                    c("NameBox").Text(product.str("Name"));
                    c("QuantityBox").Text(product.str("Quantity"));
                    c("PriceBox").Text(product.dec("Price").toPlainString());
                    c("GroupSelect").SelectedIndex(Conv.toInt(product.get("ProductGroup")));
                    c("ProductIDBox").Text(product.str("ProductID"));
                    c("ScoreBox").Text(product.dec("Score").toPlainString());
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CancelBtn_Click() {
        Close("Cancel");
    }

    private BigDecimal dec(String box, String error) {
        try {
            return new BigDecimal(c(box).Text().trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(L(error));
        }
    }

    public void ConfirmBtn_Click() {
        try {
            int result;
            try {
                result = Integer.parseInt(c("QuantityBox").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("FormatError"));
            }
            if (result < 0) {
                throw new IllegalStateException(L("FormatError"));
            }
            BigDecimal result2 = dec("PriceBox", "PriceFormatError");
            BigDecimal result3 = dec("ScoreBox", "ScoreError");
            String name = c("NameBox").Text();
            String productGroup = String.valueOf(c("GroupSelect").SelectedIndex());
            String productID = c("ProductIDBox").Text();
            if (id() == 0) {
                if (db().first("ProductList", "{Name} = :n", p("n", name)) != null) {
                    throw new IllegalStateException(L("ProductNameRepeat"));
                }
                int newId = Conv.toInt(db().insert("ProductList", Row.of("ProductID", productID, "StationID", IniInfo.StationID, "CompanyID", IniInfo.CompanyID,
                    "CreateTime", BaseFunction.ConvertDateTimeToInt(), "Name", name, "Price", result2, "ProductGroup", productGroup, "Quantity", result,
                    "Score", result3)));
                BoxBase.UpdateProductLog(newId, productID, BigDecimal.ZERO, "", "", name, result, result2, 1, result, new String[1]);
            } else {
                Row product = db().first("ProductList", "{ID} = :i", p("i", id()));
                if (product == null) {
                    throw new IllegalStateException(L("ProductInexistence"));
                }
                if (db().first("ProductList", "{Name} = :n AND {ID} <> :i", p("n", name, "i", id())) != null) {
                    throw new IllegalStateException(L("ProductNameRepeat"));
                }
                int quantity = product.i("Quantity");
                product.put("ProductID", productID);
                product.put("Name", name);
                product.put("Price", result2);
                product.put("ProductGroup", productGroup);
                product.put("Quantity", result);
                product.put("Score", result3);
                BoxBase.UpdateProductLog(product.i("ID"), productID, BigDecimal.ZERO, "", "", name, result, result2, 2, result - quantity, new String[1]);
                db().update("ProductList", product);
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
