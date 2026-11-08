package com.catukz.sbn.security;

import com.catukz.sbn.db.Database;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Mengelola master password: inisialisasi & buka kunci.
 * Menyimpan salt + verifier terenkripsi di tabel meta.
 */
public class Vault {
    private static final String VERIFIER = "CATUKZ_SBN_OK";
    private final Database db;

    public Vault(Database db) { this.db = db; }

    public boolean isInitialized() {
        return db.getMeta("vault_salt") != null;
    }

    public SecretKey initialize(char[] password) {
        byte[] salt = Crypto.randomBytes(16);
        SecretKey key = Crypto.deriveKey(password, salt);
        byte[] verifier = Crypto.encrypt(key, VERIFIER.getBytes(StandardCharsets.UTF_8));
        db.setMeta("vault_salt", Base64.getEncoder().encodeToString(salt));
        db.setMeta("vault_verifier", Base64.getEncoder().encodeToString(verifier));
        return key;
    }

    public SecretKey unlock(char[] password) {
        String saltB64 = db.getMeta("vault_salt");
        String verB64 = db.getMeta("vault_verifier");
        if (saltB64 == null || verB64 == null) return null;
        byte[] salt = Base64.getDecoder().decode(saltB64);
        SecretKey key = Crypto.deriveKey(password, salt);
        try {
            byte[] dec = Crypto.decrypt(key, Base64.getDecoder().decode(verB64));
            if (VERIFIER.equals(new String(dec, StandardCharsets.UTF_8))) return key;
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
