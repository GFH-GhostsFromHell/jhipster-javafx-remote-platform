package com.example.remoteportal.device;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional escrow for a recovery code wrapped locally under a separate password.
 * The portal NEVER receives plaintext recovery keys, raw AES keys or escrow passwords.
 * Operator approval grants one short-lived ciphertext retrieval, not disk lockout.
 */
@RestController
@RequestMapping("/api/device-security/escrow")
public class DeviceEscrowController {
    private final JdbcTemplate jdbc;

    public DeviceEscrowController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record EscrowInput(String fileLabel, String wrappedRecoveryKey, Boolean offlineBackupVerified) { }

    @PostMapping("/entries")
    public Map<String,String> deposit(Authentication auth, @RequestBody EscrowInput input) {
        String owner = owner(auth);
        if (input == null || !validText(input.fileLabel(), 120)
            || !Boolean.TRUE.equals(input.offlineBackupVerified())
            || !validCiphertext(input.wrappedRecoveryKey())) {
            throw bad("A valid encrypted recovery backup and independently verified offline backup are required");
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO device_security_escrow
            (id, owner_username, file_label, wrapped_recovery_key, created_at)
            VALUES (?, ?, ?, ?, ?)
            """, id, owner, input.fileLabel().strip(), input.wrappedRecoveryKey(), Timestamp.from(Instant.now()));
        return Map.of("id",id,"status","STORED_ENCRYPTED");
    }

    @GetMapping("/entries")
    public List<Map<String,Object>> entries(Authentication auth) {
        return jdbc.queryForList("""
            SELECT id, file_label, created_at FROM device_security_escrow
            WHERE owner_username=? ORDER BY created_at DESC LIMIT 100
            """, owner(auth));
    }

    @PostMapping("/entries/{entryId}/request")
    public Map<String,String> request(Authentication auth, @PathVariable String entryId) {
        String owner = owner(auth);
        if (!isUuid(entryId)) throw bad("Invalid entry identifier");
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM device_security_escrow WHERE id=? AND owner_username=?
            """, Integer.class, entryId, owner);
        if (count == null || count == 0) throw notFound();
        Integer alreadyPending = jdbc.queryForObject("""
            SELECT COUNT(*) FROM device_security_escrow_release
            WHERE escrow_id=? AND owner_username=? AND state='PENDING'
            """, Integer.class, entryId, owner);
        if (alreadyPending != null && alreadyPending > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This entry already has a pending review");
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO device_security_escrow_release
            (id, escrow_id, owner_username, state, requested_at)
            VALUES (?, ?, ?, 'PENDING', ?)
            """, id, entryId, owner, Timestamp.from(Instant.now()));
        return Map.of("id",id,"status","PENDING");
    }

    @GetMapping("/requests")
    public List<Map<String,Object>> myRequests(Authentication auth) {
        return jdbc.queryForList("""
            SELECT r.id, r.escrow_id, e.file_label, r.state, r.requested_at, r.decided_at, r.retrieved_at
            FROM device_security_escrow_release r
            JOIN device_security_escrow e ON e.id=r.escrow_id
            WHERE r.owner_username=? ORDER BY r.requested_at DESC LIMIT 100
            """, owner(auth));
    }

    @GetMapping("/admin/requests")
    public List<Map<String,Object>> pending(Authentication auth) {
        admin(auth);
        return jdbc.queryForList("""
            SELECT r.id, r.escrow_id, r.owner_username, e.file_label, r.state,
                   r.requested_at, r.decided_at, r.decided_by, r.retrieved_at
            FROM device_security_escrow_release r
            JOIN device_security_escrow e ON e.id=r.escrow_id
            ORDER BY r.requested_at DESC LIMIT 100
            """);
    }

    @PostMapping("/admin/requests/{requestId}/{decision}")
    public Map<String,String> decide(Authentication auth, @PathVariable String requestId,
                                     @PathVariable String decision) {
        admin(auth);
        if (!isUuid(requestId) || !("approve".equals(decision) || "deny".equals(decision)))
            throw bad("Invalid operator decision");
        String state = "approve".equals(decision) ? "APPROVED" : "DENIED";
        int changed = jdbc.update("""
            UPDATE device_security_escrow_release
            SET state=?, decided_at=?, decided_by=?
            WHERE id=? AND state='PENDING'
            """, state, Timestamp.from(Instant.now()), auth.getName(), requestId);
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Request not pending");
        return Map.of("status", state, "requestId",requestId);
    }

    @PostMapping("/entries/{entryId}/release")
    @Transactional
    public Map<String,String> release(Authentication auth, @PathVariable String entryId) {
        String owner = owner(auth);
        if (!isUuid(entryId)) throw bad("Invalid entry identifier");
        List<Map<String,Object>> authorizations = jdbc.queryForList("""
            SELECT id FROM device_security_escrow_release
            WHERE escrow_id=? AND owner_username=? AND state='APPROVED'
              AND retrieved_at IS NULL AND decided_at >= ?
            ORDER BY decided_at DESC LIMIT 1
            """, entryId, owner, Timestamp.from(Instant.now().minusSeconds(15*60)));
        if (authorizations.isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Operator approval required; approval expires after 15 minutes and can be used once");
        String requestId = String.valueOf(authorizations.get(0).get("ID"));
        // PostgreSQL map keys can be lower-case; look up case-insensitively.
        if ("null".equals(requestId)) requestId = String.valueOf(authorizations.get(0).get("id"));
        int n = jdbc.update("""
            UPDATE device_security_escrow_release SET retrieved_at=?
            WHERE id=? AND retrieved_at IS NULL AND state='APPROVED'
            """, Timestamp.from(Instant.now()), requestId);
        if (n != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Already retrieved");
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT wrapped_recovery_key FROM device_security_escrow
            WHERE id=? AND owner_username=?
            """, entryId, owner);
        if (rows.size() != 1) throw notFound();
        Object ciphertext = rows.get(0).get("WRAPPED_RECOVERY_KEY");
        if (ciphertext == null) ciphertext = rows.get(0).get("wrapped_recovery_key");
        return Map.of("wrappedRecoveryKey", String.valueOf(ciphertext));
    }

    private static boolean validCiphertext(String text) {
        if (text == null || text.length() > 1500) return false;
        String[] p = text.split("\\.",-1);
        if (p.length != 4 || !"v1".equals(p[0])) return false;
        try {
            return java.util.Base64.getUrlDecoder().decode(p[1]).length == 16
                && java.util.Base64.getUrlDecoder().decode(p[2]).length == 12
                && java.util.Base64.getUrlDecoder().decode(p[3]).length >= 16;
        } catch (IllegalArgumentException e) { return false; }
    }
    private static boolean validText(String text,int max) {
        return text != null && !text.isBlank() && text.length()<=max
            && text.chars().noneMatch(Character::isISOControl);
    }
    private static boolean isUuid(String text) {
        if (text == null) return false;
        try { return UUID.fromString(text).toString().equalsIgnoreCase(text); }
        catch (IllegalArgumentException ignored) { return false; }
    }
    private static String owner(Authentication auth) {
        if (auth==null || !auth.isAuthenticated() || auth.getName()==null ||
            auth.getName().isBlank() || "anonymousUser".equals(auth.getName()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in first");
        return auth.getName();
    }
    private static void admin(Authentication auth) {
        if (auth==null || !auth.isAuthenticated() || auth.getAuthorities().stream()
            .noneMatch(a->"ROLE_ADMIN".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Operator only");
    }
    private static ResponseStatusException bad(String why) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,why);
    }
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,"Recovery record not found");
    }
}
