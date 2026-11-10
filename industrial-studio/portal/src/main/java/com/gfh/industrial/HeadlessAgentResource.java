package com.gfh.industrial;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Separate headless collector registry. The cloud stores per-client configurations,
 * accepts bounded read-only observations and evaluates dry-run output proposals.
 * No route here accepts commands destined for a PLC or Kepware.
 */
@RestController
@RequestMapping("/api/studio")
public final class HeadlessAgentResource {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final SecureRandom random=new SecureRandom();
    private final BCryptPasswordEncoder bcrypt=new BCryptPasswordEncoder();
    private final ConcurrentHashMap<String,Telemetry> recent=new ConcurrentHashMap<>();

    public record Tag(String id,String label,String nodeId,String unit) {}
    public record Definition(String name,String sourceMode,boolean enabled,
                             List<Tag> tags,String script) {}
    public record Create(String id,Definition configuration) {}
    public record RevisionUpdate(long expectedRevision,Definition configuration) {}
    public record Sample(String id,double value,String quality) {}
    public record SampleBatch(long configurationRevision,List<Sample> samples) {}
    public record Telemetry(String observedAt,List<Sample> samples,
                            List<HeadlessLogic.Proposal> proposedWrites) {}
    public record AgentView(String id,long revision,Definition configuration,
                            String lastSeen,boolean recentlySeen,
                            List<Sample> lastSamples,
                            List<HeadlessLogic.Proposal> proposedWrites) {}
    public record Enrollment(AgentView agent,String oneTimeToken,String warning) {}
    public record ClientConfig(String id,long revision,Definition configuration,
                               boolean plcWritesEnabled) {}
    public record Accepted(String status,String timestamp,int acceptedSamples,
                           int simulatedWriteProposals,boolean plcWritesPerformed) {}
    public record Preview(Map<String,Double> inputs,
                          List<HeadlessLogic.Proposal> proposedWrites,
                          boolean plcWritesPerformed) {}

    private record Stored(String id,long revision,Definition configuration,String tokenHash) {}

    public HeadlessAgentResource(JdbcTemplate db,ObjectMapper json){
        this.db=db;this.json=json;
    }

    @PostConstruct
    public void init(){
        db.execute("create table if not exists studio_headless_agent ("+
            "agent_id varchar(48) primary key, revision bigint not null,"+
            "document text not null, token_hash varchar(100) not null,"+
            "updated_at timestamp not null)");
    }

