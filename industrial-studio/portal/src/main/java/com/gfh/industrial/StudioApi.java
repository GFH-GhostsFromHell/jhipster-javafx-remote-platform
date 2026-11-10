package com.gfh.industrial;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/studio")
public class StudioApi {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public StudioApi(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }

    @PostConstruct
    public void init() {
        db.execute("create table if not exists studio_config (id integer primary key, revision bigint not null, document text not null, updated_at timestamp not null)");
        Integer n = db.queryForObject("select count(*) from studio_config where id=1", Integer.class);
        if (n == 0) try {
            db.update("insert into studio_config(id,revision,document,updated_at) values(1,1,?,?)",
                json.writeValueAsString(StudioConfig.example()), Timestamp.from(Instant.now()));
        } catch (Exception e) { throw new IllegalStateException("Cannot seed studio configuration", e); }
    }

    public record Versioned(long revision, StudioConfig configuration) {}
    public record Update(long expectedRevision, StudioConfig configuration) {}
    public record Sample(String id, String label, String unit, double value, String quality, String nodeId, boolean alarm) {}

    @GetMapping("/config")
    public Versioned getConfig() {
        return db.queryForObject("select revision,document from studio_config where id=1", (rs,n) -> {
            try { return new Versioned(rs.getLong(1), json.readValue(rs.getString(2), StudioConfig.class)); }
            catch (Exception e) { throw new IllegalStateException("Stored configuration is invalid",e); }
        });
    }

    @PutMapping("/config")
    public Versioned putConfig(@RequestBody Update update) {
        if (update == null || update.configuration() == null || update.expectedRevision() < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid configuration submission");
        try { update.configuration().validate(); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
        try {
            int updated = db.update("update studio_config set revision=revision+1, document=?, updated_at=? where id=1 and revision=?",
                json.writeValueAsString(update.configuration()), Timestamp.from(Instant.now()), update.expectedRevision());
            if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Configuration changed; reload before saving");
            return getConfig();
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save configuration"); }
    }

    @GetMapping("/live")
    public Map<String,Object> live() {
        Versioned c = getConfig();
        long now = System.currentTimeMillis();
        List<Sample> samples = new ArrayList<>();
        for (int i=0;i<c.configuration().tags().size();i++) {
            StudioConfig.Tag t = c.configuration().tags().get(i);
            double raw = 50 + 30*Math.sin(now/8500d+i*1.31);
            double value = Math.round(raw*10.0)/10.0;
            boolean alarm = (t.lowAlarm()!=null && value<t.lowAlarm()) ||
                (t.highAlarm()!=null && value>t.highAlarm());
            samples.add(new Sample(t.id(),t.label(),t.unit(),value,"SIMULATED",t.nodeId(),alarm));
        }
        return Map.of("mode","SIMULATION", "readOnly",true, "configurationRevision",c.revision(),
            "serverTime",Instant.ofEpochMilli(now).toString(),"samples",samples,
            "message","Not connected to Kepware or any PLC");
    }

    @GetMapping("/status")
    public Map<String,Object> status() {
        return Map.of("version","0.1.0-SNAPSHOT","mode","SIMULATION","readOnly",true,
            "javaDraftExecution","DISABLED","opcUaConnected",false, "configurationRevision",getConfig().revision());
    }

    @GetMapping("/update-manifest")
    public Map<String,Object> manifest() {
        return Map.of("version","0.1.0-SNAPSHOT","channel","development",
            "downloadUrl","","sha256","","released",false);
    }
}
