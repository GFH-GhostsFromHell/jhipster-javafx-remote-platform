package com.gfh.devicesecurity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Read-only BitLocker status probe. Never requests keys or changes drive state. */
public final class DiskEncryptionStatus {
    public record Result(String state, String detail, String volume) { }
    private DiskEncryptionStatus() { }

    public static Result check() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return new Result("UNSUPPORTED", "This version checks Windows BitLocker only.", "N/A");
        }
        String drive = System.getenv().getOrDefault("SystemDrive", "C:");
        if (!drive.matches("[A-Za-z]:")) drive = "C:";
        Process process = null;
        try {
            // Documented status-only command: no encryption, decryption or key export.
            process = new ProcessBuilder("manage-bde.exe", "-status", drive)
                .redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result("UNKNOWN", "Drive-status check timed out.", drive);
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new Result("UNKNOWN", "Windows could not read BitLocker status. Try with appropriate permissions.", drive);
            }
            String state = classify(output);
            String detail = switch (state) {
                case "ENCRYPTED" -> "Drive is fully encrypted and BitLocker protection is on.";
                case "NOT_ENCRYPTED" -> "Drive reports fully decrypted.";
                case "IN_PROGRESS" -> "Windows reports encryption is in progress.";
                default -> "Drive encryption/protection status could not be verified.";
            };
            return new Result(state, detail, drive);
        } catch (IOException e) {
            return new Result("UNKNOWN", "Windows BitLocker status utility is unavailable.", drive);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result("UNKNOWN", "Status check interrupted.", drive);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    static String classify(String output) {
        if (output == null) return "UNKNOWN";
        String s = output.toLowerCase(Locale.ROOT);
        if (s.contains("encryption in progress")) return "IN_PROGRESS";
        if (s.contains("fully decrypted")) return "NOT_ENCRYPTED";
        if (s.contains("fully encrypted") && s.contains("protection on")) return "ENCRYPTED";
        return "UNKNOWN";
    }
}
