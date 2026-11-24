package com.ltstation.center.sw.forms;

import com.ltstation.center.common.ExcelImport;
import com.ltstation.center.common.ExcelImport.Column;
import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.core.WorkOrderData;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Equipment : équipements (engins, véhicules, machines) et leur ordre de travail ; accès aux ordres de travail des cartes,
 * à la consommation par ordre de travail, à l'import et à l'export Excel.
 */
@SwWindow("Equipment")
public class Equipment extends SwForm {

    public void Equipment_Load() {
        ShowData();
    }

    private void ShowData() {
        try {
            g("EquipView").RowsClear();
            for (Map<String, Object> e : WorkOrderData.equipments(c("SearchBox").Text())) {
                int i = g("EquipView").RowsAdd(e.get("id"), e.get("name"), WorkOrderData.str(e.get("work_order_no")), WorkOrderData.str(e.get("category")),
                    (WorkOrderData.str(e.get("brand")) + " " + WorkOrderData.str(e.get("model"))).trim(), WorkOrderData.str(e.get("serial_no")),
                    WorkOrderData.str(e.get("fleet_no")), WorkOrderData.str(e.get("grade_name")),
                    WorkOrderData.STATUS.get(((Number) e.get("status")).intValue()), e.get("card_count"));
                int status = ((Number) e.get("status")).intValue();
                if (status != 1) {
                    g("EquipView").RowForeColor(i, status == 2 ? "darkorange" : "red");
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private Integer selectedId() {
        int i = g("EquipView").CurrentRowIndex();
        if (i < 0 || i >= g("EquipView").RowCount()) {
            MessageBox("Choisissez d'abord un équipement dans la liste");
            return null;
        }
        return Conv.toInt(g("EquipView").Cell(i, "ID"));
    }

    public void SearchBtn_Click() {
        ShowData();
    }

    public void AddBtn_Click() {
        ShowDialog("EquipmentEdit", r -> {
            if (r.ok()) {
                ShowData();
            }
        }, "ID", 0);
    }

    public void EditBtn_Click() {
        Integer id = selectedId();
        if (id != null) {
            ShowDialog("EquipmentEdit", r -> {
                if (r.ok()) {
                    ShowData();
                }
            }, "ID", id);
        }
    }

    public void EquipView_CellDoubleClick() {
        EditBtn_Click();
    }

    public void DeleteBtn_Click() {
        Integer id = selectedId();
        if (id == null) {
            return;
        }
        Ask("Supprimer cet équipement ? Les cartes rattachées perdent leur ordre de travail à partir de maintenant ; l'historique est conservé.", yes -> {
            if (yes) {
                try {
                    WorkOrderData.delete(id);
                    ShowData();
                } catch (RuntimeException ex) {
                    MessageBox(ex.getMessage());
                }
            }
        });
    }

    public void CardsBtn_Click() {
        int i = g("EquipView").CurrentRowIndex();
        Object id = i >= 0 && i < g("EquipView").RowCount() ? g("EquipView").Cell(i, "ID") : null;
        ShowDialog("CardWorkOrder", r -> ShowData(), "EquipID", id == null ? 0 : Conv.toInt(id));
    }

    public void ConsumptionBtn_Click() {
        ShowDialog("WorkOrderConsumption", r -> { });
    }

    public void ExportBtn_Click() {
        try {
            DataTable dt = new DataTable();
            for (String col : new String[] {"Désignation", "Ordre de travail", "Catégorie", "Marque", "Modèle", "N° de série", "N° de parc",
                "Énergie", "Capacité du réservoir (L)", "Statut", "Cartes rattachées", "Observations", "Créé par"}) {
                dt.addColumn(col);
            }
            for (Map<String, Object> e : WorkOrderData.equipments(c("SearchBox").Text())) {
                dt.addRow(WorkOrderData.str(e.get("name")), WorkOrderData.str(e.get("work_order_no")), WorkOrderData.str(e.get("category")),
                    WorkOrderData.str(e.get("brand")), WorkOrderData.str(e.get("model")), WorkOrderData.str(e.get("serial_no")),
                    WorkOrderData.str(e.get("fleet_no")), WorkOrderData.str(e.get("grade_name")), WorkOrderData.str(e.get("tank_capacity")),
                    WorkOrderData.STATUS.get(((Number) e.get("status")).intValue()), WorkOrderData.str(e.get("card_count")),
                    WorkOrderData.str(e.get("memo")), WorkOrderData.str(e.get("create_by")));
            }
            String[] out = {""};
            new ExportFile().DataTableToExcel("Équipements", dt, out);
            DownloadFile(out[0]);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    // ------------------------------------------------------------ import Excel

    static final List<Column> COLUMNS = List.of(
        new Column("Désignation", "name", true, false),
        new Column("Ordre de travail", "workOrderNo", false, true),
        new Column("Catégorie", "category", false, false),
        new Column("Marque", "brand", false, false),
        new Column("Modèle", "model", false, false),
        new Column("N° de série", "serialNo", false, true),
        new Column("N° de parc / immatriculation", "fleetNo", false, true),
        new Column("Énergie", "grade", false, false),
        new Column("Capacité du réservoir (L)", "tankCapacity", false, false),
        new Column("Statut", "status", false, false),
        new Column("N° de carte", "cards", false, true),
        new Column("Observations", "memo", false, false));

    public void TemplateBtn_Click() {
        Download("Modele_import_equipements.xlsx", ExcelImport.XLSX, ExcelImport.templateBytes(COLUMNS,
            List.of(List.of("Reach stacker RS-04", "4500012873345", "Reach stacker", "Kalmar", "DRG450", "KAL-DRG450-77812", "RS-04", "Gasoil",
                    "300", "En service", "", "Quai nord"),
                List.of("Groupe électrogène GE-2", "4500012873346", "Groupe électrogène", "Caterpillar", "C18", "CAT0C18GE2", "GE-2", "Gasoil",
                    "1200", "En maintenance", "", "")),
            List.of("Import des équipements : une ligne par équipement, les colonnes marquées * sont obligatoires.",
                "Ordre de travail, N° de série, N° de parc et N° de carte sont au format Texte : gardez-les ainsi, "
                    + "sinon Excel arrondit les numéros de plus de 15 chiffres.",
                "Un équipement dont le N° de série existe déjà est mis à jour au lieu d'être créé.",
                "Énergie : nom ou code de l'énergie (ex. Gasoil). Statut : En service (par défaut), En maintenance ou Hors service.",
                "N° de carte : facultatif, une ou plusieurs cartes séparées par des virgules ; elles sont rattachées à l'équipement "
                    + "et prennent son ordre de travail à partir de l'import.",
                "Supprimez les lignes d'exemple avant d'importer.")));
    }

    private record Planned(Integer existing, WorkOrderData.Equipment form, List<String> cards) {
    }

    public void ImportBtn_Click() {
        OpenFileDialog("Excel(*.xlsx;*.xls)|*.xlsx;*.xls", path -> {
            try {
                List<ExcelImport.Line> lines;
                try (InputStream in = Files.newInputStream(Path.of(path))) {
                    lines = ExcelImport.read(path, in, COLUMNS);
                }
                List<ImportPreview.Line> checked = new ArrayList<>();
                List<Planned> plan = new ArrayList<>();
                check(lines, checked, plan);
                String key = ImportPreview.put(new ImportPreview.Check("Importer des équipements", checked));
                ShowDialog("ImportPreview", r -> {
                    ImportPreview.drop(key);
                    if (r.ok()) {
                        save(plan);
                    }
                }, "Key", key);
            } catch (IOException | RuntimeException ex) {
                MessageBox(ex.getMessage());
            }
        });
    }

    private void check(List<ExcelImport.Line> lines, List<ImportPreview.Line> checked, List<Planned> plan) {
        Map<String, String> grades = new HashMap<>();
        for (Row g : db().list("GradeSpecifics")) {
            grades.put(ExcelImport.norm(String.valueOf(g.get("Name"))), String.valueOf(g.get("Code")));
            grades.put(ExcelImport.norm(String.valueOf(g.get("Code"))), String.valueOf(g.get("Code")));
        }
        Set<String> serials = new HashSet<>();
        Set<String> cardsInFile = new HashSet<>();
        for (ExcelImport.Line l : lines) {
            List<String> errors = new ArrayList<>(l.errors());
            String grade = null;
            if (l.get("grade") != null) {
                grade = grades.get(ExcelImport.norm(l.get("grade")));
                if (grade == null) {
                    errors.add("Énergie inconnue : " + l.get("grade"));
                }
            }
            int status = 1;
            if (l.get("status") != null) {
                status = WorkOrderData.STATUS.entrySet().stream()
                    .filter(e -> ExcelImport.norm(e.getValue()).equals(ExcelImport.norm(l.get("status")))).map(Map.Entry::getKey).findFirst().orElse(0);
                if (status == 0) {
                    errors.add("Statut inconnu : " + l.get("status") + " (En service, En maintenance ou Hors service)");
                    status = 1;
                }
            }
            BigDecimal capacity = null;
            if (l.get("tankCapacity") != null) {
                try {
                    capacity = new BigDecimal(l.get("tankCapacity").replace(" ", "").replace(',', '.'));
                } catch (NumberFormatException e) {
                    errors.add("Capacité du réservoir invalide : " + l.get("tankCapacity"));
                }
            }
            String serial = l.get("serialNo");
            Integer existing = null;
            if (serial != null) {
                if (!serials.add(ExcelImport.norm(serial))) {
                    errors.add("N° de série en double dans le fichier : " + serial);
                }
                Map<String, Object> e = WorkOrderData.one("SELECT id FROM equipment WHERE serial_no = :s", WorkOrderData.p("s", serial));
                existing = e == null ? null : (Integer) e.get("id");
            }
            List<String> cards = new ArrayList<>();
            if (l.get("cards") != null) {
                for (String c : l.get("cards").split("[,;\\s]+")) {
                    if (c.isBlank()) {
                        continue;
                    }
                    if (!cardsInFile.add(c)) {
                        errors.add("Carte présente sur plusieurs lignes : " + c);
                        continue;
                    }
                    try {
                        WorkOrderData.card(c);
                        cards.add(c);
                    } catch (RuntimeException e) {
                        errors.add(e.getMessage());
                    }
                }
            }
            WorkOrderData.Equipment form = null;
            if (l.get("name") != null) {
                try {
                    form = WorkOrderData.clean(new WorkOrderData.Equipment(l.get("name"), l.get("workOrderNo"), l.get("category"), l.get("brand"),
                        l.get("model"), serial, l.get("fleetNo"), grade, capacity, status, l.get("memo")), existing);
                } catch (RuntimeException e) {
                    if (!errors.contains(e.getMessage())) {
                        errors.add(e.getMessage());
                    }
                }
            }
            if (!cards.isEmpty() && form != null && form.workOrderNo() == null) {
                errors.add("Un ordre de travail est nécessaire pour rattacher des cartes");
            }
            String detail = String.join(" · ", List.of(WorkOrderData.str(l.get("name")), "OT " + WorkOrderData.str(l.get("workOrderNo")),
                "S/N " + WorkOrderData.str(serial)) ) + (cards.isEmpty() ? "" : " · cartes " + String.join(", ", cards));
            checked.add(new ImportPreview.Line(l.number(), existing == null ? "Création" : "Mise à jour", detail, errors));
            if (errors.isEmpty()) {
                plan.add(new Planned(existing, form, cards));
            }
        }
    }

    /** Enregistre tout ou rien (une transaction). */
    private void save(List<Planned> plan) {
        var tx = db().begin();
        try {
            for (Planned p : plan) {
                int id;
                if (p.existing() == null) {
                    id = WorkOrderData.insert(p.form());
                } else {
                    id = p.existing();
                    WorkOrderData.update(id, p.form());
                }
                for (String card : p.cards()) {
                    WorkOrderData.assign(card, p.form().workOrderNo(), id, null, null);
                }
            }
            tx.commit();
            MessageBox(plan.size() + " équipement(s) importé(s)");
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox("Import annulé, rien n'a été enregistré : " + ex.getMessage());
        }
        ShowData();
    }
}
