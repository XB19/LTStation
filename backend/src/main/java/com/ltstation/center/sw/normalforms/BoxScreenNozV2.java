package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * BoxScreenViewV2.BoxScreenNozV2 : vignette d'une pompe dans la caisse V2 (état, produit, montant, transactions à facturer
 * rafraîchies chaque seconde) ; un clic choisit la pompe (SelectNoz).
 */
@SwWindow("BoxScreenViewV2.BoxScreenNozV2")
public class BoxScreenNozV2 extends SwForm {

    public int BoxPumpID;
    public String BoxAmount;
    public java.math.BigDecimal BoxVolume;
    public String BoxGradeName;
    public int BoxNozStatue;
    public int CurrentNozID;
    public int BoxAllow;
    public IntConsumer SelectNoz;

    public BoxScreenNozV2() {
    }

    public BoxScreenNozV2(int PumpID) {
        BoxPumpID = PumpID;
    }

    @Override
    protected void init() {
        if (arg("PumpID") != null) {
            BoxPumpID = argInt("PumpID");
        }
        c("PumpIDLabel").Text(String.valueOf(BoxPumpID));
        for (String n : List.of("flowLayoutPanel1", "flowLayoutPanel2", "NozHandLabel", "NozStatuePanel", "PumpIDLabel", "RealAmountLabel",
            "RealCountLabel", "RealGradeNameLabel")) {
            On(n, "Click", "BoxScreenNozV2_Click");
        }
        // SetCountThread : toutes les secondes
        Timer("ShowTransactionCount", 1000);
        ShowTransactionCount();
    }

    public void BoxScreenNozV2_Click() {
        try {
            if (SelectNoz != null) {
                SelectNoz.accept(BoxPumpID);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void ClearImage(int Select) {
        c("$this").BackgroundImage(Select == 0 ? "res/NozBackGround.png" : "res/RedNozBackGround.png");
    }

    public void UpdateView(int NozStatue, int Allow, String GradeName, String Amount, int NozID) {
        try {
            c("NozStatuePanel").BackgroundImage(Allow == 1 ? "res/forbid.png" : "res/BoxScreenPump.png");
            CurrentNozID = NozID;
            BoxNozStatue = NozStatue;
            BoxGradeName = GradeName;
            BoxAmount = Amount;
            c("RealGradeNameLabel").Text(GradeName);
            if (!"-1".equals(Amount)) {
                c("RealAmountLabel").Text(Amount);
            }
            if (Allow == 1) {
                c("NozStatuePanel").BackgroundImage("res/forbid.png");
                return;
            }
            switch (NozStatue) {
                case 4 -> c("NozStatuePanel").BackgroundImage("res/BoxScreenPump.png");
                case 0 -> {
                    c("NozHandLabel").Text(NozID + " ↓");
                    c("NozStatuePanel").BackgroundImage("res/BoxScreenPump.png");
                }
                case 1, 3 -> {
                    c("NozHandLabel").Text(NozID + " ↑");
                    c("NozStatuePanel").BackgroundImage("res/BoxScreenNozRun.png");
                }
                default -> {
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void ShowTransactionCount() {
        try {
            List<Object> inValues = new ArrayList<>();
            for (Row noz : db().list("NormalNoz", "{PumpID} = :p", p("p", BoxPumpID))) {
                inValues.add(noz.get("NozID"));
            }
            long count = inValues.isEmpty() ? 0 : db().count("NormalTransaction",
                "{NozID} IN (:n) AND ({SaleCheck} = '' OR {SaleCheck} IS NULL) AND {Amount} <> 0 AND {Volume} <> 0", p("n", inValues));
            c("RealCountLabel").Text(String.valueOf(count));
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