    private String newSecret(){
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static void validId(String id){
        if(id==null||!id.matches("[A-Za-z][A-Za-z0-9_-]{1,47}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Collector ID must be 2-48 letters, numbers, hyphens or underscores");
    }
    private static Set<String> validate(Definition config){
        if(config==null||config.name()==null||config.name().isBlank()||
            config.name().length()>100)
            throw new IllegalArgumentException("Collector name must be 1-100 characters");
        if(config.sourceMode()==null||
            !Set.of("SIMULATION","KEPWARE_READ").contains(config.sourceMode()))
            throw new IllegalArgumentException("Choose SIMULATION or KEPWARE_READ");
        if(config.tags()==null||config.tags().isEmpty()||config.tags().size()>50)
            throw new IllegalArgumentException("Configure between 1 and 50 read-only tags");
        var ids=new HashSet<String>();
        for(Tag tag:config.tags()){
            if(tag==null||tag.id()==null||
                !tag.id().matches("[A-Za-z][A-Za-z0-9_]{0,49}")||
                !ids.add(tag.id()))
                throw new IllegalArgumentException("Tags require unique alphanumeric IDs");
            if(tag.label()==null||tag.label().isBlank()||tag.label().length()>100||
                tag.unit()==null||tag.unit().length()>30||
                tag.nodeId()==null||tag.nodeId().length()>250||
                !tag.nodeId().matches("ns=[0-9]+;[isgb]=.+"))
                throw new IllegalArgumentException("Each tag needs a label, unit and OPC UA NodeId");
        }
        HeadlessLogic.compile(config.script(),ids);
        return ids;
    }
    private Stored parse(java.sql.ResultSet row) throws java.sql.SQLException{
        try{
            return new Stored(row.getString("agent_id"),row.getLong("revision"),
                json.readValue(row.getString("document"),Definition.class),
                row.getString("token_hash"));
        }catch(Exception error){throw new IllegalStateException("Invalid collector configuration",error);}
    }
    private Stored stored(String id){
        validId(id);
        var results=db.query("select agent_id,revision,document,token_hash from studio_headless_agent where agent_id=?",
            (rs,n)->parse(rs),id);
        if(results.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Unknown headless collector");
        return results.getFirst();
    }
    private Stored authorized(String id,String token){
        Stored value=stored(id);
        if(!value.configuration().enabled()||token==null||token.length()<40||
            token.length()>128||!bcrypt.matches(token,value.tokenHash()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Collector not authorized");
        return value;
    }
    private AgentView view(Stored value){
        Telemetry t=recent.get(value.id());
        Instant seen=t==null?null:Instant.parse(t.observedAt());
        boolean connected=seen!=null && seen.isAfter(Instant.now().minusSeconds(30));
        return new AgentView(value.id(),value.revision(),value.configuration(),
            t==null?"":t.observedAt(),connected,
            t==null?List.of():t.samples(),
            t==null?List.of():t.proposedWrites());
    }

    // Admin only; no passwords/hashes appear in list or detail responses.
    @GetMapping("/headless-agents")
    public List<AgentView> list(){
        return db.query("select agent_id,revision,document,token_hash from studio_headless_agent order by agent_id",
            (rs,n)->view(parse(rs)));
    }
    @PostMapping("/headless-agents")
    public ResponseEntity<Enrollment> create(@RequestBody Create request){
        if(request==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Collector required");
        validId(request.id());
        try{validate(request.configuration());}
        catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
        String token=newSecret();
        try{
            db.update("insert into studio_headless_agent(agent_id,revision,document,token_hash,updated_at) values(?,?,?,?,?)",
                request.id(),1,json.writeValueAsString(request.configuration()),
                bcrypt.encode(token),Timestamp.from(Instant.now()));
        }catch(org.springframework.dao.DuplicateKeyException e){
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Collector ID already exists");
        }catch(Exception e){
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Unable to create collector");
        }
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(new Enrollment(view(stored(request.id())),token,
                "Save the secret only on the approved edge computer. It will not be shown again."));
    }
    @PutMapping("/headless-agents/{id}")
    public AgentView update(@PathVariable String id,@RequestBody RevisionUpdate request){
        validId(id);
        if(request==null||request.expectedRevision()<1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Expected revision required");
        try{validate(request.configuration());}
        catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
        int n;
        try{
            n=db.update("update studio_headless_agent set revision=revision+1,document=?,updated_at=? where agent_id=? and revision=?",
                json.writeValueAsString(request.configuration()),Timestamp.from(Instant.now()),
                id,request.expectedRevision());
        }catch(Exception e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Unable to save collector");}
        if(n!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Collector was changed by another editor; refresh first");
        recent.remove(id); // no stale values under a changed mapping
        return view(stored(id));
    }
    @PostMapping("/headless-agents/{id}/rotate-token")
    public ResponseEntity<Enrollment> rotate(@PathVariable String id){
        Stored existing=stored(id);
        String token=newSecret();
        db.update("update studio_headless_agent set token_hash=?,updated_at=? where agent_id=?",
            bcrypt.encode(token),Timestamp.from(Instant.now()),existing.id());
        recent.remove(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new Enrollment(view(stored(id)),token,
                "Old collector token revoked immediately. Configure this new secret on the approved edge PC."));
    }
    @PostMapping("/headless-agents/{id}/preview")
    public Preview preview(@PathVariable String id,@RequestBody Map<String,Double> input){
        Stored value=stored(id);
        Set<String> known=validate(value.configuration());
        if(input==null||input.size()>50||!known.containsAll(input.keySet())||
            input.values().stream().anyMatch(v->v==null||!Double.isFinite(v)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provide finite values only for configured input tags");
        return new Preview(Map.copyOf(input),
            HeadlessLogic.preview(HeadlessLogic.compile(value.configuration().script(),known),input),false);
    }

    // Authenticated by per-edge-client token; no shared admin/viewer password required.
    @GetMapping("/edge-clients/{id}/config")
    public ResponseEntity<ClientConfig> clientConfig(@PathVariable String id,
        @RequestHeader(value="X-Studio-Edge-Token",required=false) String token){
        Stored value=authorized(id,token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new ClientConfig(value.id(),value.revision(),value.configuration(),false));
    }
    @PostMapping("/edge-clients/{id}/samples")
    public ResponseEntity<Accepted> collect(@PathVariable String id,
        @RequestHeader(value="X-Studio-Edge-Token",required=false) String token,
        @RequestBody SampleBatch batch){
        Stored value=authorized(id,token);
        if(batch==null||batch.configurationRevision()!=value.revision()||
            batch.samples()==null||batch.samples().isEmpty()||batch.samples().size()>50)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid or outdated collector sample batch");
        Set<String> allowed=validate(value.configuration());
        Map<String,Double> inputs=new HashMap<>();
        List<Sample> verified=new ArrayList<>();
        String expectedQuality=value.configuration().sourceMode().equals("SIMULATION")?"SIMULATED":"GOOD";
        for(Sample sample:batch.samples()){
            if(sample==null||!allowed.contains(sample.id())||
                !Double.isFinite(sample.value())||!expectedQuality.equals(sample.quality())||
                inputs.putIfAbsent(sample.id(),sample.value())!=null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Samples must have allowed IDs, finite values and valid source quality");
            verified.add(sample);
        }
        var proposals=HeadlessLogic.preview(
            HeadlessLogic.compile(value.configuration().script(),allowed),inputs);
        String observed=Instant.now().toString();
        recent.put(id,new Telemetry(observed,List.copyOf(verified),proposals));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Accepted("ACCEPTED_DRY_RUN",observed,verified.size(),proposals.size(),false));
    }
}
