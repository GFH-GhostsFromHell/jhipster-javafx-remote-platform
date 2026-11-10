package com.gfh.industrial.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/** Headless outbound-only collector. Does not execute cloud-authored scripts or write PLC tags. */
public final class EdgeCollector {
    private final HttpClient http=HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json=new ObjectMapper();
    private final String base;
    private final String id;
    private final String token;
    private final long started=System.currentTimeMillis();

    private EdgeCollector(String base,String id,String token) {
        this.base=validBase(base);
        if(!id.matches("[A-Za-z][A-Za-z0-9_-]{1,47}")||
           !token.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("Invalid collector identity or secret");
        this.id=id;this.token=token;
    }

    static String validBase(String raw) {
        if(raw==null||raw.isBlank()||raw.length()>240)
            throw new IllegalArgumentException("Set STUDIO_EDGE_CLOUD_URL");
        URI uri=URI.create(raw.trim());
        boolean https="https".equals(uri.getScheme());
        boolean local="http".equals(uri.getScheme()) &&
            ("127.0.0.1".equals(uri.getHost())||"localhost".equals(uri.getHost()));
        if((!https&&!local)||uri.getUserInfo()!=null||uri.getFragment()!=null||
            uri.getRawQuery()!=null||!("/".equals(uri.getPath())||uri.getPath().isBlank()))
            throw new IllegalArgumentException("Cloud URL must be HTTPS or loopback HTTP");
        return uri.toString().replaceAll("/$","");
    }

    private HttpRequest.Builder request(String suffix) {
        return HttpRequest.newBuilder(URI.create(base+"/api/studio/edge-clients/"+id+suffix))
            .header("Accept","application/json")
            .header("Cache-Control","no-store")
            .header("X-Studio-Edge-Token",token)
            .timeout(Duration.ofSeconds(15));
    }

    private JsonNode getConfig() throws Exception {
        HttpResponse<String> response=http.send(request("/config").GET().build(),
            HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)
            throw new IllegalStateException("Cloud config unavailable HTTP "+response.statusCode());
        JsonNode config=json.readTree(response.body());
        if(!id.equals(config.path("id").asText("")) ||
            config.path("plcWritesEnabled").asBoolean(true) ||
            !config.path("configuration").path("enabled").asBoolean(false))
            throw new IllegalStateException("Unexpected or disabled collector configuration");
        return config;
    }

    static Map<String,Double> simulation(JsonNode tags,long millis){
        Map<String,Double> data=new LinkedHashMap<>();
        int i=0;
        for(JsonNode tag:tags){
            if(++i>50)throw new IllegalArgumentException("Too many PLC read tags");
            String key=tag.path("id").asText("");
            if(!key.matches("[A-Za-z][A-Za-z0-9_]{0,49}")||data.containsKey(key))
                throw new IllegalArgumentException("Invalid source tag ID");
            double raw=50+25*Math.sin(millis/9000d+i*1.13)+5*Math.sin(millis/29000d);
            data.put(key,Math.round(raw*10d)/10d);
        }
        return data;
    }

    private void send(long revision,String quality,Map<String,Double> values) throws Exception {
        if(values.isEmpty())throw new IllegalStateException("No fresh approved-quality samples");
        List<Map<String,Object>> samples=new ArrayList<>();
        for(var entry:values.entrySet()){
            if(!Double.isFinite(entry.getValue()))continue;
            samples.add(Map.of("id",entry.getKey(),"value",entry.getValue(),"quality",quality));
        }
        if(samples.isEmpty())throw new IllegalStateException("No finite samples");
        String body=json.writeValueAsString(Map.of("configurationRevision",revision,"samples",samples));
        HttpRequest call=request("/samples")
            .header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response=http.send(call,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)
            throw new IllegalStateException("Telemetry rejected HTTP "+response.statusCode());
        JsonNode acknowledgement=json.readTree(response.body());
        if(acknowledgement.path("plcWritesPerformed").asBoolean(true) ||
           !"ACCEPTED_DRY_RUN".equals(acknowledgement.path("status").asText()))
            throw new IllegalStateException("Unexpected write-capable response; refusing to continue");
        System.out.println("["+java.time.Instant.now()+"] "+id+
            " · "+acknowledgement.path("acceptedSamples").asInt()+" fresh points, "+
            acknowledgement.path("simulatedWriteProposals").asInt()+" simulated write proposals; PLC writes disabled");
    }

    private void loop() throws Exception {
        try(KepwareReadOnlyOpc opc=new KepwareReadOnlyOpc()){
            while(!Thread.currentThread().isInterrupted()){
                try {
                    JsonNode snapshot=getConfig();
                    JsonNode config=snapshot.path("configuration");
                    String mode=config.path("sourceMode").asText("");
                    long rev=snapshot.path("revision").asLong(-1);
                    if(rev<1)throw new IllegalStateException("Missing configuration revision");
                    if("SIMULATION".equals(mode)){
                        send(rev,"SIMULATED",simulation(config.path("tags"),
                            System.currentTimeMillis()-started));
                    }else if("KEPWARE_READ".equals(mode)){
                        send(rev,"GOOD",opc.read(config.path("tags")));
                    }else throw new IllegalStateException("Unknown source mode");
                }catch(Exception error){
                    // Never reveal passwords or tokens in log output; keep the collector fail-closed.
                    System.err.println("["+java.time.Instant.now()+"] Collector unavailable: "+
                        error.getClass().getSimpleName()+" (no PLC commands were sent)");
                }
                Thread.sleep(5000);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String base=System.getenv().getOrDefault("STUDIO_EDGE_CLOUD_URL","");
        String id=System.getenv().getOrDefault("STUDIO_EDGE_ID","");
        String token=System.getenv().getOrDefault("STUDIO_EDGE_TOKEN","");
        if(base.isBlank()||id.isBlank()||token.isBlank()){
            System.err.println("Provide STUDIO_EDGE_CLOUD_URL, STUDIO_EDGE_ID and STUDIO_EDGE_TOKEN as protected service environment variables.");
            System.exit(2);
        }
        new EdgeCollector(base,id,token).loop();
    }
}
