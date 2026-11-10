package com.gfh.industrial;

import jakarta.annotation.PostConstruct;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Declarative HMI configuration update notices. The server never pushes executable files
 * or sends a command to restart a workstation; each enrolled client pulls changes and
 * explicitly chooses "Update now" before applying a new screen/configuration.
 *
 * Fingerprint covers shared tag configuration, assigned screen revision, PC assignment
 * and explicit administrator update requests. The current applied fingerprint is
 * acknowledged only by an authenticated workstation AFTER it loads the revised screen.
 */
@RestController
@RequestMapping("/api/studio")
public class WorkstationUpdateResource {
    private final JdbcTemplate db;
    private final ScreenApi screens;
    private final StudioApi config;

    public WorkstationUpdateResource(JdbcTemplate db, ScreenApi screens, StudioApi config) {
        this.db=db; this.screens=screens; this.config=config;
    }

    @PostConstruct
    public void init() {
        // Separate table: no modification or migration of existing workstation credentials.
        db.execute("create table if not exists studio_workstation_update (" +
            "device_id varchar(48) primary key, requested_revision bigint not null," +
            "applied_fingerprint varchar(200), applied_at timestamp)");
    }

    public record UpdateStatus(String workstationId, String workstationName, String screenId,
        boolean enabled, long screenRevision, long configurationRevision, long requestedRevision,
        String fingerprint, String appliedFingerprint, String appliedAt, boolean updatePending) {}
    public record UpdateAck(String fingerprint) {}

    private static void checkId(String id) {
        if(id==null || !id.matches("[A-Za-z][A-Za-z0-9_-]{1,47}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PC ID");
    }

    private record Delivery(long requested, String applied, String appliedAt) {}

    private Delivery delivery(String id) {
        var rows=db.query(
            "select requested_revision, applied_fingerprint, applied_at from studio_workstation_update where device_id=?",
            (rs,n)->new Delivery(rs.getLong(1),rs.getString(2),
                rs.getTimestamp(3)==null?null:rs.getTimestamp(3).toInstant().toString()),id);
        return rows.isEmpty()?new Delivery(0L,null,null):rows.getFirst();
    }

    private UpdateStatus state(ScreenApi.Workstation pc) {
        var layouts=screens.screens().stream().filter(s->s.id().equals(pc.screenId())).findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Assigned screen unavailable"));
        long confRev=config.getConfig().revision();
        Delivery delivery=delivery(pc.id());
        // The screen ID is part of the fingerprint, so two layouts with equal revision
        // numbers cannot accidentally appear equivalent after a PC is reassigned.
        Timestamp workstationChange=db.queryForObject(
            "select updated_at from studio_workstation where device_id=?",Timestamp.class,pc.id());
        // PC label/assignment changes must trigger a notice as well as screen/tag edits.
        // Include nanoseconds to avoid losing two rapid edits within one millisecond.
        String pcRevision=workstationChange==null?"0":
            workstationChange.toInstant().getEpochSecond()+"-"+workstationChange.toInstant().getNano();
        String desired=pc.screenId()+":"+layouts.revision()+":"+confRev+":"+
            delivery.requested()+":"+pcRevision;
        return new UpdateStatus(pc.id(),pc.name(),pc.screenId(),pc.enabled(),layouts.revision(),confRev,
            delivery.requested(),desired,delivery.applied(),delivery.appliedAt(),
            !desired.equals(delivery.applied()));
    }

    private ScreenApi.Workstation workstation(String id) {
        checkId(id);
        return screens.workstations().stream().filter(s->s.id().equals(id)).findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"PC not registered"));
    }

    @GetMapping("/workstations/update-status")
    public List<UpdateStatus> all() {
        return screens.workstations().stream().map(this::state).toList();
    }

    @GetMapping("/client-update-status")
    public ResponseEntity<UpdateStatus> clientStatus(
        @RequestHeader(value="X-Studio-Device-Id",required=false) String id,
        @RequestHeader(value="X-Studio-Device-Token",required=false) String token) {
        screens.clientScreen(id,token); // Re-validate enabled PC and hashed device token.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(state(workstation(id)));
    }

    @GetMapping("/client-app-manifest")
    public ResponseEntity<Map<String,Object>> clientApplicationRelease(
        @RequestHeader(value="X-Studio-Device-Id",required=false) String id,
        @RequestHeader(value="X-Studio-Device-Token",required=false) String token) {
        screens.clientScreen(id,token); // Endpoint is accessible only to a registered and enabled PC.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(config.manifest());
    }

    @Transactional
    @PostMapping("/workstations/{id}/request-update")
    public UpdateStatus requestUpdate(@PathVariable String id) {
        ScreenApi.Workstation pc=workstation(id);
        if(!pc.enabled())throw new ResponseStatusException(HttpStatus.CONFLICT,"PC is disabled");
        int changed=db.update(
            "update studio_workstation_update set requested_revision=requested_revision+1 where device_id=?",id);
        if(changed==0) {
            try {
                db.update("insert into studio_workstation_update(device_id,requested_revision) values(?,1)",id);
            }catch(org.springframework.dao.DuplicateKeyException race) {
                db.update("update studio_workstation_update set requested_revision=requested_revision+1 where device_id=?",id);
            }
        }
        return state(pc);
    }

    @Transactional
    @PostMapping("/client-update-ack")
    public ResponseEntity<UpdateStatus> acknowledge(
        @RequestHeader(value="X-Studio-Device-Id",required=false) String id,
        @RequestHeader(value="X-Studio-Device-Token",required=false) String token,
        @RequestBody UpdateAck ack) {
        screens.clientScreen(id,token);
        if(ack==null || ack.fingerprint()==null || ack.fingerprint().length()>200 ||
           !ack.fingerprint().matches("[A-Za-z0-9_:-]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid update revision");
        var pc=workstation(id);
        UpdateStatus expected=state(pc);
        if(!expected.fingerprint().equals(ack.fingerprint()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Server configuration changed again; refresh before applying");
        Timestamp now=Timestamp.from(Instant.now());
        int changed=db.update(
            "update studio_workstation_update set applied_fingerprint=?,applied_at=? where device_id=? and requested_revision=?",
            ack.fingerprint(),now,id,expected.requestedRevision());
        if(changed==0 && expected.requestedRevision()==0) {
            try {
                db.update("insert into studio_workstation_update(device_id,requested_revision,applied_fingerprint,applied_at) values(?,?,?,?)",
                    id,0,ack.fingerprint(),now);
            }catch(org.springframework.dao.DuplicateKeyException race) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Server configuration changed; retry");
            }
        } else if(changed==0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Update request changed; retry");
        }
        // Rechecking after the acknowledgement guards against a concurrent screen/config update.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(state(pc));
    }
}
