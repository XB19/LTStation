package com.ltstation.center.sw.forms;

import com.ltstation.center.common.ExcelImport;
import com.ltstation.center.common.ExcelImport.Column;
import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** EmpForm : liste des employés de la station. */
@SwWindow("EmpForm")
public class EmpForm extends SwForm {

    public void EmpForm_Load() {
        ViewShow(db().list("Employees"));
    }

    private void ViewShow(List<Row> list) {
        try {
            g("EmpView").RowsClear();
            for (Row item : list) {
                g("EmpView").RowsAdd(item.get("EmployeeID"), item.get("Name"), item.get("Memo1"), GetType(Conv.toInt(item.get("StationType"))));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private String GetType(int id) {
        // La table des droits d'abord : même libellé que la liste Droits de la fiche employé
        Row a = db().first("AuthorityMess", "{User_IDMess} = :id", p("id", id));
        if (a != null) {
            return a.str("Name");
        }
        return switch (id) {
            case 4 -> L("FuelEmployee");
            case 1 -> L("Administrators");
            case 2 -> L("Accounting");
            case 3 -> L("Cashier");
            default -> "";
        };
    }

    public void EmpView_CellDoubleClick() {
        ShowDialog("EmpUpdate", r -> {
            if (r.ok()) {
                ViewShow(db().list("Employees"));
            }
        }, "ID", Conv.toInt(g("EmpView").Cell(e.i("RowIndex"), 0)));
    }

    public void AddEmp_Click() {
        ShowDialog("CreateEmployee", r -> {
            if (r.ok()) {
                ViewShow(db().list("Employees"));
            }
        }, "ID", 2);
    }

    public void Confirm_Click() {
        Close();
    }

    // ------------------------------------------------------------ import Excel

    static final List<Column> COLUMNS = List.of(
        new Column("Nom", "name", true, false),
        new Column("Identifiant de connexion", "login", true, true),
        new Column("Mot de passe", "password", true, true),
        new Column("Profil", "profile", true, false),
        new Column("Connexion au Center", "center", false, false));

    public void TemplateBtn_Click() {
        List<String> profiles = db().list("AuthorityMess").stream().map(a -> a.str("Name")).toList();
        Download("Modele_import_employes.xlsx", ExcelImport.XLSX, ExcelImport.templateBytes(COLUMNS,
            List.of(List.of("Kodjo Mensah", "kmensah", "Lome2026!", profiles.isEmpty() ? "" : profiles.get(profiles.size() - 1), "Non")),
            List.of("Import des employés de la station : une ligne par employé, les colonnes marquées * sont obligatoires.",
                "Identifiant de connexion : unique. Mot de passe : mot de passe de connexion initial. Ce fichier contient des mots de passe : "
                    + "supprimez-le après l'import.",
                "Profil : nom exact d'un profil de droits de la station : " + String.join(", ", profiles) + ".",
                "Connexion au Center : Oui ou Non (Non par défaut).",
                "Les employés sont créés sur Center puis recopiés dans la station, comme avec « Ajouter un employé ».",
                "Supprimez la ligne d'exemple avant d'importer.")));
    }

    private record Planned(String name, String login, String password, String profile, boolean center) {
    }

    public void ImportBtn_Click() {
        OpenFileDialog("Excel(*.xlsx;*.xls)|*.xlsx;*.xls", path -> {
            try {
                List<ExcelImport.Line> lines;
                try (InputStream in = Files.newInputStream(Path.of(path))) {
                    lines = ExcelImport.read(path, in, COLUMNS);
                }
                List<Row> profiles = db().list("AuthorityMess");
                List<ImportPreview.Line> checked = new ArrayList<>();
                List<Planned> plan = new ArrayList<>();
                Set<String> logins = new HashSet<>();
                for (ExcelImport.Line l : lines) {
                    List<String> errors = new ArrayList<>(l.errors());
                    String login = l.get("login");
                    if (login != null) {
                        if (!logins.add(login.toLowerCase())) {
                            errors.add("Identifiant en double dans le fichier : " + login);
                        } else if (db().first("Employees", "{Memo1} = :l", p("l", login)) != null) {
                            errors.add("Identifiant déjà utilisé : " + login);
                        }
                    }
                    Row profile = null;
                    if (l.get("profile") != null) {
                        profile = profiles.stream().filter(a -> ExcelImport.norm(a.str("Name")).equals(ExcelImport.norm(l.get("profile"))))
                            .findFirst().orElse(null);
                        if (profile == null) {
                            errors.add("Profil inconnu : " + l.get("profile"));
                        }
                    }
                    boolean center = l.get("center") != null && ExcelImport.matches(l.get("center"), Set.of("Oui", "O", "Yes", "1", "Vrai"));
                    if (l.get("center") != null && !center && !ExcelImport.matches(l.get("center"), Set.of("Non", "N", "No", "0", "Faux"))) {
                        errors.add("Connexion au Center : Oui ou Non attendu");
                    }
                    checked.add(new ImportPreview.Line(l.number(), "Création", joinParts(l.get("name"), login,
                        profile == null ? null : profile.str("Name"), center ? "connexion Center" : null), errors));
                    if (errors.isEmpty()) {
                        plan.add(new Planned(l.get("name"), login, l.get("password"), profile.str("User_IDMess"), center));
                    }
                }
                String key = ImportPreview.put(new ImportPreview.Check("Importer des employés", checked));
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

    /** Création sur Center ligne par ligne (comme CreateEmployee), puis recopie des employés dans la station. */
    private void save(List<Planned> plan) {
        Row station = db().first("Stations");
        if (station == null) {
            MessageBox("Informations de la station manquantes");
            return;
        }
        int ok = 0;
        List<String> failed = new ArrayList<>();
        for (Planned e : plan) {
            try {
                Row employee = SW.dto().make("CenterEmployees", "StationID", station.get("StationID"), "Memo", e.password(),
                    "PostCode", e.profile(), "Address", e.login(), "Name", e.name(), "CenterLogin", e.center());
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CreateEmployee", Wire.json(employee));
                if (!r.Result()) {
                    throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
                }
                ok++;
            } catch (RuntimeException ex) {
                failed.add(e.login() + " : " + ex.getMessage());
            }
        }
        try {
            if (ok > 0) {
                CreateEmployee.downloadEmployees();
            }
        } catch (RuntimeException ex) {
            failed.add("Recopie des employés depuis Center : " + ex.getMessage());
        }
        ViewShow(db().list("Employees"));
        MessageBox(ok + " employé(s) créé(s) sur " + plan.size() + (failed.isEmpty() ? "" : "\nNon créés :\n" + String.join("\n", failed)));
    }

    private static String joinParts(String... parts) {
        List<String> out = new ArrayList<>();
        for (String x : parts) {
            if (x != null && !x.isBlank()) {
                out.add(x);
            }
        }
        return String.join(" · ", out);
    }
}
