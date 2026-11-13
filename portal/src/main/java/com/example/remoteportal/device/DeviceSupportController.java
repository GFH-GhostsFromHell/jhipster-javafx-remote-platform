package com.example.remoteportal.device;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Support messages store ONLY password-encrypted ciphertext submitted by clients.
 * Passwords and plaintext messages are never sent to the portal.
 * Recovery requests are advisory approvals, never remote lock/unlock commands.
 */
@RestController
@RequestMapping("/api/device-security/support")
public class DeviceSupportController {
    private final JdbcTemplate jdbc;

    public DeviceSupportController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record MessageInput(String ciphertext) { }
    public record RequestInput(String fileLabel, String reason) { }

    @GetMapping("/messages")
    public List<Map<String,Object>> myMessages(Authentication authentication) {
        return messages(owner(authentication));
    }

    @PostMapping("/messages")
    public Map<String,String> send(Authentication authentication, @RequestBody MessageInput input) {
        String owner = owner(authentication);
        if (input == null || !validCiphertext(input.ciphertext())) bad("Encrypted message is required");
        saveMessage(owner, "CLIENT", input.ciphertext());
        return Map.of("status", "SAVED");
    }

    @GetMapping("/admin/inbox")
    public List<Map<String,Object>> inbox(Authentication authentication) {
        requireAdmin(authentication);
        return jdbc.queryForList("""
            SELECT owner_username, COUNT(*) AS message_count, MAX(sent_at) AS last_message_at
            FROM device_security_chat GROUP BY owner_username ORDER BY last_message_at DESC LIMIT 100
            """);
    }

    @GetMapping("/admin/conversations/{username}")
    public List<Map<String,Object>> conversation(Authentication authentication, @PathVariable String username) {
        requireAdmin(authentication);
        if (!validOwner(username)) bad("Invalid username");
        return messages(username);
    }

    @PostMapping("/admin/conversations/{username}")
    public Map<String,String> reply(Authentication authentication,
                                    @PathVariable String username, @RequestBody MessageInput input) {
        requireAdmin(authentication);
        if (!validOwner(username) || input == null || !validCiphertext(input.ciphertext())) bad("Invalid reply");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM device_security_chat WHERE owner_username=?",
            Integer.class, username);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User must initiate the support conversation");
        }
        saveMessage(username, "OPERATOR", input.ciphertext());
        return Map.of("status","SAVED");
    }

    @GetMapping("/recovery")
    public List<Map<String,Object>> myRequests(Authentication authentication) {
        return jdbc.queryForList("""
            SELECT id, file_label, reason, state, requested_at, decided_at
            FROM device_security_recovery_requests
            WHERE owner_username=? ORDER BY requested_at DESC LIMIT 100
            """, owner(authentication));
    }

    @PostMapping("/recovery")
    public Map<String,String> requestRecovery(Authentication authentication, @RequestBody RequestInput input) {
        String owner = owner(authentication);
        if (input == null || !validText(input.fileLabel(), 120)
            || !validText(input.reason(), 500)) bad("File label and reason are required");
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO device_security_recovery_requests
            (id, owner_username, file_label, reason, state, requested_at)
            VALUES (?, ?, ?, ?, 'PENDING', ?)
            """, id, owner, input.fileLabel().strip(), input.reason().strip(), Timestamp.from(Instant.now()));
        return Map.of("id", id, "state", "PENDING");
    }

    @GetMapping("/admin/recovery")
    public List<Map<String,Object>> pendingRequests(Authentication authentication) {
        requireAdmin(authentication);
        return jdbc.queryForList("""
            SELECT id, owner_username, file_label, reason, state, requested_at, decided_at, decided_by
            FROM device_security_recovery_requests
            ORDER BY requested_at DESC LIMIT 100
            """);
    }

    @PostMapping("/admin/recovery/{requestId}/{decision}")
    public Map<String,String> decide(Authentication authentication, @PathVariable String requestId,
                                     @PathVariable String decision) {
        requireAdmin(authentication);
        if (!isUuid(requestId) || !("approve".equals(decision) || "deny".equals(decision))) {
            bad("Invalid recovery approval request");
        }
        String state = "approve".equals(decision) ? "APPROVED" : "DENIED";
        int n = jdbc.update("""
            UPDATE device_security_recovery_requests
            SET state=?, decided_at=?, decided_by=? WHERE id=? AND state='PENDING'
            """, state, Timestamp.from(Instant.now()), authentication.getName(), requestId);
        if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Request was already decided or not found");
        return Map.of("id", requestId, "state", state);
    }

    private List<Map<String,Object>> messages(String username) {
        return jdbc.queryForList("""
            SELECT id, sender_role, ciphertext, sent_at FROM (
              SELECT id, sender_role, ciphertext, sent_at
              FROM device_security_chat WHERE owner_username=? ORDER BY sent_at DESC LIMIT 100
            ) recent ORDER BY sent_at ASC
            """, username);
    }

    private void saveMessage(String owner, String sender, String ciphertext) {
        jdbc.update("""
            INSERT INTO device_security_chat (id, owner_username, sender_role, ciphertext, sent_at)
            VALUES (?, ?, ?, ?, ?)
            """, UUID.randomUUID().toString(), owner, sender, ciphertext, Timestamp.from(Instant.now()));
    }

    private static void requireAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operator permission required");
        }
    }
    private static String owner(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
            || auth.getName().isBlank() || "anonymousUser".equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
        }
        return auth.getName();
    }
    private static boolean validOwner(String username) {
        return username != null && username.length() < 256 && !username.isBlank()
            && username.chars().noneMatch(Character::isISOControl);
    }
    private static boolean validText(String text, int max) {
        return text != null && !text.isBlank() && text.length() <= max
            && text.chars().noneMatch(Character::isISOControl);
    }
    private static boolean validCiphertext(String ciphertext) {
        if (ciphertext == null || ciphertext.length() > 9000) return false;
        String[] parts = ciphertext.split("\\.", -1);
        if (parts.length != 4 || !"v1".equals(parts[0])) return false;
        try {
            byte[] salt = java.util.Base64.getUrlDecoder().decode(parts[1]);
            byte[] iv = java.util.Base64.getUrlDecoder().decode(parts[2]);
            byte[] cipher = java.util.Base64.getUrlDecoder().decode(parts[3]);
            return salt.length == 16 && iv.length == 12 && cipher.length >= 16 && cipher.length <= 6000;
        } catch (IllegalArgumentException e) { return false; }
    }
    private static boolean isUuid(String candidate) {
        if (candidate == null) return false;
        try { return UUID.fromString(candidate).toString().equalsIgnoreCase(candidate); }
        catch (IllegalArgumentException ignored) { return false; }
    }
    private static void bad(String detail) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
