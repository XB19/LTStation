package com.ltstation.center.sw.normalforms;

import static com.ltstation.center.legacy.Sugar.p;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.normal.BoxBase;
import java.math.BigDecimal;

/** Articles eTIMS (Kenya) des cuves : code KenyaCode et kenyasaveitem, code répété dans TankAdd, TankUpdate et PriceUpdate. */
final class KenyaItems {

    private KenyaItems() {
    }

    /** kenyasaveitem d'un carburant (type 2, Kenya, unités NT / BA, taxe B). */
    static void saveItem(String itemCd, String itemClsCd, String itemNm, BigDecimal price) {
        BoxBase.kenyaDeal.saveItem(SW.dto().make("kenyasaveitem", "itemCd", itemCd, "itemClsCd", itemClsCd, "itemNm", itemNm, "itemTyCd", "2",
            "orgnNatCd", "KE", "pkgUnitCd", "NT", "qtyUnitCd", "BA", "taxTyCd", "B", "dftPrc", price, "isrcAplcbYn", "N", "useYn", "Y",
            "regrId", IniInfo.LoginName, "regrNm", IniInfo.LoginName, "modrNm", IniInfo.LoginName, "modrId", IniInfo.LoginName), new String[1]);
    }

    /**
     * Nouveau code d'article de la cuve (KE2NTBA0000001, puis le suivant) enregistré auprès d'eTIMS avec le prix du produit ;
     * renvoie le code, ou null si le produit n'a pas de prix.
     */
    static String newTankItem(Row grade) {
        Row cls = SW.db().first("KenyaselectItemCls", "{itemClsLvl} >= 4 AND {useYn} = 'Y' AND {taxTyCd} = 'B' AND UPPER({itemClsNm}) LIKE '%GASOLINE%'",
            null);
        String text = SW.db().scalar(SW.db().sql("NormalTank", "select MAX({KenyaCode}) from [NormalTank]"), null, String.class);
        if (text == null || text.isEmpty()) {
            text = "KE2NTBA0000001";
        } else {
            text = text.substring(0, 7) + BaseFunction.SetStrLen(String.valueOf(Integer.parseInt(text.substring(7)) + 1), 7);
        }
        Row price = SW.db().first("NormalPriceInfo", "{GradeID} = :g", p("g", grade.get("ID")));
        if (price == null) {
            return null;
        }
        // comme l'original : sans classe GASOLINE, l'erreur interrompt l'enregistrement
        saveItem(text, cls.str("itemClsCd"), grade.str("GradeName"), Conv.netRound(price.dec("Price"), 2));
        return text;
    }
}
