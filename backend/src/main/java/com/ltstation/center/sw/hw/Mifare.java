package com.ltstation.center.sw.hw;

import com.ltstation.center.sw.core.IniInfo;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.nio.charset.StandardCharsets;

/**
 * Lecteur Mifare mwrf32.dll (cartes employé et cartes de solde Normal). Mêmes fonctions que la classe Mifare de l'original.
 * Sans DLL (ou avec une JVM 64 bits face à la DLL 32 bits) ou sans lecteur, rf_init renvoie 0 : les fenêtres affichent
 * alors leur erreur habituelle (lecteur déconnecté) au lieu de planter.
 */
public final class Mifare {

    private Mifare() {
    }

    interface Lib extends StdCallLibrary {
        Pointer rf_init(short port, int baud);

        short rf_request(Pointer icdev, byte mode, ShortByReference tagtype);

        short rf_anticoll(Pointer icdev, byte bcnt, IntByReference snr);

        short rf_select(Pointer icdev, int snr, ByteByReference size);

        short rf_authentication_key(Pointer icdev, byte mode, byte secnr, byte[] keybuff);

        short rf_read_hex(Pointer icdev, byte blocknr, byte[] databuff);

        short rf_exit(Pointer icdev);

        short rf_write_hex(Pointer icdev, int blocknr, byte[] databuff);
    }

    private static Lib lib;
    private static boolean tried;

    private static synchronized Lib lib() {
        if (!tried) {
            tried = true;
            try {
                lib = Native.load("mwrf32", Lib.class);
            } catch (Throwable ex) {
                lib = null;
            }
        }
        return lib;
    }

    /** Poignée du lecteur, 0 si absent (équivaut à icdev.ToInt32() &lt;= 0). */
    public static long rf_init(int port, int baud) {
        Lib l = lib();
        if (l == null) {
            return 0;
        }
        try {
            return Pointer.nativeValue(l.rf_init((short) port, baud));
        } catch (Throwable ex) {
            return 0;
        }
    }

    private static Pointer p(long icdev) {
        return new Pointer(icdev);
    }

    /** rf_request + rf_anticoll + rf_select, résultats ignorés comme dans l'original. */
    public static void Select(long icdev) {
        lib().rf_request(p(icdev), (byte) 1, new ShortByReference());
        IntByReference snr = new IntByReference();
        lib().rf_anticoll(p(icdev), (byte) 0, snr);
        lib().rf_select(p(icdev), snr.getValue(), new ByteByReference());
    }

    public static short rf_authentication_key(long icdev, int mode, int secnr, byte[] keybuff) {
        return lib().rf_authentication_key(p(icdev), (byte) mode, (byte) secnr, keybuff);
    }

    /** Bloc lu en 32 caractères hexadécimaux. */
    public static String rf_read_hex(long icdev, int blocknr) {
        byte[] buf = new byte[32];
        lib().rf_read_hex(p(icdev), (byte) blocknr, buf);
        return new String(buf, StandardCharsets.ISO_8859_1);
    }

    public static short rf_write_hex(long icdev, int blocknr, String hex) {
        return lib().rf_write_hex(p(icdev), blocknr, hex.getBytes(StandardCharsets.ISO_8859_1));
    }

    public static void rf_exit(long icdev) {
        if (icdev > 0 && lib() != null) {
            lib().rf_exit(p(icdev));
        }
    }

    /** Clé des secteurs employé (réglage MifareCardKey, 12 hexadécimaux). */
    public static String KeyHex() {
        return IniInfo.MifareCardKey == null ? "" : IniInfo.MifareCardKey.toUpperCase();
    }

    public static byte[] Key() {
        String hex = KeyHex();
        if (hex.length() != 12) {
            throw new IllegalStateException("MifareCardKey");
        }
        byte[] b = new byte[6];
        for (int i = 0; i < 6; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    /** Carte vierge (clé FF) : bloc 20 = numéro de carte, 21 = type, remorques 23 et 27 avec la clé MifareCardKey. */
    public static void WriteCard(long icdev, String asn) {
        String key = KeyHex();
        Key();
        Select(icdev);
        if (rf_authentication_key(icdev, 0, 20, DEFAULT_KEY) != 0) {
            throw new IllegalStateException("Read Card Error");
        }
        StringBuilder text = new StringBuilder(asn.substring(8));
        while (text.length() < 32) {
            text.append('0');
        }
        write(icdev, 20, text.toString());
        write(icdev, 21, "20000000000000000000000000000000");
        write(icdev, 23, key + "678F0969" + key);
        Select(icdev);
        if (rf_authentication_key(icdev, 0, 27, DEFAULT_KEY) != 0) {
            throw new IllegalStateException("Read Card Error");
        }
        write(icdev, 27, key + "77878869" + key);
    }

    private static void write(long icdev, int block, String hex) {
        if (rf_write_hex(icdev, block, hex) != 0) {
            throw new IllegalStateException("Write Error");
        }
    }

    public static final byte[] DEFAULT_KEY = {-1, -1, -1, -1, -1, -1};
}
