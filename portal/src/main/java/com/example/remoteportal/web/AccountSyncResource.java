package com.example.remoteportal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Per-account, per-record synchronization. All records are namespaced by the authenticated
 * principal (never by a caller-provided username). Revisions use compare-and-swap.
 * A deleted record is a tombstone so an offline device cannot silently resurrect it.
 */
@RestController
@RequestMapping("/api/sync")
public class AccountSyncResource {
  private static final Set<String> BUCKETS = Set.of(
      "projects", "materials", "nameplates", "commissioning", "inspections",
      "hours", "credentials", "quizHistory", "unsafeConditions", "rubberGloves",
      "safetyMaintenance", "stormStandDowns", "safetyAssessments", "benderProfiles",
      "troubleshootingCases", "guidedSolutions", "practiceDrafts", "designReports",
      "panelSchedules", "motorDesigns", "transformerDesigns", "estimates",
      "calculatorFavorites", "recentCalculations", "customPresets", "preferences",
      "fieldProjects", "fieldRecords");
  private static final int MAX_RECORDS = 200;
  private static final int MAX_JSON_CHARS = 64000;
  private final JdbcTemplate db;
  private final ObjectMapper mapper;

  public AccountSyncResource(JdbcTemplate db, ObjectMapper mapper) {
    this.db = db;
    this.mapper = mapper;
  }

  @PostConstruct
  public void initialize() {
    db.execute("""
       create table if not exists account_sync_record (
         username varchar(255) not null,
         bucket varchar(64) not null,
         record_id varchar(128) not null,
         revision bigint not null,
         payload text not null,
         deleted boolean not null default false,
         updated_at timestamp not null,
         primary key (username, bucket, record_id)
       )
       """);
    db.execute("create index if not exists idx_account_sync_user_bucket on account_sync_record(username,bucket)");
  }

  public record SyncItem(String id, Long revision, JsonNode data, Boolean deleted) {}
  public record BatchRequest(List<SyncItem> records) {}
  public record SyncRecord(String id, long revision, JsonNode data, boolean deleted, String updatedAt) {}
  public record BatchResponse(List<SyncRecord> accepted, List<SyncRecord> conflicts) {}

  @GetMapping("/{bucket}")
  public List<SyncRecord> list(@PathVariable String bucket, Principal principal) {
    checkedBucket(bucket);
    return db.query("""
        select record_id,revision,payload,deleted,updated_at
        from account_sync_record
        where username=? and bucket=?
        order by updated_at, record_id
        limit 5000
        """, (rs, row) -> read(rs.getString(1),rs.getLong(2),rs.getString(3),
            rs.getBoolean(4),rs.getTimestamp(5)), principal.getName(), bucket);
  }

  @PostMapping("/{bucket}/batch")
  public BatchResponse push(@PathVariable String bucket, @RequestBody BatchRequest batch, Principal principal) {
    checkedBucket(bucket);
    if (batch == null || batch.records() == null || batch.records().size() > MAX_RECORDS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Submit at most 200 records per batch");
    }
    var accepted = new ArrayList<SyncRecord>();
    var conflicts = new ArrayList<SyncRecord>();
    var username = principal.getName();
    var seen = new HashSet<String>();
    for (SyncItem item : batch.records()) {
      if (item == null || item.id() == null || !item.id().matches("[A-Za-z0-9_.:-]{1,128}")
          || !seen.add(item.id()) || item.revision() == null || item.revision() < 0
          || item.data() == null || (item.deleted() == null)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ID, revision or payload");
      }
      String payload;
      try { payload = mapper.writeValueAsString(item.data()); }
      catch(Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload"); }
      if (payload.length() > MAX_JSON_CHARS) {
        throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Record exceeds 64,000 characters");
      }
      long next = item.revision() + 1;
      Timestamp now = Timestamp.from(Instant.now());
      int modified;
      if (item.revision() == 0) {
        modified = db.update("""
          insert into account_sync_record(username,bucket,record_id,revision,payload,deleted,updated_at)
          values(?,?,?,?,?,?,?)
          on conflict(username,bucket,record_id) do nothing
          """,username,bucket,item.id(),1,payload,item.deleted(),now);
      } else {
        modified = db.update("""
          update account_sync_record set revision=?,payload=?,deleted=?,updated_at=?
          where username=? and bucket=? and record_id=? and revision=?
          """,next,payload,item.deleted(),now,username,bucket,item.id(),item.revision());
      }
      if (modified == 1) {
        accepted.add(new SyncRecord(item.id(),next,item.data(),item.deleted(),now.toInstant().toString()));
      } else {
        var current = db.query("""
          select record_id,revision,payload,deleted,updated_at from account_sync_record
          where username=? and bucket=? and record_id=?
          """,(rs,row)->read(rs.getString(1),rs.getLong(2),rs.getString(3),
              rs.getBoolean(4),rs.getTimestamp(5)),username,bucket,item.id());
        if (current.isEmpty()) {
          conflicts.add(new SyncRecord(item.id(),0,mapper.createObjectNode(),true,""));
        } else {
          conflicts.add(current.getFirst());
        }
      }
    }
    return new BatchResponse(accepted,conflicts);
  }

  private SyncRecord read(String id,long revision,String raw,boolean deleted,Timestamp time) {
    try { return new SyncRecord(id,revision,mapper.readTree(raw),deleted,time.toInstant().toString()); }
    catch(Exception ex) { throw new IllegalStateException("Invalid stored sync record: "+id,ex); }
  }
  private static void checkedBucket(String bucket) {
    if (!BUCKETS.contains(bucket)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported sync collection");
    }
  }
}
