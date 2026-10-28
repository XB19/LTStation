package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** BoxScreenView.DiscountView : pavé « remise » non modal de la caisse ; le montant saisi est lu par la caisse (TransferConfirm). */
@SwWindow("BoxScreenView.DiscountView")
public class DiscountView extends SwForm {

    public Runnable TransferConfirm;
    public Runnable CloseConfirm;

    public void AddDiscountBtn_Click() {
        if (TransferConfirm != null) {
            TransferConfirm.run();
        }
    }

    public void DiscountView_FormClosed() {
        if (CloseConfirm != null) {
            CloseConfirm.run();
        }
    }
}
