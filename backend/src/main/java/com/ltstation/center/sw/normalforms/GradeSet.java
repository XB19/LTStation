package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** GradeSet : produits (téléchargés depuis Center) et leurs prix ; ajout, modification, suppression. */
@SwWindow(value = "NormalForm.GradeSet", setup = true)
public class GradeSet extends SwForm {

    public void GradeSet_Load() {
        c("GradeAdd").Visible(false);
        c("GradeDelete").Visible(false);
        LoadGradeHttp();
        GradeShowData();
        PriceShowData();
    }

    /** Produits de la société sur Center (réponse non chiffrée pour cette action). */
    private void LoadGradeHttp() {
        try {
            CustomerRespone r = BaseFunction.post("ServerDeal/NormalGradeDowload", SW.dto().make("CustomerParam", "CompanyID", IniInfo.CompanyID));
            if (!r.Result()) {
                return;
            }
            for (Row item : Wire.parseRows(r.Mess())) {
                Row grade = db().first("NormalGrade", "{ID} = :i", p("i", item.i("GradeSpecificID")));
                if (grade == null) {
                    db().insert("NormalGrade", Row.of("ID", item.i("GradeSpecificID"), "GradeName", item.str("Name"), "Code", item.str("Code"), "IsUsing", true));
                } else {
                    grade.put("GradeName", item.str("Name"));
                    grade.put("Code", item.str("Code"));
                    db().update("NormalGrade", grade);
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void GradeShowData() {
        try {
            g("GradeView").RowsClear();
            for (Row item : db().list("NormalGrade", "{IsUsing} = true ORDER BY {ID}", null)) {
                g("GradeView").RowsAdd(item.get("ID"), item.str("GradeName"));
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void PriceShowData() {
        try {
            g("PriceView").RowsClear();
            for (Row item : db().list("NormalPriceInfo", "{IsUsing} = true ORDER BY {ID}", null)) {
                Row grade = db().first("NormalGrade", "{ID} = :g", p("g", item.get("GradeID")));
                if (grade != null) {
                    g("PriceView").RowsAdd(item.get("ID"), grade.str("GradeName"), Conv.netRound(item.dec("Price"), IniInfo.Pradio));
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void Add_Click() {
        if (sender.equals("GradeAdd")) {
            ShowDialog("NormalForm.GradeAdd", r -> {
                if ("OK".equals(r.result())) {
                    GradeShowData();
                }
            });
        } else if (sender.equals("PriceAdd")) {
            ShowDialog("NormalForm.PriceAdd", r -> {
                if ("OK".equals(r.result())) {
                    PriceShowData();
                }
            });
        }
    }

    public void GradeView_CellDoubleClick() {
        try {
            int id = Conv.toInt(g(sender).Cell(g(sender).CurrentRowIndex(), 0));
            if (sender.equals("GradeView")) {
                ShowDialog("NormalForm.GradeUpdate", r -> {
                    if ("OK".equals(r.result())) {
                        GradeShowData();
                    }
                }, "ID", id);
            } else if (sender.equals("PriceView")) {
                ShowDialog("NormalForm.PriceUpdate", r -> {
                    if ("OK".equals(r.result())) {
                        PriceShowData();
                    }
                }, "ID", id);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void Delete_Click() {
        try {
            if (sender.equals("PriceDelete")) {
                int id = Conv.toInt(g("PriceView").Cell(g("PriceView").CurrentRowIndex(), 0));
                if (Confirm(L("DeleteConfirm"))) {
                    Row price = db().first("NormalPriceInfo", "{ID} = :i", p("i", id));
                    if (price == null) {
                        throw new IllegalStateException(L("PriceExist"));
                    }
                    if (db().any("NormalTank", "{IsUsing} = true AND {GradeID} = :g", p("g", price.get("GradeID")))) {
                        throw new IllegalStateException(L("IsUsing"));
                    }
                    BaseFunction.post("ServerDeal/NormalDeletePrice", SW.dto().has("NormalPriceInfo") ? SW.dto().shape("NormalPriceInfo", price) : price);
                    price.put("IsUsing", false);
                    db().update("NormalPriceInfo", price);
                    PriceShowData();
                }
            } else if (sender.equals("GradeDelete")) {
                int id = Conv.toInt(g("GradeView").Cell(g("GradeView").CurrentRowIndex(), 0));
                if (Confirm(L("DeleteConfirm"))) {
                    if (db().any("NormalPriceInfo", "{GradeID} = :g AND {IsUsing} = true", p("g", id))) {
                        throw new IllegalStateException(L("AssociatedError"));
                    }
                    if (db().any("NormalTank", "{IsUsing} = true AND {GradeID} = :g", p("g", id))) {
                        throw new IllegalStateException(L("IsUsing"));
                    }
                    Row grade = db().first("NormalGrade", "{ID} = :i", p("i", id));
                    if (grade == null) {
                        throw new IllegalStateException(L("GradeNone"));
                    }
                    BaseFunction.post("ServerDeal/NormalDeleteGrade", SW.dto().make("GradeSpecifics", "GradeSpecificID", id));
                    db().deleteEntity("NormalGrade", grade);
                    GradeShowData();
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
