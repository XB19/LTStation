package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** BoxScreenView.CouponView : pavé « coupon » non modal de la caisse (CouponConfirm). */
@SwWindow("BoxScreenView.CouponView")
public class CouponView extends SwForm {

    public Runnable CouponConfirm;
    public Runnable CloseConfirm;

    public void AddDiscountBtn_Click() {
        if (CouponConfirm != null) {
            CouponConfirm.run();
        }
    }

    public void CouponView_FormClosed() {
        if (CloseConfirm != null) {
            CloseConfirm.run();
        }
    }
}
