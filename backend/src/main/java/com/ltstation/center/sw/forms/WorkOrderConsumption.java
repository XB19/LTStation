package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.WorkOrderData;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** WorkOrderConsumption : carburant consommé par ordre de travail / équipement et énergie, sur une période, avec export. */
@SwWindow("WorkOrderConsumption")
public class WorkOrderConsumption extends SwForm {

    public void WorkOrderConsumption_Load() {
        c("StartDate").Value(LocalDate.now().withDayOfMonth(1).atStartOfDay());
        c("EndDate").Value(LocalDateTime.now());
        SearchBtn_Click();
    }

    private List<Map<String, Object>> query() {
        LocalDateTime start = c("StartDate").Value();
        LocalDateTime end = c("EndDate").Value();
        if (end.isBefore(start)) {
            throw new IllegalStateException(L("EndOutofStart"));
        }
        return WorkOrderData.consumption(start, end, c("WorkOrderBox").Text());
    }

    private static String time(Object v) {
        return v == null ? "" : Conv.toDateTime(v).format(Conv.STD);
    }

    public void SearchBtn_Click() {
        try {
            List<Map<String, Object>> rows = query();
            g("ResultView").RowsClear();
            BigDecimal volume = BigDecimal.ZERO;
            BigDecimal amount = BigDecimal.ZERO;
            long count = 0;
            for (Map<String, Object> r : rows) {
                g("ResultView").RowsAdd(WorkOrderData.str(r.get("work_order_no")), WorkOrderData.str(r.get("equipment_name")),
                    WorkOrderData.str(r.get("category")), WorkOrderData.str(r.get("serial_no")), WorkOrderData.str(r.get("oil_name")),
                    r.get("transaction_count"), r.get("card_count"), Conv.round(Conv.toDouble(r.get("volume")), IniInfo.Vradio),
                    Conv.round(Conv.toDouble(r.get("amount")), IniInfo.Mradio), time(r.get("first_time")), time(r.get("last_time")));
                volume = volume.add(Conv.toDecimal(r.get("volume")));
                amount = amount.add(Conv.toDecimal(r.get("amount")));
                count += Conv.toLong(r.get("transaction_count"));
            }
            c("TotalLabel").Text(rows.size() + " ligne(s) · " + count + " transaction(s) · volume " + Conv.round(volume, IniInfo.Vradio)
                + " · montant " + Conv.round(amount.doubleValue(), IniInfo.Mradio));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ExportBtn_Click() {
        try {
            DataTable dt = new DataTable();
            for (String col : new String[] {"Ordre de travail", "Équipement", "Catégorie", "N° de série", "Énergie", "Transactions", "Cartes",
                "Volume", "Montant", "Première transaction", "Dernière transaction"}) {
                dt.addColumn(col);
            }
            for (Map<String, Object> r : query()) {
                dt.addRow(WorkOrderData.str(r.get("work_order_no")), WorkOrderData.str(r.get("equipment_name")), WorkOrderData.str(r.get("category")),
                    WorkOrderData.str(r.get("serial_no")), WorkOrderData.str(r.get("oil_name")), WorkOrderData.str(r.get("transaction_count")),
                    WorkOrderData.str(r.get("card_count")), WorkOrderData.str(r.get("volume")), WorkOrderData.str(r.get("amount")),
                    time(r.get("first_time")), time(r.get("last_time")));
            }
            String[] out = {""};
            new ExportFile().DataTableToExcel("Consommation par ordre de travail", dt, out);
            DownloadFile(out[0]);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
