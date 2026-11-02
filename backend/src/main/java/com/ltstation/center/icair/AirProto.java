package com.ltstation.center.icair;

import com.ltstation.center.serverdeal.CenterDeal;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Fonctions de trame communes aux deux protocoles des appareils (ICAirController d'origine). */
final class AirProto {

    /** Encoding.Default du serveur d'origine. */
    static final Charset ANSI = Charset.forName("GBK");

    private AirProto() {
    }

    static String pad(String s, int len) {
        return CenterDeal.setStrLength(s, len);
    }

    /** SetStrLength(Convert.ToString(v, 16), len) : minuscules, complément à deux si négatif. */
    static String hex(long v, int len) {
        return pad(Long.toHexString(v), len);
    }

    static String hex(int v, int len) {
        return pad(Integer.toHexString(v), len);
    }

    /** Convert.ToInt32(s, 16) : accepte 32 bits au plus, le bit de poids fort donne un négatif. */
    static int int16(String s) {
        BigInteger v = new BigInteger(s, 16);
        if (v.bitLength() > 32) {
            throw new ArithmeticException("Valeur trop grande pour un Int32");
        }
        return v.intValue();
    }

    static byte[] bytes(String hex) {
        String h = hex.replace(" ", "");
        return HexFormat.of().parseHex(h);
    }

    static String str(byte[] b) {
        return HexFormat.of().withUpperCase().formatHex(b);
    }

    static String ascii(String hex) {
        return new String(bytes(hex), StandardCharsets.US_ASCII);
    }

    static BigDecimal pow10(int digits) {
        return BigDecimal.ONE.scaleByPowerOfTen(digits);
    }

    static double dpow10(int digits) {
        return Math.pow(10.0, digits);
    }

    /** (int)x sur un decimal : troncature. */
    static long trunc(BigDecimal x) {
        return x.setScale(0, RoundingMode.DOWN).longValue();
    }

    /** Hauteur de jauge : flottant big-endian écrit en caractères hexadécimaux ASCII, Convert.ToDecimal (7 chiffres). */
    static BigDecimal asciiFloat(String hex16) {
        float v = ByteBuffer.wrap(bytes(ascii(hex16))).getFloat();
        return new BigDecimal((double) v, new MathContext(7)).setScale(3, RoundingMode.HALF_EVEN);
    }

    /** CRC-16 (polynôme 0xA001), octet de poids fort en tête. */
    static String crc(String dt) {
        byte[] data = bytes(dt);
        if (data.length == 0) {
            return "";
        }
        int crc = 0;
        for (byte b : data) {
            crc ^= b & 0xFF;
            for (int j = 0; j < 8; j++) {
                crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xA001 : crc >> 1;
            }
        }
        return str(new byte[] {(byte) (crc >> 8), (byte) crc});
    }

    /** Double chaque octet FA. */
    static String faReplace(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i += 2) {
            String b = s.substring(i, i + 2);
            out.append("FA".equals(b) ? "FAFA" : b);
        }
        return out.toString();
    }

    /** Retire le doublement des octets FA. */
    static String replaceFa(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i += 2) {
            String b = s.substring(i, i + 2);
            if ("FA".equals(b) && i != s.length() - 2 && "FA".equals(s.substring(i + 2, i + 4))) {
                i += 2;
            }
            out.append(b);
        }
        return out.toString();
    }

    /** Trame de l'ancien protocole : CRC, doublement des FA, préfixe FA. */
    static String frameFa(String body) {
        String t = (body + crc(body)).toUpperCase();
        return "FA" + faReplace(t);
    }

    /** Retire les zéros de fin du code App. */
    static String appCode(String code) {
        int end = code.length();
        while (end > 0 && code.charAt(end - 1) == '0') {
            end--;
        }
        return code.substring(0, end);
    }
}
