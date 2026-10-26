package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;

/** Contrôles de saisie communs à CardMake et CardUpDate (blocs répétés de leurs méthodes Regular). */
abstract class CardFormBase extends SwForm {

    static final String PHONE = "^(0[0-9]{2,3}\\-)([2-9][0-9]{6,7})?(\\-[0-9]{1,4})?$|(^(13[0-9]|15[0-9]|17[0-9]|18[0-9])\\d{8}$)";
    static final String CAR = "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领A-Z]{1}[A-Z]{1}[A-Z0-9]{4}[A-Z0-9挂学警港澳]{1}$";
    static final String POST = "^\\d{6}$";
    static final String MAIL = "^[\\w-]+@[\\w-]+\\.(com|net|org|edu|mil|tv|biz|info)$";
    static final String DEC = "^[0-9]+(.[0-9]{1,3})?$";
    static final String NUM = "^[0-9]*$";

    static void need(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }

    static boolean valid(String text, String pattern) {
        return BaseFunction.ValidMessage(text, pattern);
    }

    /** Le champ, s'il est rempli, respecte le motif. */
    void match(String box, String pattern, String message) {
        need(c(box).Text().isEmpty() || valid(c(box).Text(), pattern), message);
    }

    /** Limite décimale journalière : 10^(position du point) comparé au format de la station. */
    void limitDaily(String box, String errKey, int format, double max, String exceededKey) {
        String t = c(box).Text();
        if (t.isEmpty()) {
            return;
        }
        need(valid(t, DEC), L(errKey) + "\n" + L("NumberOrDecimal"));
        int idx = t.lastIndexOf('.');
        need(!(Math.pow(10.0, idx) > format), L(errKey) + digits());
        need(!(Conv.toDouble(t) * Math.pow(10.0, idx) > max), L(exceededKey));
    }

    /** Limite décimale mensuelle : position du point comparée au format de la station. */
    void limitMonthly(String box, String errKey, int format, double max, String exceededKey, String digitsMessage) {
        String t = c(box).Text();
        if (t.isEmpty()) {
            return;
        }
        need(valid(t, DEC), L(errKey) + "\n" + L("NumberOrDecimal"));
        int idx = t.lastIndexOf('.');
        need(idx <= format, L(errKey) + digitsMessage);
        need(!(Conv.toDouble(t) * Math.pow(10.0, idx) > max), L(exceededKey));
    }

    String digits() {
        return "\n" + L("OutOfStationDigits") + "\n" + L("PLSCheckDigits");
    }

    void password(String box) {
        String t = c(box).Text();
        if (!t.isEmpty()) {
            need(t.length() == 4, L("NeedPasswordMaxLength"));
            need(valid(t, NUM), L("PasswordIsNumber"));
        }
    }

    void carNo(String box) {
        String t = c(box).Text();
        if (!t.isEmpty()) {
            need(t.length() == 7, L("CarNoLength"));
            need(valid(t, CAR), L("CarFormatError"));
        }
    }

    void numeric(String box, String key) {
        match(box, NUM, L(key));
    }

    /** Jour de facturation / remboursement : obligatoire, entier, au plus 30. */
    void days(String box, String emptyKey) {
        String t = c(box).Text();
        need(!t.isEmpty(), L(emptyKey));
        need(valid(t, NUM), L("NumberOrDecimal"));
        need(Conv.toInt(t) <= 30, L("DayRange"));
    }

    static boolean isDouble(String s) {
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
