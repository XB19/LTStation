package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/** AddDisCountForm(id) : création (id 0) / modification / suppression d'une remise. */
@SwWindow("AddDisCountForm")
public class AddDisCountForm extends SwForm {

    private int sid;

    @Override
    protected void init() {
        sid = argInt("ID");
    }

    public void SaveButton_Click() {
        var tx = db().begin();
        try {
            if (c("DisCountName").Text().isEmpty() || c("DisCountValue").Text().isEmpty()) {
                throw new IllegalStateException(L("InformationLoss"));
            }
            BigDecimal result;
            try {
                result = new BigDecimal(c("DisCountValue").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("DiscountError"));
            }
            if (sid != 0) {
                Row d = db().first("DisCount", "{ID} = :id", p("id", sid));
                if (d != null) {
                    d.put("Name", c("DisCountName").Text());
                    d.put("Count", result);
                    db().update("DisCount", d);
                }
            } else {
                db().insert("DisCount", Row.of("ID", Conv.toInt(c("DisCountID").Text()), "Name", c("DisCountName").Text(),
                    "Count", Conv.toDecimal(c("DisCountValue").Text())));
            }
            tx.commit();
            MessageBox(L("OperateSuccess"));
            Close("OK");
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    public void AddDisCountForm_Load() {
        if (sid != 0) {
            c("DeleteButton").Visible(true);
            Row d = db().first("DisCount", "{ID} = :id", p("id", sid));
            if (d != null) {
                c("DisCountID").Text(d.str("ID"));
                c("DisCountName").Text(d.str("Name"));
                c("DisCountValue").Text(d.str("Count"));
            }
        } else {
            Integer num = db().scalar(db().sql("DisCount", "SELECT MAX({ID}) FROM [DisCount]"), null, Integer.class);
            if (num == null) {
                throw new IllegalStateException("Sequence contains no elements");
            }
            c("DisCountID").Text(String.valueOf(num + 1));
        }
    }

    public void DeleteButton_Click() {
        var tx = db().begin();
        try {
            Row d = db().first("DisCount", "{ID} = :id", p("id", sid));
            if (d != null) {
                db().deleteEntity("DisCount", d);
            }
            tx.commit();
            MessageBox(L("OperateSuccess"));
            Close("OK");
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }
}
