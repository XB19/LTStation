package com.ltstation.center.sw.hw;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * StationSoftWare.Forms.PriceSerial : afficheur de prix relié à un port série (type 0 « Price_Normal »,
 * type 1 « Price_LYX ») ; les prix sont renvoyés à chaque changement.
 */
public class PriceSerial {

    private String PriceNow = "";
    private final int ID;
    private final String PriceName;
    private final int Code;
    private final int DeviceTypeID;
    private LegacyLog PriceLog;
    private SerialClass sp;
    private Thread thread;
    public volatile boolean PriceStop;

    public PriceSerial(int id, String priceName, int code, int deviceTypeId) {
        this.ID = id;
        this.PriceName = priceName;
        this.Code = code;
        this.DeviceTypeID = deviceTypeId;
    }

    private static Sugar db() {
        return SW.db();
    }

    public boolean DeviceStart() {
        PriceLog = new LegacyLog(PriceName);
        try {
            Row com = db().first("ComConfig", "{CODE} = :c", Sugar.p("c", Code));
            if (com == null) {
                throw new IllegalStateException("Port introuvable");
            }
            SerialClass.Parity parity = switch (com.i("PARITY")) {
                case 1 -> SerialClass.Parity.Odd;
                case 2 -> SerialClass.Parity.Even;
                case 3 -> SerialClass.Parity.Mark;
                default -> SerialClass.Parity.None;
            };
            SerialClass.StopBits stopBits = switch (com.i("STOPBIT")) {
                case 1 -> SerialClass.StopBits.One;
                case 2 -> SerialClass.StopBits.Two;
                default -> SerialClass.StopBits.None;
            };
            sp = new SerialClass("COM" + com.i("PORT"), com.i("BAUDRATE"), parity, com.i("DATABIT"), stopBits);
            String[] error = {""};
            if (!sp.openPort(error)) {
                throw new IllegalStateException(error[0]);
            }
            PriceStop = true;
            thread = new Thread(this::SendData, "PriceSerial-" + ID);
            thread.setDaemon(true);
            if (DeviceTypeID == 0) {
                String text = "09010000" + BaseFunction.SetStrLen(String.valueOf(db().count("PriceInfos", null, null)), 2) + "030008";
                text = "FEFF" + text + AddValid(text) + "EF";
                byte[] array = SerialClass.strToToHexByte(text);
                sp.sendbyte(array, 0, array.length);
                Thread.sleep(300);
            }
            thread.start();
            PriceLog.writeLog("Port Open Success");
            return true;
        } catch (Exception ex) {
            PriceLog.writeLog("Port Open Failed");
            return false;
        }
    }

    /** Math.Round(decimal, n) : arrondi bancaire sans ajouter de zéros. */
    private static BigDecimal round(BigDecimal d, int n) {
        return d.scale() > n ? d.setScale(n, RoundingMode.HALF_EVEN) : d;
    }

    private void SendData() {
        while (IniInfo.ThreadStop) {
            try {
                String text = "";
                List<Row> list = db().list("PriceInfos", "ORDER BY {Code}", null);
                StringBuilder sb = new StringBuilder();
                for (Row item : list) {
                    sb.append(Conv.toStr(item.get("NewPPU")));
                }
                String text2 = sb.toString();
                if (text2.equals(PriceNow)) {
                    // L'original reboucle sans pause ici : pause ajoutée pour ne pas solliciter la base en continu
                    Thread.sleep(300);
                    continue;
                }
                PriceNow = text2;
                LocalDateTime now = LocalDateTime.now();
                switch (DeviceTypeID) {
                    case 0 -> {
                        StringBuilder t7 = new StringBuilder();
                        for (Row item : list) {
                            String col = Conv.toDateTime(item.get("ValidateTime")).isBefore(now) ? "NewPPU" : "CurrentPPU";
                            t7.append(DealPrice(round(Conv.toDecimal(item.get(col)), IniInfo.Pradio)));
                        }
                        String s7 = String.format("%02x", t7.length() / 2 + 4) + "0101" + t7;
                        s7 = s7 + AddValid(s7) + "EF";
                        text = "FEFF" + s7;
                    }
                    case 1 -> {
                        StringBuilder t5 = new StringBuilder();
                        for (Row item : list) {
                            String col = Conv.toDateTime(item.get("ValidateTime")).isBefore(now) ? "NewPPU" : "CurrentPPU";
                            double v = Conv.round(Conv.toDouble(item.get(col)), IniInfo.Pradio);
                            // string.Format("{0:0.0…}") : au moins une décimale, Pradio décimales
                            int dec = Math.max(1, IniInfo.Pradio);
                            t5.append(DealPrice(BigDecimal.valueOf(v).setScale(dec, RoundingMode.HALF_UP).toPlainString()));
                        }
                        String t6 = BaseFunction.SetStrLen(Integer.toHexString(t5.length() / 2 + 10).toUpperCase(), 2) + "0807060504030201" + t5;
                        t6 += AddValid(t6);
                        text = "1FAA55F101B7" + t6;
                    }
                    default -> {
                    }
                }
                PriceLog.writeLog("Data:" + text);
                byte[] array = SerialClass.strToToHexByte(text);
                if (sp != null) {
                    sp.sendbyte(array, 0, array.length);
                }
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ignored) {
                // comme l'original
            }
        }
    }

    private static String AddValid(String command) {
        try {
            int num = 0;
            for (byte b : SerialClass.strToToHexByte(command)) {
                num += b & 0xFF;
            }
            return String.format("%02X", num & 0xFF);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String DealPrice(BigDecimal price) {
        StringBuilder text = new StringBuilder(price.toPlainString().replace(".", ""));
        while (text.length() < 6) {
            text.append('0');
        }
        return text.toString();
    }

    /** Prix en segments de l'afficheur LYX (chiffre + 12 = chiffre suivi du point, 0A = vide). */
    private static String DealPrice(String price) {
        try {
            StringBuilder text = new StringBuilder();
            int num = price.indexOf('.');
            if (Integer.parseInt(price.substring(num + 1)) == 0) {
                price = price.length() < 5 ? price : price.substring(0, 5);
            }
            for (int i = 0; i < price.length(); i++) {
                int digit = price.charAt(i) - '0';
                if (i + 1 < price.length() && price.charAt(i + 1) == '.') {
                    text.append(BaseFunction.SetStrLen(Integer.toHexString(digit + 12).toUpperCase(), 2));
                    i++;
                } else {
                    if (digit < 0 || digit > 9) {
                        throw new NumberFormatException(String.valueOf(price.charAt(i)));
                    }
                    text.append(BaseFunction.SetStrLen(Integer.toHexString(digit).toUpperCase(), 2));
                }
            }
            while (text.length() / 2 < 4) {
                text.insert(0, "0A");
            }
            return text.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    public void Stop() {
        PriceStop = false;
        if (sp != null) {
            sp.closePort();
        }
    }
}
