package com.ltstation.center.sw.hw;

import com.ltstation.center.sw.core.IniInfo;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;

/**
 * ServerDeal.PrintClass : impression des tickets sur l'imprimante thermique 58 mm du poste
 * (36 lignes par page, « libellé&valeur » sur deux colonnes, sinon centré). Sans imprimante, rien
 * n'est imprimé, comme l'original.
 */
public class PrintClass {

    public static final String DEFAULT_PRINTER = "58mm Series Printer";
    private static final int LinePerPage = 36;

    private final List<String> InfoList = new ArrayList<>();
    public final List<String> DataList = new ArrayList<>();
    private int TotalPages;

    public static List<String> GetLocalPrinters() {
        List<String> list = new ArrayList<>();
        for (PrintService s : PrintServiceLookup.lookupPrintServices(null, null)) {
            list.add(s.getName());
        }
        return list;
    }

    private boolean ReadyForPrint() {
        InfoList.clear();
        String[] array = IniInfo.StationName.split(" ", -1);
        if (array.length > 1) {
            InfoList.addAll(List.of(array));
        } else {
            InfoList.add(IniInfo.StationName);
        }
        InfoList.addAll(DataList);
        InfoList.add(" ");
        int count = InfoList.size();
        if (count > 0) {
            TotalPages = count / LinePerPage + (count % LinePerPage != 0 ? 1 : 0);
            return true;
        }
        return false;
    }

    /** Dessin d'une page : coordonnées d'origine (points à 100 par pouce → 72 par pouce). */
    private final Printable page = (Graphics g, PageFormat pf, int pageIndex) -> {
        if (pageIndex >= TotalPages) {
            return Printable.NO_SUCH_PAGE;
        }
        Graphics2D g2 = (Graphics2D) g;
        g2.translate(pf.getImageableX(), pf.getImageableY());
        g2.scale(0.72, 0.72);
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Times New Roman", Font.PLAIN, 12));
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < LinePerPage; i++) {
            int num = pageIndex * LinePerPage + i;
            if (num > InfoList.size() - 1) {
                break;
            }
            String text = InfoList.get(num);
            int y = 20 * i + fm.getAscent();
            if (text.indexOf('&') > 0) {
                String[] parts = text.split("&", -1);
                g2.drawString(parts[0], 0, y);
                // DirectionRightToLeft + Near : texte aligné à droite sur l'abscisse 191
                g2.drawString(parts[1], 191 - fm.stringWidth(parts[1]), y);
            } else {
                g2.drawString(text, 100 - fm.stringWidth(text) / 2, y);
            }
        }
        return Printable.PAGE_EXISTS;
    };

    private boolean printOn(String printerName) {
        for (PrintService s : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (s.getName().equals(printerName)) {
                try {
                    PrinterJob job = PrinterJob.getPrinterJob();
                    job.setPrintService(s);
                    job.setPrintable(page);
                    job.print();
                    return true;
                } catch (PrinterException e) {
                    return false;
                }
            }
        }
        return false;
    }

    /** Print() : impression sur l'imprimante 58 mm si elle est installée. */
    public boolean Print() {
        return GetLocalPrinters().contains(DEFAULT_PRINTER) && ReadyForPrint() && printOn(DEFAULT_PRINTER);
    }

    /** PrintExtend(imprimante) : impression sur l'imprimante indiquée (58 mm par défaut). */
    public boolean PrintExtend(String printMachine) {
        if (printMachine == null || printMachine.isEmpty()) {
            printMachine = DEFAULT_PRINTER;
        }
        try {
            return ReadyForPrint() && printOn(printMachine);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Lignes du ticket telles qu'imprimées (aperçu côté poste). */
    public List<String> lines() {
        ReadyForPrint();
        return new ArrayList<>(InfoList);
    }
}
