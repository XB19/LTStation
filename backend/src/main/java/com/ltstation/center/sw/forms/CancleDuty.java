package com.ltstation.center.sw.forms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.time.format.DateTimeFormatter;

/** CancleDuty : annulation d'un poste clôturé (les transactions repassent dans le poste courant). */
@SwWindow("CancleDuty")
public class CancleDuty extends SwForm {

    public void CancleDuty_Load() {
        c("ShiftNo").DataSource(db().list("ShiftConfig"), "Name", "ID");
    }

    public void ShiftClose_Click() {
        var tx = db().begin();
        try {
            int id = c("ShiftNo").SelectedRow().i("ID");
            String dutyShift = c("ShiftDT").Value().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + MakeLen(String.valueOf(id), 2);
            db().delete("DutyGunInfo", "{Shift} = :s", p("s", dutyShift));
            db().delete("DutyBrandSales", "{Shift} = :s", p("s", dutyShift));
            db().delete("DutyDenomination", "{Shift} = :s", p("s", dutyShift));
            db().exec(db().sql("OilTransactions", "UPDATE [OilTransactions] SET {Shift} = '1' WHERE {Shift} = :s"), p("s", dutyShift));
            tx.commit();
            MessageBox(L("OperateSuccess"));
        } catch (RuntimeException ex) {
            tx.rollback();
        }
    }

    private static String MakeLen(String str, int len) {
        while (str.length() < len) {
            str = "0" + str;
        }
        return str;
    }
}
