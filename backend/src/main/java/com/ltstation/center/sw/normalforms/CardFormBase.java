package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.normal.BaseInfo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Listes communes à Card.CardBuild et Card.Card_Update (règles de points, groupes, carburants, station) et contrôles des limites. */
abstract class CardFormBase extends SwForm {

    protected void GetRule() {
        try {
            CustomerRespone r = BaseFunction.post("ExtendUpdate/ExtendGetScoreRule",
                SW.dto().make("Card_Login_Return", "CompanyID", IniInfo.CompanyID, "EmpID", IniInfo.LoginID));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            List<Row> list = new ArrayList<>(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())));
            list.add(Row.of("ID", 0, "RuleName", "No Rule"));
            list.sort(Comparator.comparingLong(t -> Conv.toLong(t.get("ID"))));
            c("RuleSelect").DataSource(list, "RuleName", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    protected void GetGroup(boolean selectFirst) {
        try {
            CustomerRespone r = BaseFunction.post("ExtendUpdate/ExtendGetGroup",
                SW.dto().make("Card_Login_Return", "CompanyID", IniInfo.CompanyID, "EmpID", IniInfo.LoginID));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            List<Row> list = new ArrayList<>(Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())));
            list.add(Row.of("GroupID", 100000001, "Name", L("Person"), "GroupType", 0));
            list.sort(Comparator.comparingLong(t -> Conv.toLong(t.get("GroupID"))));
            c("GroupBox").DataSource(list, "Name", "GroupID");
            if (selectFirst) {
                c("GroupBox").SelectedIndex(0);
            }
            GroupBox_SelectedIndexChanged();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    protected void GetGradeList() {
        try {
            List<Row> list = new ArrayList<>(db().list("NormalGrade", "{IsUsing} = true", null));
            list.add(Row.of("ID", 0, "GradeName", L("None")));
            list.sort(Comparator.comparingLong(t -> Conv.toLong(t.get("ID"))));
            c("GradeSelectBox").DataSource(list, "GradeName", "ID");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    protected void GetStationList() {
        c("StationSelectBox").DataSource(List.of(Row.of("StationID", 0, "Name", L("None")),
            Row.of("StationID", IniInfo.StationID, "Name", IniInfo.StationName)), "Name", "StationID");
    }

    public void GroupManageBtn_Click() {
        ShowDialog("Card.GroupManage", r -> {
            if ("OK".equals(r.result())) {
                GetGroup(this instanceof CardBuild);
            }
        });
    }

    public void GroupBox_SelectedIndexChanged() {
        try {
            Row g = c("GroupBox").SelectedRow();
            c("GroupTypeLabel").Text(BaseInfo.GetGroupType(g.i("GroupType")));
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** int.TryParse + contrôle &gt;= 0 ; vide accepté (valeur 0) seulement si optional. */
    protected int intLimit(String box, String name, boolean optional) {
        String t = c(box).Text().trim();
        if (optional && c(box).Text().isEmpty()) {
            return 0;
        }
        try {
            int v = Integer.parseInt(t);
            if (v < 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " Format Error");
        }
    }

    protected BigDecimal decLimit(String box, String name, boolean optional) {
        if (optional && c(box).Text().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal v = new BigDecimal(c(box).Text().trim());
            if (v.signum() < 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " Format Error");
        }
    }

    /** Remise en % (100 si vide). */
    protected BigDecimal discount(String box, String rangeError) {
        if (c(box).Text().isEmpty()) {
            return new BigDecimal(100);
        }
        BigDecimal v;
        try {
            v = new BigDecimal(c(box).Text().trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Discount Format Error");
        }
        if (v.compareTo(new BigDecimal(100)) > 0 || v.signum() < 0) {
            throw new IllegalStateException(rangeError);
        }
        return v;
    }
}
