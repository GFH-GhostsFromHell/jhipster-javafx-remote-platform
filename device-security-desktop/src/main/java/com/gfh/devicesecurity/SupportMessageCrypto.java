package com.gfh.devicesecurity;

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
 * Passphrase-encrypted chat payload interoperable with browser Web Crypto.
 * The server only receives v1.salt.iv.ciphertext and cannot decrypt messages.
 * Participants exchange a strong shared passphrase outside the chat.
 */
public final class SupportMessageCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final int ROUNDS = 210_000;
    private SupportMessageCrypto() { }

    public static String seal(String plaintext, char[] password) throws Exception {
        if (plaintext == null || plaintext.isBlank() || plaintext.length() > 3000)
            throw new IllegalArgumentException("Message must contain 1–3000 characters");
        requirePassword(password);
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(iv);
        byte[] key = derive(password, salt);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1." + ENCODER.encodeToString(salt) + "."
                + ENCODER.encodeToString(iv) + "." + ENCODER.encodeToString(ciphertext);
        } finally {
            Arrays.fill(key, (byte)0);
        }
    }

    public static String open(String payload, char[] password) throws Exception {
        requirePassword(password);
        String[] parts = payload == null ? new String[0] : payload.split("\\.", -1);
        if (parts.length != 4 || !"v1".equals(parts[0]))
            throw new IllegalArgumentException("Unrecognized encrypted message");
        byte[] salt = DECODER.decode(parts[1]);
        byte[] iv = DECODER.decode(parts[2]);
        byte[] ciphertext = DECODER.decode(parts[3]);
        if (salt.length != 16 || iv.length != 12 || ciphertext.length < 16 || ciphertext.length > 6000)
            throw new IllegalArgumentException("Invalid encrypted message format");
        byte[] key = derive(password, salt);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(key, (byte)0);
        }
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length < 12)
            throw new IllegalArgumentException("Use a strong shared chat passphrase of at least 12 characters");
    }

    private static byte[] derive(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ROUNDS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
