package com.orbit.auth.signingkey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Seals / unseals signing-key private material with AES-256-GCM. The AES key is
 * derived from {@code AUTH_KEYSTORE_SECRET} via PBKDF2WithHmacSHA256. See
 * ADR-011 §3.
 *
 * <p>Wire format (base64): {@code [12-byte IV][ciphertext][16-byte GCM tag]}.
 */
@Component
public class KeySealer {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 120_000;
    // A fixed application salt: the secret is the real entropy source, and a
    // per-value salt would have to be stored next to the ciphertext anyway.
    private static final byte[] SALT = "orbit-auth-signing-key-v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec aesKey;
    private final SecureRandom random = new SecureRandom();

    public KeySealer(@Value("${orbit.auth.keystore-secret}") String keystoreSecret) {
        if (keystoreSecret == null || keystoreSecret.isBlank()) {
            throw new IllegalStateException("orbit.auth.keystore-secret (AUTH_KEYSTORE_SECRET) must be set");
        }
        this.aesKey = deriveKey(keystoreSecret);
    }

    private static SecretKeySpec deriveKey(String secret) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), SALT, PBKDF2_ITERATIONS, KEY_BITS);
            byte[] key = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to derive keystore encryption key", ex);
        }
    }

    public String seal(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to seal signing key", ex);
        }
    }

    public byte[] unseal(String sealed) {
        try {
            byte[] in = Base64.getDecoder().decode(sealed);
            byte[] iv = Arrays.copyOfRange(in, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(in, IV_BYTES, in.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to unseal signing key — AUTH_KEYSTORE_SECRET may have changed", ex);
        }
    }
}
