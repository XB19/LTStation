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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** CardScoreMess(Asn) : points d'une carte, historique (par page) et déduction de points. */
@SwWindow("CardScoreMess")
public class CardScoreMess extends SwForm {

    public String Asn = "";
    public int PageIndex = 1;
    public int TotalPage;

    @Override
    protected void init() {
        Asn = argStr("ID");
        LoadData(1);
        LoadCardScore();
    }

    private void LoadData(int pageIndex) {
        try {
            if (Asn.isEmpty()) {
                throw new IllegalStateException("Informations non valides");
            }
            Row obj = SW.dto().make("ScorePage", "Asn", Asn, "PageIndex", pageIndex);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CardScoreLog", Wire.json(obj));
            if (r.Result()) {
                Row list = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                TotalPage = list.i("TotalPage");
                c("label1").Text(pageIndex + "/" + TotalPage);
                ShowData(list.rows("list"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowData(List<Row> list) {
        try {
            g("TransactionView").RowsClear();
            for (Row item : list) {
                String value = item.i("ScoreType") == 1 ? "Retrait de points" : "Ajout de points";
                g("TransactionView").RowsAdd(item.get("Asn"), item.get("ScoreName"), value, item.get("ScoreValue"), item.get("FinalScore"),
                    BaseFunction.StampToDateTime(item.str("CreateTime")));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void NextBtn_Click() {
        try {
            if ("NextBtn".equals(sender)) {
                if (PageIndex >= TotalPage) {
                    throw new IllegalStateException("Vous êtes déjà sur la dernière page");
                }
                PageIndex++;
            } else if ("PreBtn".equals(sender)) {
                if (PageIndex == 1) {
                    throw new IllegalStateException("Vous êtes sur la première page");
                }
                PageIndex--;
            }
            LoadData(PageIndex);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadCardScore() {
        try {
            if (Asn.isEmpty()) {
                throw new IllegalStateException("Informations introuvables");
            }
            Row obj = SW.dto().make("ScorePage", "Asn", Asn, "PageIndex", PageIndex);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCardScoreMess", Wire.json(obj));
            if (r.Result()) {
                Row u = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                c("CardNo").Text(u.get("Asn"));
                c("CardName").Text(u.get("Name"));
                c("CardPhone").Text(u.get("Phone"));
                BigDecimal score = Conv.toDecimal(u.get("Score"));
                c("CurrentScore").Text((score.scale() > 3 ? score.setScale(3, RoundingMode.HALF_EVEN) : score).toPlainString());
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DelBtn_Click() {
        try {
            BigDecimal result;
            try {
                result = new BigDecimal(c("ScoreValue").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Nombre de points incorrect");
            }
            if (result.signum() < 0) {
                throw new IllegalStateException("Nombre de points non valide");
            }
            Row del = SW.dto().make("ScoreDel", "SR", result, "Asn", Asn);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CardScoreDeal", Wire.json(del));
            if (r.Result()) {
                MessageBox("Opération réussie");
                LoadData(1);
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
