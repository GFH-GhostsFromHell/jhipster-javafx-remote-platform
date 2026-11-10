package com.gfh.industrial;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Demo workstation provisioning. Workstation secret is minted once and only its bcrypt hash
 * is persisted. Viewer account + matching workstation secret are both required to download
 * a layout and its scoped simulator data. NOT a production device identity system.
 */
@RestController
@RequestMapping("/api/studio")
public final class ScreenApi {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final StudioApi telemetry;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom rng = new SecureRandom();

    public ScreenApi(JdbcTemplate db, ObjectMapper json, StudioApi telemetry) {
        this.db = db;
        this.json = json;
        this.telemetry = telemetry;
    }

    @PostConstruct
    public void init() {
        db.execute("create table if not exists studio_screen ("+
            "screen_id varchar(48) primary key, revision bigint not null, document text not null, updated_at timestamp not null)");
        db.execute("create table if not exists studio_workstation ("+
            "device_id varchar(48) primary key, display_name varchar(100) not null, "+
            "screen_id varchar(48) not null, token_hash varchar(100) not null, "+
            "enabled boolean not null, updated_at timestamp not null)");
        Integer n = db.queryForObject("select count(*) from studio_screen where screen_id='overview'",Integer.class);
        if (n != null && n == 0) {
            try {
                db.update("insert into studio_screen(screen_id, revision, document, updated_at) values(?,?,?,?)",
                    "overview",1L,json.writeValueAsString(ScreenLayout.example()),Timestamp.from(Instant.now()));
            } catch(Exception e) { throw new IllegalStateException("Cannot seed HMI sample screen",e); }
        }
    }

    public record ScreenRecord(String id,long revision,ScreenLayout layout,String updatedAt) {}
    public record NewScreen(String id,ScreenLayout layout) {}
    public record ChangeScreen(long expectedRevision,ScreenLayout layout) {}
    public record Workstation(String id,String name,String screenId,boolean enabled) {}
    public record NewWorkstation(String id,String name,String screenId) {}
    public record ChangeWorkstation(String name,String screenId,boolean enabled) {}
    public record WorkstationProvisioning(Workstation workstation,String oneTimeToken,String warning) {}
    public record ClientScreen(String workstationId,String workstationName,String screenId,long revision,ScreenLayout layout) {}

