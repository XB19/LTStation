package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.BoxBase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** TankConfig.TankHandImport : dépotage saisi à la main (argument ID = cuve), déclaré en entrée de stock à eTIMS (Kenya). */
@SwWindow("TankConfig.TankHandImport")
public class TankHandImport extends SwForm {

    public void TankHandImport_Load() {
    }

    private static BigDecimal parse(String text, String error) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(error);
        }
    }

    private static BigDecimal r2(BigDecimal d) {
        return Conv.netRound(d, 2);
    }

    public void ConfirmBtn_Click() {
        try {
            int tid = argInt("ID");
            BigDecimal volume = parse(c("ImportVolumeBox").Text(), "入库量格式错误");
            BigDecimal price = parse(c("ImportPriceBox").Text(), "入库单价格式错误");
            BigDecimal amount = parse(c("ImportAmountBox").Text(), "入库金额格式错误");
            Row tank = db().first("NormalTank", "{ID} = :i", p("i", tid));
            if (tank == null) {
                throw new IllegalStateException("Lost Tank");
            }
            Row item = db().first("KenyaSelectItemList", "{itemCd} = :c", p("c", tank.str("KenyaCode")));
            if (item == null) {
                throw new IllegalStateException("Lost Code");
            }
            int sarNo = Conv.toInt(db().insert("NormalTankImportHand", Row.of("TankID", tid, "Volume", volume, "Price", price, "Amount", amount,
                "CreateTime", BaseFunction.ConvertDateTimeToInt(), "ImportType", 0)));
            BigDecimal tax = amount.multiply(BigDecimal.valueOf(16)).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN);
            Row io = SW.dto().make("KenyainsertStockIOResponse", "sarNo", sarNo, "orgSarNo", 0, "regTyCd", "M", "sarTyCd", "01",
                "ocrnDt", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), "totItemCnt", 1, "totTaxblAmt", r2(amount), "totTaxAmt", r2(tax),
                "totAmt", r2(amount.add(tax)), "regrId", IniInfo.LoginName, "regrNm", IniInfo.LoginName, "modrId", IniInfo.LoginName,
                "modrNm", IniInfo.LoginName);
            io.put("itemList", List.of(SW.dto().make("KenyainsertStockIOItemListItem", "itemSeq", 1, "itemCd", tank.str("KenyaCode"),
                "itemClsCd", item.str("itemClsCd"), "itemNm", item.str("itemNm"), "pkgUnitCd", item.str("pkgUnitCd"), "pkg", r2(volume),
                "qtyUnitCd", item.str("qtyUnitCd"), "qty", r2(volume), "prc", r2(price), "splyAmt", r2(amount), "taxblAmt", r2(amount), "taxTyCd", "B",
                "taxAmt", r2(tax), "totAmt", r2(amount.add(tax)))));
            String[] error = {""};
            if (!BoxBase.kenyaDeal.insertStockIO(io, error)) {
                throw new IllegalStateException(error[0]);
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
