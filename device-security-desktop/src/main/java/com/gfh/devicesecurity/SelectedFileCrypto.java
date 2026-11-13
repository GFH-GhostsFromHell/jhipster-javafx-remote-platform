package com.gfh.devicesecurity;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.BiConsumer;

/**
 * Non-destructive, opt-in encryption of a single selected regular data file.
 * No directory traversal, system-drive operations, source deletion or overwrite.
 * AES-256-GCM provides authenticated encryption.
 */
public final class SelectedFileCrypto {
    private static final byte[] MAGIC = "GFHENC01".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_SIZE = 12;
    private static final int TAG_BITS = 128;
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024; // bounded memory for provider GCM verification
    private SelectedFileCrypto() { }

    public interface Progress {
        void update(long processed, long total) throws InterruptedException;
    }

    public static byte[] createKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256, RANDOM);
        return generator.generateKey().getEncoded();
    }

    public static String recoveryText(byte[] key) {
        if (key == null || key.length != 32) throw new IllegalArgumentException("AES-256 needs a 32-byte key");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
    }

    public static byte[] readRecoveryText(String text) {
        try {
            byte[] key = Base64.getUrlDecoder().decode(text == null ? "" : text.strip());
            if (key.length != 32) throw new IllegalArgumentException("Recovery key must contain 256 bits");
            return key;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid recovery key: expected the full offline recovery code");
        }
    }

    public static Path encrypt(Path selectedFile, byte[] key, Progress progress) throws Exception {
        validateSelectedRegularFile(selectedFile);
        if (key == null || key.length != 32) throw new IllegalArgumentException("Invalid AES-256 key");
        long total = Files.size(selectedFile);
        if (total > MAX_FILE_BYTES) throw new IllegalArgumentException("This first version supports selected files up to 64 MiB");
        Path output = selectedFile.resolveSibling(selectedFile.getFileName() + ".gfhenc");
        if (Files.exists(output)) throw new FileAlreadyExistsException(output.toString());
        byte[] iv = new byte[NONCE_SIZE];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        return transform(selectedFile, output, cipher, MAGIC, iv, total, progress, false);
    }

    public static Path decrypt(Path selectedFile, byte[] key, Progress progress) throws Exception {
        validateSelectedRegularFile(selectedFile);
        if (key == null || key.length != 32) throw new IllegalArgumentException("Invalid AES-256 key");
        long total = Files.size(selectedFile);
        if (total > MAX_FILE_BYTES + MAGIC.length + NONCE_SIZE + 16)
            throw new IllegalArgumentException("Encrypted file is larger than supported limit");
        if (total < MAGIC.length + NONCE_SIZE + 16) throw new IllegalArgumentException("Invalid encrypted file");
        String fileName = selectedFile.getFileName().toString();
        if (!fileName.endsWith(".gfhenc")) throw new IllegalArgumentException("Select a .gfhenc file");
        String name = fileName.substring(0, fileName.length() - ".gfhenc".length()) + ".restored";
        Path output = selectedFile.resolveSibling(name);
        if (Files.exists(output)) throw new FileAlreadyExistsException(output.toString());
        byte[] header = new byte[MAGIC.length + NONCE_SIZE];
        try (InputStream source = Files.newInputStream(selectedFile)) {
            if (source.readNBytes(header, 0, header.length) != header.length)
                throw new IllegalArgumentException("Encrypted file header is truncated");
        }
        if (!Arrays.equals(Arrays.copyOfRange(header, 0, MAGIC.length), MAGIC))
            throw new IllegalArgumentException("File was not encrypted with Device Security Center");
        byte[] iv = Arrays.copyOfRange(header, MAGIC.length, header.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        return transform(selectedFile, output, cipher, null, null,
                total - header.length, progress, true);
    }

    private static Path transform(Path source, Path output, Cipher cipher, byte[] magic, byte[] iv,
                                  long total, Progress progress, boolean decrypt) throws Exception {
        Path temp = Files.createTempFile(source.toAbsolutePath().getParent(), "gfh-crypt-", ".partial");
        boolean finished = false;
        try {
            try (InputStream in = Files.newInputStream(source);
                 OutputStream out = Files.newOutputStream(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                if (decrypt) {
                    in.skipNBytes(MAGIC.length + NONCE_SIZE); // Java 21
                } else {
                    out.write(magic);
                    out.write(iv);
                }
                long processed = 0;
                byte[] buffer = new byte[64 * 1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Operation cancelled");
                    byte[] chunk = cipher.update(buffer, 0, len);
                    if (chunk != null && chunk.length != 0) out.write(chunk);
                    processed += len;
                    if (progress != null) progress.update(processed, total);
                }
                byte[] end = cipher.doFinal(); // verifies GCM tag for decryption
                if (end != null && end.length != 0) out.write(end);
                if (progress != null) progress.update(total, total);
            }
            // Never overwrite, and never modify/remove the selected source file.
            Files.move(temp, output);
            finished = true;
            return output;
        } finally {
            if (!finished) Files.deleteIfExists(temp);
        }
    }

    private static void validateSelectedRegularFile(Path selectedFile) throws Exception {
        if (selectedFile == null || !Files.isRegularFile(selectedFile, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Select one ordinary file, not a folder or symbolic link");
        if (!Files.isReadable(selectedFile)) throw new IllegalArgumentException("Selected file cannot be read");
    }
}
