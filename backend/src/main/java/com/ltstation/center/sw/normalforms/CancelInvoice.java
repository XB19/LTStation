package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * BoxScreenView.CancelInvoice : affichage d'une facture dans la caisse (ShowDataConfirm) puis, après confirmation,
 * facture d'annulation (BillPayMent 4, montants opposés) et lignes passées à BillComplete = 4.
 */
@SwWindow("BoxScreenView.CancelInvoice")
public class CancelInvoice extends SwForm {

    /** Caisse qui s'abonne à ShowDataConfirm. */
    public interface ShowDataConfirmHandler {
        void F_ShowDataConfirm(String billOrder);
    }

    public void OKBtn_Click() {
        try {
            if (!sender.equals("OKBtn")) {
                if (sender.equals("CloseBtn")) {
                    Close();
                }
                return;
            }
            String billOrder = c("InvoiceBox").Text();
            ((ShowDataConfirmHandler) Owner).F_ShowDataConfirm(billOrder);
            // Task.Run de l'original : l'erreur éventuelle de ResetBill n'est pas affichée
            Ask("Exécuter l'opération ?", yes -> {
                if (yes && ResetBill() == null) {
                    Close("OK");
                }
            });
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** null si l'annulation est faite, sinon le message d'erreur. */
    private String ResetBill() {
        try {
            String BillOrder = c("InvoiceBox").Text();
            if (BillOrder.isEmpty()) {
                throw new IllegalStateException("Transaction Number Lost");
            }
            Row summary = db().first("BillOrderSummary", "{BillOrderNumber} = :b", p("b", BillOrder));
            if (summary == null) {
                throw new IllegalStateException("Transaction Lost");
            }
            String billOrderNumber = LocalDateTime.now().plusSeconds(1).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            db().exec(db().sql("BillOrder", "update [BillOrder] set {BillComplete} = 4 where {BillOrderNumber} = :b"), p("b", BillOrder));
            db().insert("BillOrderSummary", Row.of("SumAmount", summary.dec("SumAmount").negate(), "Discount", summary.get("Discount"),
                "BillOrderNumber", billOrderNumber, "BillPayMent", 4, "CardNumber", summary.get("CardNumber"),
                "CreateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()), "OldOrderNumber", summary.str("BillOrderNumber"),
                "RealAmount", summary.dec("RealAmount").negate()));
            return null;
        } catch (RuntimeException ex) {
            return ex.getMessage();
        }
    }
}
