package com.gfh.industrial;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * One-code workstation pairing for the read-only test HMI.
 * Admin mints a high-entropy 15-minute single-use setup code. A client redeems it
 * over TLS to get its own scoped secret. No shared viewer password is required.
 * NOT production OT device attestation: deploy mTLS/PKI and audit controls before commissioning.
 */
@RestController
@RequestMapping("/api/studio")
public class WorkstationPairingResource {
    private final JdbcTemplate db;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public WorkstationPairingResource(JdbcTemplate db,
        @Value("${STUDIO_CLIENT_BASE_URL:http://127.0.0.1:8090}") String baseUrl) {
        this.db = db;
        this.baseUrl = WorkstationSetupPackageResource.validateBaseUrl(baseUrl);
    }

    @PostConstruct
    public void init() {
        db.execute("create table if not exists studio_pairing (" +
            "device_id varchar(48) primary key, code_hash varchar(64) not null," +
            "expires_at timestamp not null, consumed_at timestamp)");
    }

    public record PairingOffer(String workstationId,String setupCode,String expiresAt,String instructions) {}
    public record PairingRequest(String workstationId,String pairingCode) {}
    public record PairedDevice(String workstationId,String workstationName,String screenId,
                               String serverUrl,String deviceSecret) {}
    private record Device(String name,String screen) {}
    private record CodeRow(String hash, Instant expiresAt, boolean consumed) {}

    private String randomSecret() {
        byte[] b=new byte[32];random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }

    static boolean validId(String id) {
        return id!=null && id.matches("[A-Za-z][A-Za-z0-9_-]{1,47}");
    }

    @Transactional
    @PostMapping("/workstations/{id}/pairing")
    public ResponseEntity<PairingOffer> newPairing(@PathVariable String id) {
        if(!validId(id))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid PC ID");
        Integer enabled=db.query("select enabled from studio_workstation where device_id=?",
            rs->rs.next()?(rs.getBoolean(1)?1:0):null,id);
        if(enabled==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"PC not registered");
        if(enabled==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Enable this PC before generating a setup code");
        String secret=randomSecret();
        Instant expiration=Instant.now().plus(15,ChronoUnit.MINUTES);
        db.update("delete from studio_pairing where device_id=?",id);
        db.update("insert into studio_pairing(device_id,code_hash,expires_at,consumed_at) values(?,?,?,null)",
            id,digest(secret),Timestamp.from(expiration));
        String fullCode="ICS1|"+baseUrl+"|"+id+"|"+secret;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PairingOffer(
            id,fullCode,expiration.toString(),
            "Copy this setup code into the JavaFX desktop app within 15 minutes. Single use; generate a new one if needed."));
    }

    @Transactional
    @PostMapping("/pair")
    public ResponseEntity<PairedDevice> pair(@RequestBody PairingRequest request) {
        if(request==null || !validId(request.workstationId()) ||
            request.pairingCode()==null || !request.pairingCode().matches("[A-Za-z0-9_-]{43}"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid or expired PC setup code");
        // Device ID + random 256-bit code, never the shared viewer password.
        List<CodeRow> codes=db.query("select code_hash,expires_at,consumed_at from studio_pairing where device_id=?",
            (rs,row)->new CodeRow(rs.getString(1),rs.getTimestamp(2).toInstant(),
                rs.getTimestamp(3)!=null),request.workstationId());
        if(codes.isEmpty() || codes.getFirst().consumed() ||
            !codes.getFirst().expiresAt().isAfter(Instant.now()) ||
            !MessageDigest.isEqual(codes.getFirst().hash().getBytes(StandardCharsets.US_ASCII),
                digest(request.pairingCode()).getBytes(StandardCharsets.US_ASCII)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid or expired PC setup code");
        var devices=db.query("select display_name,screen_id from studio_workstation where device_id=? and enabled=true",
            (rs,row)->new Device(rs.getString(1),rs.getString(2)),request.workstationId());
        if(devices.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"PC disabled or unavailable");

        String newDeviceSecret=randomSecret();
        Timestamp now=Timestamp.from(Instant.now());
        int consumed=db.update("update studio_pairing set consumed_at=? where device_id=? and code_hash=? and consumed_at is null and expires_at>?",
            now,request.workstationId(),codes.getFirst().hash(),now);
        if(consumed!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Setup code was already used or expired");
        int updated=db.update("update studio_workstation set token_hash=?,updated_at=? where device_id=? and enabled=true",
            bcrypt.encode(newDeviceSecret),now,request.workstationId());
        if(updated!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"PC unavailable");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new PairedDevice(request.workstationId(),devices.getFirst().name(),devices.getFirst().screen(),
                baseUrl,newDeviceSecret));
    }
}
