package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** NormalForm.ScreenNoz : vignette d'une pompe dans la caisse (BoxScreen) ; un clic la choisit (TransferValue). */
@SwWindow("NormalForm.ScreenNoz")
public class ScreenNoz extends SwForm {

    public int BoxPumpID;
    public int CurrentNozID;
    public int CurrentStatue;
    public int Allow;
    public int NozStatue;
    public String GradeName;
    public String Amount;
    public IntConsumer TransferValue;
    public Consumer<String> TransferDeal;

    public ScreenNoz() {
    }

    public ScreenNoz(int boxPumpID) {
        BoxPumpID = boxPumpID;
    }

    @Override
    protected void init() {
        if (arg("PumpID") != null) {
            BoxPumpID = argInt("PumpID");
        }
        c("NozLabel").Text(String.valueOf(BoxPumpID));
        Allow = 4;
        for (Row item : db().list("NormalNoz", "{PumpID} = :p", p("p", BoxPumpID))) {
            if (item.i("AllowAccredit") == 1) {
                Allow = 1;
                break;
            }
        }
        NozStatue = 0;
    }

    public void ScreenNoz_Load() {
    }

    public void UpdateView(int NozStatue, int Allow, String GradeName, String Amount, int NozID) {
        try {
            c("PumpStatue").Image(Allow == 1 ? "res/forbid.png" : "res/Normal.png");
            CurrentNozID = NozID;
            CurrentStatue = NozStatue;
            this.GradeName = GradeName;
            this.Amount = Amount;
            c("GradeLabel").Text(GradeName);
            if (!"-1".equals(Amount)) {
                c("AmountLabel").Text(Amount);
            }
            this.NozStatue = NozStatue;
            if (Allow == 1) {
                c("PumpStatue").Image("res/forbid.png");
                return;
            }
            switch (NozStatue) {
                case 4 -> c("PumpStatue").Image("res/Normal.png");
                case 0 -> {
                    c("NozRunLabel").Text(NozID + " ↓");
                    c("NozLabel").Image("res/ScreenNozOnline.png");
                    c("PumpStatue").Image("res/Normal.png");
                }
                case 1 -> {
                    c("NozRunLabel").Text(NozID + " ↑");
                    c("NozLabel").Image("res/ScreenNozOnline.png");
                    c("PumpStatue").Image("res/Fuel1.png");
                }
                case 3 -> {
                    c("NozRunLabel").Text(NozID + " ↑");
                    c("NozLabel").Image("res/ScreenNozOnline.png");
                    c("PumpStatue").Image("res/Fueling.gif");
                }
                default -> {
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void ScreenNoz_Click() {
        try {
            if (TransferValue != null) {
                TransferValue.accept(BoxPumpID);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void UpdateSelect(int Select) {
        c("panel1").BackgroundImage(Select == 0 ? "res/NorBase.png" : "res/bg2.jpg");
    }
}
