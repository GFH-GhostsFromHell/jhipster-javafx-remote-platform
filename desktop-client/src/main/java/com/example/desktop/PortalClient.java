package com.example.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public final class PortalClient {
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper json = new ObjectMapper();

    public PortalClient() { this.baseUrl = System.getenv().getOrDefault("PORTAL_URL", "https://remote-javafx-portal.onrender.com").replaceAll("/$", ""); }
    public String baseUrl() { return baseUrl; }

    public boolean health() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/public/client-health")).timeout(Duration.ofSeconds(120)).GET().build();
        HttpResponse<String> res = sendWithRetry(req, 3, 2500); return res.statusCode() == 200;
    }

    public String bootstrap(String username,String password) throws Exception{
        String body=authorizedGet("/api/client/bootstrap",username,password);
        // Ask ChatGPT is a local desktop capability backed by the user's own
        // OpenAI authorization. Keep it additive even if an older portal has not
        // yet advertised the new feature entitlement.
        try{
            ObjectNode root=(ObjectNode)json.readTree(body);
            ObjectNode features=root.withObject("features");
            features.put("ASK_CHATGPT",true);
            return json.writeValueAsString(root);
        }catch(Exception ignored){
            return body;
        }
    }
    public String profile(String username,String password) throws Exception{return authorizedGet("/api/profile",username,password);}
    public String updateProfile(String username,String password,String jsonBody) throws Exception{return authorizedJson("/api/profile","PUT",username,password,jsonBody);}
    public String changePassword(String username,String password,String jsonBody) throws Exception{return authorizedJson("/api/profile/password","PUT",username,password,jsonBody);}
    public String calculatorCatalog(String username,String password) throws Exception{return authorizedGet("/api/calculators/catalog",username,password);}
    public String calculatorEditions(String username,String password) throws Exception{return authorizedGet("/api/calculators/editions",username,password);}
    public String transformerCalculator(String username,String password,String body) throws Exception{return calc("transformer",username,password,body);}
    public String dryTransformerWorksheet(String username,String password,String body) throws Exception{return calc("transformer-dry-worksheet",username,password,body);}
    public String hvTransformerWorksheet(String username,String password,String body) throws Exception{return calc("transformer-hv-worksheet",username,password,body);}
    public String capacitorWorksheet(String username,String password,String body) throws Exception{return calc("capacitor-worksheet",username,password,body);}
    public String refrigerationWorksheet(String username,String password,String body) throws Exception{return calc("refrigeration-worksheet",username,password,body);}
    public String welderWorksheet(String username,String password,String body) throws Exception{return calc("welder-worksheet",username,password,body);}
    public String motorCalculator(String username,String password,String body) throws Exception{return calc("motor",username,password,body);}
    public String refrigerationCalculator(String username,String password,String body) throws Exception{return calc("refrigeration",username,password,body);}
    public String multiMotorCalculator(String username,String password,String body) throws Exception{return calc("multi-motor",username,password,body);}
    public String capacitorBankCalculator(String username,String password,String body) throws Exception{return calc("capacitor-bank",username,password,body);}
    public String welderCalculator(String username,String password,String body) throws Exception{return calc("welder",username,password,body);}
    public String tapCalculator(String username,String password,String body) throws Exception{return calc("tap",username,password,body);}
    public String conduitFillCalculator(String username,String password,String body) throws Exception{return calc("conduit-fill",username,password,body);}
    public String boxFillCalculator(String username,String password,String body) throws Exception{return calc("box-fill",username,password,body);}
    public String serviceCalculator(String username,String password,String body) throws Exception{return calc("service",username,password,body);}
    public String residentialServiceCalculator(String username,String password,String body) throws Exception{return serviceCalc("residential",username,password,body);}
    public String apartmentServiceCalculator(String username,String password,String body) throws Exception{return serviceCalc("apartment",username,password,body);}
    public String schoolServiceCalculator(String username,String password,String body) throws Exception{return serviceCalc("school",username,password,body);}
    public String hospitalServiceCalculator(String username,String password,String body) throws Exception{return serviceCalc("hospital",username,password,body);}
    public String otherServiceCalculator(String username,String password,String body) throws Exception{return serviceCalc("other",username,password,body);}
    private String calc(String path,String username,String password,String body) throws Exception{return authorizedJson("/api/calculators/"+path,"POST",username,password,body);}
    private String serviceCalc(String path,String username,String password,String body) throws Exception{return authorizedJson("/api/calculators/service/"+path,"POST",username,password,body);}

    private String authorizedGet(String path,String username,String password) throws Exception {
        HttpRequest req=HttpRequest.newBuilder(URI.create(baseUrl+path)).timeout(Duration.ofSeconds(150)).header("Authorization",basic(username,password)).GET().build();
        return bodyOrThrow(sendWithRetry(req,3,3000));
    }
    private String authorizedJson(String path,String method,String username,String password,String jsonBody) throws Exception {
        HttpRequest req=HttpRequest.newBuilder(URI.create(baseUrl+path)).timeout(Duration.ofSeconds(150)).header("Authorization",basic(username,password)).header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(jsonBody==null?"{}":jsonBody,StandardCharsets.UTF_8)).build();
        return bodyOrThrow(sendWithRetry(req,3,3000));
    }
    private static String basic(String username,String password){return "Basic "+Base64.getEncoder().encodeToString((username+":"+password).getBytes(StandardCharsets.UTF_8));}
    private static String bodyOrThrow(HttpResponse<String> res){if(res.statusCode()==401||res.statusCode()==403)throw new SecurityException("Invalid username/password or account disabled");if(res.statusCode()<200||res.statusCode()>=300){String d=res.body()==null||res.body().isBlank()?"":": "+res.body();throw new IllegalStateException("Portal returned HTTP "+res.statusCode()+d);}return res.body();}
    private HttpResponse<String> sendWithRetry(HttpRequest req,int attempts,long delayMs) throws Exception {Exception last=null;for(int i=1;i<=attempts;i++){try{return http.send(req,HttpResponse.BodyHandlers.ofString());}catch(java.net.http.HttpTimeoutException|java.net.ConnectException e){last=e;if(i<attempts)Thread.sleep(delayMs);}}if(last!=null)throw last;throw new IllegalStateException("Request failed");}
}
