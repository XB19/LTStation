package com.ltstation.center.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Chiffrement AES-256-CBC/PKCS7 encodé en hexadécimal, identique à
 * DealServer.EncryptByAes / DecryptByAES de la plateforme d'origine.
 * Il doit rester identique pour rester compatible avec les logiciels
 * Station et CardMake déjà déployés, ainsi qu'avec la licence (active_web).
 */
public final class LegacyAes {

    private static final byte[] KEY = "IXNRPASLSX729SI3Y2I9AN4MS57W25Z8".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IV = "Z72NX84FDK9A72H2".getBytes(StandardCharsets.UTF_8);
    private static final HexFormat HEX = HexFormat.of();

    private LegacyAes() {
    }

    public static String encrypt(String input) {
        try {
            return HEX.formatHex(cipher(Cipher.ENCRYPT_MODE).doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement AES impossible", e);
        }
    }

    public static String decrypt(String hex) {
        try {
            byte[] data = HEX.parseHex(hex.replace(" ", ""));
            return new String(cipher(Cipher.DECRYPT_MODE).doFinal(data), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Données chiffrées invalides", e);
        }
    }

    private static Cipher cipher(int mode) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(mode, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(IV));
        return c;
    }
}
