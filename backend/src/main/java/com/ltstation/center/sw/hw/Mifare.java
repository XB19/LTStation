package com.ltstation.center.sw.hw;

import com.ltstation.center.sw.core.IniInfo;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Lecteur Mifare mwrf32.dll (cartes employé et cartes de solde Normal). Mêmes fonctions que la classe Mifare de l'original.
 * La DLL est en 32 bits : un Java 64 bits passe par la passerelle desktop/mifare (mwrf-helper.exe, chemin dans la
 * propriété ltstation.mifare.helper ou la variable LTSTATION_MIFARE_HELPER).
 * Sans DLL, sans passerelle ou sans lecteur, rf_init renvoie 0 : les fenêtres affichent alors leur erreur habituelle
 * (lecteur déconnecté) au lieu de planter.
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
    private static Process helper;
    private static BufferedWriter toHelper;
    private static BufferedReader fromHelper;

    /** Passerelle 32 bits ; null si elle n'est pas installée. */
    private static boolean helperReady() {
        if (helper != null && helper.isAlive()) {
            return true;
        }
        String path = System.getProperty("ltstation.mifare.helper", System.getenv("LTSTATION_MIFARE_HELPER"));
        if (path == null || !new File(path).isFile()) {
            return false;
        }
        try {
            File exe = new File(path).getAbsoluteFile();
            helper = new ProcessBuilder(exe.getPath()).directory(exe.getParentFile()).redirectErrorStream(true).start();
            toHelper = new BufferedWriter(new OutputStreamWriter(helper.getOutputStream(), StandardCharsets.US_ASCII));
            fromHelper = new BufferedReader(new InputStreamReader(helper.getInputStream(), StandardCharsets.US_ASCII));
            Runtime.getRuntime().addShutdownHook(new Thread(helper::destroy));
            return "ok".equals(call("ping"));
        } catch (IOException ex) {
            helper = null;
            return false;
        }
    }

    private static synchronized String call(String line) {
        try {
            toHelper.write(line);
            toHelper.newLine();
            toHelper.flush();
            String answer = fromHelper.readLine();
            if (answer == null) {
                throw new IllegalStateException("Passerelle Mifare arrêtée");
            }
            return answer;
        } catch (IOException ex) {
            throw new IllegalStateException("Passerelle Mifare : " + ex.getMessage());
        }
    }

    private static short callShort(String line) {
        return Short.parseShort(call(line));
    }

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }

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
            if (!helperReady()) {
                return 0;
            }
            try {
                return Long.parseLong(call("init " + port + " " + baud));
            } catch (RuntimeException ex) {
                return 0;
            }
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
        if (lib() == null) {
            call("select " + icdev);
            return;
        }
        lib().rf_request(p(icdev), (byte) 1, new ShortByReference());
        IntByReference snr = new IntByReference();
        lib().rf_anticoll(p(icdev), (byte) 0, snr);
        lib().rf_select(p(icdev), snr.getValue(), new ByteByReference());
    }

    public static short rf_authentication_key(long icdev, int mode, int secnr, byte[] keybuff) {
        if (lib() == null) {
            return callShort("auth " + icdev + " " + mode + " " + secnr + " " + hex(keybuff));
        }
        return lib().rf_authentication_key(p(icdev), (byte) mode, (byte) secnr, keybuff);
    }

    /** Bloc lu en 32 caractères hexadécimaux. */
    public static String rf_read_hex(long icdev, int blocknr) {
        if (lib() == null) {
            return call("read " + icdev + " " + blocknr);
        }
        byte[] buf = new byte[32];
        lib().rf_read_hex(p(icdev), (byte) blocknr, buf);
        return new String(buf, StandardCharsets.ISO_8859_1);
    }

    public static short rf_write_hex(long icdev, int blocknr, String hex) {
        if (lib() == null) {
            return callShort("write " + icdev + " " + blocknr + " " + hex);
        }
        return lib().rf_write_hex(p(icdev), blocknr, hex.getBytes(StandardCharsets.ISO_8859_1));
    }

    public static void rf_exit(long icdev) {
        if (icdev > 0 && lib() != null) {
            lib().rf_exit(p(icdev));
        } else if (icdev > 0) {
            call("exit " + icdev);
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
