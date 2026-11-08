package com.catukz.sbn.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Enkripsi AES-256-GCM dengan kunci diturunkan dari master password (PBKDF2).
 */
public final class Crypto {
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    private Crypto() {}

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    public static SecretKey deriveKey(char[] password, byte[] salt) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS);
            byte[] key = f.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Gagal menurunkan kunci", e);
        }
    }

    /** @return iv(12 byte) + ciphertext+tag */
    public static byte[] encrypt(SecretKey key, byte[] plain) {
        try {
            byte[] iv = randomBytes(GCM_IV_LEN);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plain);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Enkripsi gagal", e);
        }
    }

    public static byte[] decrypt(SecretKey key, byte[] ivct) {
        try {
            byte[] iv = Arrays.copyOfRange(ivct, 0, GCM_IV_LEN);
            byte[] ct = Arrays.copyOfRange(ivct, GCM_IV_LEN, ivct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return c.doFinal(ct);
        } catch (Exception e) {
            throw new RuntimeException("Dekripsi gagal (master password salah?)", e);
        }
    }
}
