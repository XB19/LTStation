package com.ltstation.center.sw.hw;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.MultiLanguage;
import com.ltstation.center.sw.core.SW;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * ServerDeal.Tool.TankSerial : sonde de niveau des cuves (protocole ATG ASCII, port série 9600 8N1).
 * Interrogation toutes les 10 s (« i201 »), réponse terminée par « && » et une somme de contrôle,
 * mesures en flottants IEEE ; téléchargement des barèmes hauteur / volume.
 */
public class TankSerial {

    /** TransferTankEvent(TankID, Height, Vol, Temp, WHeight, WVolume) */
    public interface TransferTankData {
        void accept(int tankId, BigDecimal height, BigDecimal vol, BigDecimal temp, BigDecimal wHeight, BigDecimal wVolume);
    }

    /** TransferDowloadEvent(i, Total) */
    public interface TrnsferTankDowload {
        void accept(int i, int total);
    }

    private static final Charset ANSI = Charset.forName("GBK");

    public LegacyLog Log;
    public SerialClass sp = new SerialClass();
    public Thread thread;
    private String ReceiveData = "";
    private volatile boolean SendFlag = true;
    private int i;
    private List<Row> TankTable;
    private int DowLoadTankID;
    private int TotalDowLoad;
    public final Deque<Integer> Record = new ArrayDeque<>();
    public TransferTankData TransferTankEvent = (a, b, c, d, e, f) -> { };
    public TrnsferTankDowload TransferDowloadEvent = (a, b) -> { };

    public TankSerial(int portId) {
        Log = new LegacyLog("Tank Level");
        sp = new SerialClass("COM" + portId, 9600, SerialClass.Parity.None, 8, SerialClass.StopBits.One);
        sp.onDataReceived(this::Sp_DataReceived);
        try {
            String[] error = {""};
            if (!sp.openPort(error)) {
                throw new IllegalStateException(error[0]);
            }
            Log.writeLog("Open Port-" + portId + " Successed!");
            thread = new Thread(this::SendData, "TankSerial");
            thread.setDaemon(true);
            thread.start();
        } catch (RuntimeException ex) {
            Log.writeLog("Error:" + ex.getMessage());
        }
    }

    private static Sugar db() {
        return SW.db();
    }

    private static BigDecimal r1(float f) {
        return new BigDecimal(Float.toString(f)).setScale(1, RoundingMode.HALF_EVEN);
    }

