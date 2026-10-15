package com.ltstation.center.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Découpage temporel des graphiques (remplace les jointures sur master.dbo.spt_values de l'original).
 * type 0 = heures d'un jour, 1 = jours d'un mois, 2 = mois d'une année, 4 = années de début à fin.
 */
public record TimeBuckets(List<String> labels, LocalDateTime from, LocalDateTime to, String unit, int offset) {

    public static TimeBuckets of(int type, LocalDate date, LocalDate endDate) {
        List<String> labels = new ArrayList<>();
        switch (type) {
            case 1 -> {
                int days = YearMonth.from(date).lengthOfMonth();
                for (int d = 1; d <= days; d++) {
                    labels.add(String.valueOf(d));
                }
                LocalDateTime from = date.withDayOfMonth(1).atStartOfDay();
                return new TimeBuckets(labels, from, from.plusMonths(1), "day", 1);
            }
            case 2, 3 -> {
                for (int m = 1; m <= 12; m++) {
                    labels.add(Month.of(m).getDisplayName(TextStyle.SHORT, Locale.FRENCH));
                }
                LocalDateTime from = date.withDayOfYear(1).atStartOfDay();
                return new TimeBuckets(labels, from, from.plusYears(1), "month", 1);
            }
            case 4 -> {
                LocalDate end = endDate == null ? date : endDate;
                for (int y = date.getYear(); y <= end.getYear(); y++) {
                    labels.add(String.valueOf(y));
                }
                return new TimeBuckets(labels, LocalDate.of(date.getYear(), 1, 1).atStartOfDay(),
                        LocalDate.of(end.getYear() + 1, 1, 1).atStartOfDay(), "year", date.getYear());
            }
            default -> {
                for (int h = 0; h < 24; h++) {
                    labels.add(h + "h");
                }
                LocalDateTime from = date.atStartOfDay();
                return new TimeBuckets(labels, from, from.plusDays(1), "hour", 0);
            }
        }
    }

    /** Expression SQL du compartiment pour une colonne horodatée. */
    public String expr(String column) {
        return "extract(" + unit + " from " + column + ")";
    }

    /** Index du compartiment (ou -1 hors plage). */
    public int index(Object bucketValue) {
        if (!(bucketValue instanceof Number n)) {
            return -1;
        }
        int i = n.intValue() - offset;
        return i >= 0 && i < labels.size() ? i : -1;
    }

    public int size() {
        return labels.size();
    }

    public static LocalDate parseDate(String s) {
        return s == null || s.isBlank() ? LocalDate.now() : LocalDate.parse(s.substring(0, 10));
    }
}