    private ScreenRecord read(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            return new ScreenRecord(rs.getString(1),rs.getLong(2),
                json.readValue(rs.getString(3),ScreenLayout.class),
                rs.getTimestamp(4).toInstant().toString());
        } catch(Exception e) { throw new IllegalStateException("Invalid saved HMI screen",e); }
    }

    private static void checkId(String id) {
        if(id==null || !id.matches("[A-Za-z][A-Za-z0-9_-]{1,47}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ID must be 2-48 letters, numbers, hyphens or underscores");
        }
    }

    private static void checkName(String name) {
        if(name==null || name.isBlank() || name.length()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Name must be 1-100 characters");
    }

    private void checkLayout(ScreenLayout layout) {
        if(layout==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Layout is required");
        Set<String> tags = new HashSet<>();
        telemetry.getConfig().configuration().tags().forEach(tag -> tags.add(tag.id()));
        try { layout.validate(tags); }
        catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage()); }
    }

    @GetMapping("/screens")
    public List<ScreenRecord> screens() {
        return db.query("select screen_id,revision,document,updated_at from studio_screen order by screen_id",
            (rs,row)->read(rs));
    }

    @PostMapping("/screens")
    public ScreenRecord createScreen(@RequestBody NewScreen request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Screen required");
        checkId(request.id()); checkLayout(request.layout());
        try {
            int changed=db.update("insert into studio_screen(screen_id,revision,document,updated_at) values(?,?,?,?)",
                request.id(),1,json.writeValueAsString(request.layout()),Timestamp.from(Instant.now()));
            if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Unable to create screen");
        } catch(org.springframework.dao.DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Screen ID already exists");
        } catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Unable to save screen");
        }
        return screen(request.id());
    }

    private ScreenRecord screen(String id) {
        List<ScreenRecord> results=db.query(
            "select screen_id,revision,document,updated_at from studio_screen where screen_id=?",
            (rs,row)->read(rs),id);
        if(results.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Screen not found");
        return results.getFirst();
    }

    @PutMapping("/screens/{id}")
    public ScreenRecord saveScreen(@PathVariable String id,@RequestBody ChangeScreen request) {
        checkId(id);
        if(request==null || request.expectedRevision()<1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid screen revision");
        checkLayout(request.layout());
        int changed;
        try {
            changed=db.update("update studio_screen set revision=revision+1,document=?,updated_at=? where screen_id=? and revision=?",
                json.writeValueAsString(request.layout()),Timestamp.from(Instant.now()),id,request.expectedRevision());
        } catch(Exception e){ throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Unable to save screen"); }
        if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Screen changed: reload before saving");
        return screen(id);
    }

    @GetMapping("/workstations")
    public List<Workstation> workstations() {
        return db.query("select device_id,display_name,screen_id,enabled from studio_workstation order by device_id",
            (rs,row)->new Workstation(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4)));
    }

    private void checkScreenExists(String id) {
        checkId(id);
        Integer count=db.queryForObject("select count(*) from studio_screen where screen_id=?",Integer.class,id);
        if(count==null || count==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown screen assignment");
    }

    @PostMapping("/workstations")
    public WorkstationProvisioning provision(@RequestBody NewWorkstation request) {
        if(request==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Workstation is required");
        checkId(request.id());checkName(request.name());checkScreenExists(request.screenId());
        byte[] bytes=new byte[32];rng.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try{
            db.update("insert into studio_workstation(device_id,display_name,screen_id,token_hash,enabled,updated_at) "+
                "values(?,?,?,?,?,?)",request.id(),request.name(),request.screenId(),encoder.encode(token),true,Timestamp.from(Instant.now()));
        }catch(org.springframework.dao.DuplicateKeyException e){
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Workstation ID already exists");
        }
        return new WorkstationProvisioning(new Workstation(request.id(),request.name(),request.screenId(),true),
            token,"Save this secret securely on the assigned PC. It will not be shown again.");
    }

    @PutMapping("/workstations/{id}")
    public Workstation updateWorkstation(@PathVariable String id,@RequestBody ChangeWorkstation request) {
        checkId(id);
        if(request==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Workstation data required");
        checkName(request.name());checkScreenExists(request.screenId());
        int n=db.update("update studio_workstation set display_name=?,screen_id=?,enabled=?,updated_at=? where device_id=?",
            request.name(),request.screenId(),request.enabled(),Timestamp.from(Instant.now()),id);
        if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown workstation");
        return new Workstation(id,request.name(),request.screenId(),request.enabled());
    }

    private record Assigned(String id,String name,String screenId) {}

    private Assigned authenticateDevice(String id,String token) {
        if(id==null || !id.matches("[A-Za-z][A-Za-z0-9_-]{1,47}") || token==null ||
            token.length()<20 || token.length()>128) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid workstation credentials");
        }
        List<String[]> devices=db.query("select display_name,screen_id,token_hash from studio_workstation where device_id=? and enabled=true",
            (rs,row)->new String[]{rs.getString(1),rs.getString(2),rs.getString(3)},id);
        if(devices.isEmpty() || !encoder.matches(token,devices.getFirst()[2]))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid workstation credentials");
        return new Assigned(id,devices.getFirst()[0],devices.getFirst()[1]);
    }

    private Assigned assigned(String id,String token) { return authenticateDevice(id,token); }

    @GetMapping("/client-screen")
    public ClientScreen clientScreen(
        @RequestHeader(value="X-Studio-Device-Id",required=false) String id,
        @RequestHeader(value="X-Studio-Device-Token",required=false) String token) {
        Assigned d=assigned(id,token);
        ScreenRecord s=screen(d.screenId());
        return new ClientScreen(d.id(),d.name(),s.id(),s.revision(),s.layout());
    }

    public record ClientSample(String id,String unit,double value,String quality,boolean alarm) {}

    @GetMapping("/client-live")
    public Map<String,Object> clientLive(
        @RequestHeader(value="X-Studio-Device-Id",required=false) String id,
        @RequestHeader(value="X-Studio-Device-Token",required=false) String token) {
        Assigned d=assigned(id,token);
        var screen=screen(d.screenId());
        Set<String> used=new HashSet<>();
        screen.layout().widgets().forEach(w->{ if(w.tagId()!=null)used.add(w.tagId()); });
        var base=telemetry.live();
        @SuppressWarnings("unchecked") var all=(List<StudioApi.Sample>)base.get("samples");
        var selected=new ArrayList<ClientSample>();
        for (var sample:all) if(used.contains(sample.id()))
            selected.add(new ClientSample(sample.id(),sample.unit(),sample.value(),sample.quality(),sample.alarm()));
        return Map.of("mode",base.get("mode"),"connected",base.get("connected"),"readOnly",true,"workstationId",d.id(),
            "screenId",screen.id(),"screenRevision",screen.revision(),"serverTime",base.get("serverTime"),
            "samples",selected);
    }
}