    private synchronized void Sp_DataReceived(byte[] bits) {
        try {
            if (SendFlag) {
                String text = SerialClass.byteToHexStr(bits);
                ReceiveData += text;
                Log.writeLog(MultiLanguage.getText("DataReveive") + " Str:" + text);
                if (!ReceiveData.contains("2626")) {
                    return;
                }
                Log.writeLog(MultiLanguage.getText("DataReveive") + " ReceiveData:" + ReceiveData);
                String text2 = ADD(ReceiveData.substring(2, ReceiveData.length() - 10));
                byte[] array = SerialClass.strToToHexByte(ReceiveData);
                StringBuilder text3 = new StringBuilder();
                for (int k = array.length - 5; k < array.length - 1; k++) {
                    text3.append((char) (array[k] & 0xFF));
                }
                if (!text2.equalsIgnoreCase(text3.toString())) {
                    return;
                }
                int num = 0;
                Integer rec = Record.pollFirst();
                if (rec != null) {
                    num = rec;
                }
                int num2 = 17;
                while (num2 + 57 <= array.length) {
                    String text6 = "" + (char) (array[num2] & 0xFF) + (char) (array[num2 + 1] & 0xFF);
                    String mess = DealMessage(num2 + 9, 8, array);
                    String mess2 = DealMessage(num2 + 33, 8, array);
                    String mess3 = DealMessage(num2 + 41, 8, array);
                    String mess4 = DealMessage(num2 + 49, 8, array);
                    int tank = Integer.parseInt(text6.trim());
                    TransferTankEvent.accept(tank, r1(DealToFloat(mess2)), r1(DealToFloat(mess)), r1(DealToFloat(mess4)), r1(DealToFloat(mess3)),
                        BigDecimal.ZERO);
                    if (num == tank) {
                        db().insert("NormalTankHistory", Row.of("TankID", tank, "TankNo", text6, "CurrentHeight", r1(DealToFloat(mess2)),
                            "CurrentVolume", r1(DealToFloat(mess)), "CurrentTemp", r1(DealToFloat(mess4)), "CurrentWaterHeight", r1(DealToFloat(mess3)),
                            "CreateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now())));
                    }
                    num2 += 65;
                    if (num2 > array.length) {
                        break;
                    }
                }
                ReceiveData = "";
            } else {
                DowLoadMess();
            }
        } catch (RuntimeException ex2) {
            Log.writeLog(MultiLanguage.getText("DataReveive") + " Error:" + ex2.getMessage());
            ReceiveData = "";
        }
    }

    private void SendData() {
        while (IniInfo.ThreadStop) {
            try {
                if (SendFlag) {
                    synchronized (this) {
                        ReceiveData = "";
                    }
                    String text = "01693230313030";
                    byte[] array = SerialClass.strToToHexByte(text);
                    Log.writeLog(MultiLanguage.getText("DataSend") + ":" + text);
                    if (!sp.PortIsOpen()) {
                        throw new IllegalStateException("The port is closed.");
                    }
                    sp.sendbyte(array, 0, array.length);
                }
            } catch (RuntimeException ex) {
                Log.writeLog(MultiLanguage.getText("DataSend") + " Error:" + ex.getMessage());
                sp.openPort(new String[1]);
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Somme de contrôle : complément à un de la somme des octets (16 bits). */
    private static String ADD(String command) {
        try {
            long num = 0L;
            for (byte b : SerialClass.strToToHexByte(command)) {
                num += b & 0xFF;
            }
            num = (num >> 16) + (num & 0xFFFF);
            num += num >> 16;
            num = ~num;
            String text = Long.toHexString(num);
            return text.substring(text.length() - 4);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String DealMessage(int start, int length, byte[] d) {
        StringBuilder text = new StringBuilder();
        for (int k = start; k < start + length; k++) {
            text.append((char) (d[k] & 0xFF));
        }
        return text.toString();
    }

    /** Flottant IEEE transmis en hexadécimal (poids fort en premier). */
    private static float DealToFloat(String mess) {
        try {
            byte[] b = SerialClass.strToToHexByte(mess);
            return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getFloat();
        } catch (RuntimeException e) {
            return 0f;
        }
    }

    /** Téléchargement du barème d'une cuve dans la sonde (une ligne par réponse). */
    public synchronized void DowLoadTable(int tankId) {
        i = 0;
        SendFlag = false;
        DowLoadTankID = tankId;
        TankTable = db().list("TankVolConfig", "{TankID} = :t ORDER BY {Height}", Sugar.p("t", tankId));
        TotalDowLoad = TankTable.size();
        String ef = TankTable.stream().mapToDouble(t -> Conv.toDouble(t.get("Vol"))).max().orElse(0) > 1000000.0 ? "F" : "E";
        DowloadVolumeTable(Conv.toDecimal(TankTable.get(i).get("Height")), Conv.toDecimal(TankTable.get(i).get("Vol")), tankId, ef);
        i++;
        TransferDowloadEvent.accept(i, TankTable.size());
    }

    public void DowLoadMess() {
        String ef = TankTable.stream().mapToDouble(t -> Conv.toDouble(t.get("Vol"))).max().orElse(0) > 1000000.0 ? "F" : "E";
        DowloadVolumeTable(Conv.toDecimal(TankTable.get(i).get("Height")), Conv.toDecimal(TankTable.get(i).get("Vol")), DowLoadTankID, ef);
        i++;
        TransferDowloadEvent.accept(i, TankTable.size());
        if (i == TankTable.size()) {
            SendFlag = true;
        }
    }

    public void DowloadVolumeTable(BigDecimal height, BigDecimal volume, int tankId, String ef) {
        try {
            SendFlag = false;
            height = height.multiply(BigDecimal.valueOf(100));
            if (ef.equals("E")) {
                volume = volume.multiply(BigDecimal.valueOf(100));
            }
            Log.writeLog("TankID:" + tankId);
            Log.writeLog("Height:" + height.toPlainString());
            Log.writeLog("Volume:" + volume.toPlainString());
            String text = MakeLen(height.setScale(0, RoundingMode.HALF_EVEN).toPlainString(), 8);
            String text2 = MakeLen(volume.setScale(0, RoundingMode.HALF_EVEN).toPlainString(), 8);
            Log.writeLog("HSTR:" + text);
            Log.writeLog("VSTR:" + text2);
            String text3 = ASC(text);
            String text4 = ASC(text2);
            Log.writeLog("HByte:" + text3);
            Log.writeLog("VByte:" + text4);
            String text6 = "03693230" + ASC(ef) + ASC(MakeLen(String.valueOf(tankId), 2)) + text3 + text4 + ASC("&&");
            text6 += ADD(text6);
            Log.writeLog("res:" + text6);
            byte[] array = SerialClass.strToToHexByte(text6);
            sp.sendbyte(array, 0, array.length);
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public static String MakeLen(String str, int len) {
        while (str.length() < len) {
            str = "0" + str;
        }
        return str;
    }

    public static String ASC(String str) {
        return SerialClass.byteToHexStr(str.getBytes(ANSI));
    }

    public void close() {
        sp.closePort();
    }
}
