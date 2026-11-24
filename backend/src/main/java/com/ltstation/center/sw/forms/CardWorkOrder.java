package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.WorkOrderData;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CardWorkOrder(EquipID, ASN) : ordre de travail d'une carte. Recherche par n° complet ou 5 derniers chiffres (plusieurs cartes
 * possibles : l'utilisateur choisit dans la liste), ordre en cours, nouvel ordre (équipement et/ou n°, date d'effet), historique.
 */
@SwWindow("CardWorkOrder")
public class CardWorkOrder extends SwForm {

    private String asn;
    private int equipId;

    @Override
    protected void init() {
        equipId = argInt("EquipID");
        asn = argStr("ASN");
        List<Row> items = new ArrayList<>();
        items.add(Row.of("ID", 0, "Text", ""));
        for (Map<String, Object> e : WorkOrderData.equipments("")) {
            String no = WorkOrderData.str(e.get("work_order_no"));
            items.add(Row.of("ID", e.get("id"), "Text", e.get("name") + (no.isEmpty() ? "" : " · OT " + no)));
        }
        c("EquipBox").DataSource(items, "Text", "ID");
        c("FromDate").Value(LocalDateTime.now());
    }

    public void CardWorkOrder_Load() {
        Search("");
        if (asn != null && !asn.isEmpty()) {
            Pick(asn);
        }
    }

    public void SearchBtn_Click() {
        Search(c("CardBox").Text());
    }

    private void Search(String q) {
        try {
            List<Map<String, Object>> cards = WorkOrderData.searchCards(q);
            g("CardView").RowsClear();
            for (Map<String, Object> r : cards) {
                g("CardView").RowsAdd(r.get("asn"), r.get("kind"), WorkOrderData.str(r.get("holder")), WorkOrderData.str(r.get("vehicle")),
                    WorkOrderData.str(r.get("work_order_no")), WorkOrderData.str(r.get("equipment_name")),
                    r.get("valid_from") == null ? "" : Conv.toDateTime(r.get("valid_from")).format(Conv.STD));
            }
            if (!q.isBlank()) {
                if (cards.size() == 1) {
                    Pick((String) cards.get(0).get("asn"));
                } else if (cards.isEmpty()) {
                    MessageBox("Aucune carte trouvée");
                } else {
                    c("SelectedCard").Text(cards.size() + " cartes correspondent : cliquez sur la bonne dans la liste");
                    c("SelectedCard").ForeColor("darkorange");
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CardView_CellClick() {
        int i = e.i("RowIndex");
        if (i >= 0 && i < g("CardView").RowCount()) {
            Pick(String.valueOf(g("CardView").Cell(i, "ASN")));
        }
    }

    /** Carte choisie : informations, ordre en cours pré-rempli, historique. */
    private void Pick(String cardNo) {
        try {
            Map<String, Object> card = WorkOrderData.card(cardNo);
            asn = (String) card.get("asn");
            List<Map<String, Object>> history = WorkOrderData.history(asn);
            Map<String, Object> current = history.stream().filter(h -> h.get("valid_to") == null).findFirst().orElse(null);
            c("SelectedCard").Text(asn + " · " + card.get("kind") + " · " + WorkOrderData.str(card.get("holder"))
                + (current == null ? " · aucun ordre en cours" : " · ordre en cours : " + WorkOrderData.str(current.get("work_order_no"))));
            c("SelectedCard").ForeColor("green");
            if (equipId != 0) {
                c("EquipBox").SelectedValue(equipId);
                c("WorkOrderBox").Text(WorkOrderData.str(WorkOrderData.equipment(equipId).get("work_order_no")));
            } else if (current != null) {
                c("EquipBox").SelectedValue(current.get("equipment_id") == null ? 0 : current.get("equipment_id"));
                c("WorkOrderBox").Text(WorkOrderData.str(current.get("work_order_no")));
            }
            c("FromDate").Value(LocalDateTime.now());
            g("HistoryView").RowsClear();
            for (Map<String, Object> h : history) {
                g("HistoryView").RowsAdd(WorkOrderData.str(h.get("work_order_no")), WorkOrderData.str(h.get("equipment_name")),
                    Conv.toDateTime(h.get("valid_from")).format(Conv.STD),
                    h.get("valid_to") == null ? "En cours" : Conv.toDateTime(h.get("valid_to")).format(Conv.STD),
                    WorkOrderData.str(h.get("memo")), WorkOrderData.str(h.get("create_by")));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void EquipBox_SelectedIndexChanged() {
        try {
            Object v = c("EquipBox").SelectedValue();
            int id = v == null ? 0 : Conv.toInt(v);
            if (id != 0) {
                c("WorkOrderBox").Text(WorkOrderData.str(WorkOrderData.equipment(id).get("work_order_no")));
            }
        } catch (RuntimeException ignored) {
            // liste en cours de remplissage
        }
    }

    public void SaveBtn_Click() {
        try {
            if (asn == null || asn.isEmpty()) {
                throw new IllegalStateException("Choisissez d'abord une carte");
            }
            Object v = c("EquipBox").SelectedValue();
            int id = v == null ? 0 : Conv.toInt(v);
            WorkOrderData.assign(asn, c("WorkOrderBox").Text(), id == 0 ? null : id, c("FromDate").Value(), c("MemoBox").Text());
            MessageBox("Ordre de travail enregistré pour la carte " + asn);
            Search("");
            Pick(asn);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void EndBtn_Click() {
        if (asn == null || asn.isEmpty()) {
            MessageBox("Choisissez d'abord une carte");
            return;
        }
        Ask("Mettre fin à l'ordre de travail en cours de la carte " + asn + " ? L'historique est conservé.", yes -> {
            if (yes) {
                try {
                    WorkOrderData.end(asn);
                    Search("");
                    Pick(asn);
                } catch (RuntimeException ex) {
                    MessageBox(ex.getMessage());
                }
            }
        });
    }
}
