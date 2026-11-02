package com.example.remoteportal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Server-side Sign in with ChatGPT integration for the hosted web portal.
 *
 * OpenAI treats remotely hosted websites differently from local/open-source
 * runtimes: a website needs an OpenAI-provisioned oaiapp_* OAuth client and
 * ChatGPT-plan sharing must be approved for that client. Until those settings
 * are supplied, this service reports itself as not configured and the portal
 * presents an explanatory disabled state rather than exposing a fake sign-in.
 */
@Service
public class ChatGptWebService {
  private static final String AUTH="https://auth.openai.com/api/accounts/authorize";
  private static final String TOKEN="https://auth.openai.com/api/accounts/oauth/token";
  private static final String REVOKE="https://auth.openai.com/api/accounts/oauth/revoke";
  private static final String JWKS="https://auth.openai.com/.well-known/jwks.json";
  private static final String ISSUER="https://auth.openai.com";
  private static final String RESOURCE="https://api.openai.com/v1";
  private static final String MODELS="https://api.openai.com/v1/models";
  private static final String RESPONSES="https://api.openai.com/v1/responses";
  private static final String IDENTITY_SCOPES="openid profile email offline_access";
  private static final String PLAN_SCOPES="openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";

  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();
  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;
  private final boolean planSharingEnabled;
  private final byte[] encryptionKey;
  private final Map<String,PendingAuth> pending=new ConcurrentHashMap<>();

  public ChatGptWebService(
      JdbcTemplate db,
      ObjectMapper json,
      @Value("${OPENAI_CHATGPT_CLIENT_ID:}") String clientId,
      @Value("${OPENAI_CHATGPT_CLIENT_SECRET:}") String clientSecret,
      @Value("${OPENAI_CHATGPT_REDIRECT_URI:https://remote-javafx-portal.onrender.com/api/chatgpt/callback}") String redirectUri,
      @Value("${OPENAI_CHATGPT_PLAN_SHARING_ENABLED:false}") boolean planSharingEnabled,
      @Value("${OPENAI_CHATGPT_TOKEN_KEY:}") String tokenKey) {
    this.db=db;
    this.json=json;
    this.clientId=trim(clientId);
    this.clientSecret=trim(clientSecret);
    this.redirectUri=trim(redirectUri);
    this.planSharingEnabled=planSharingEnabled;
    this.encryptionKey=tokenKey==null||tokenKey.isBlank()?null:sha256(tokenKey.trim().getBytes(StandardCharsets.UTF_8));
  }

  @PostConstruct
  void init(){
    db.execute("create table if not exists chatgpt_web_connection (username varchar(255) primary key, subject varchar(512) not null, email varchar(512), display_name varchar(512), access_token text, refresh_token text, scopes varchar(2000), expires_at bigint, updated_at timestamp)");
  }

  public record WebStatus(boolean configured,boolean planSharingConfigured,boolean connected,boolean sharingEnabled,String name,String email,String message,String manageUsageUrl){}
  public record Model(String slug,String displayName){}
  public record ChatLine(String role,String text){}

  public WebStatus status(String username){
    Connection c=load(username);
    boolean configured=configured();
    boolean planReady=planReady();
    boolean connected=c!=null;
    boolean sharing=connected&&planReady&&c.scopes().contains("chatgpt.tokens.use.direct")&&c.accessToken()!=null;
    String message;
    if(!configured) message="Hosted ChatGPT sign-in is prepared but requires an OpenAI-issued website OAuth client (oaiapp_*).";
    else if(!planSharingEnabled) message="ChatGPT identity sign-in can be configured, but hosted ChatGPT-plan usage has not been enabled for this website client.";
    else if(encryptionKey==null) message="Hosted plan sharing is enabled but OPENAI_CHATGPT_TOKEN_KEY is not configured; tokens will not be stored.";
    else if(!connected) message="Connect your ChatGPT account to use the hosted Ask ChatGPT workspace.";
    else if(!sharing) message="ChatGPT is connected, but this website client was not granted ChatGPT-plan usage.";
    else message="ChatGPT plan usage is connected for the web portal.";
    return new WebStatus(configured,planReady,connected,sharing,c==null?"":c.name(),c==null?"":c.email(),message,"https://chatgpt.com/settings/usage");
  }

  public URI begin(String username,String sessionId){
    if(!configured()) throw new IllegalStateException("Hosted ChatGPT sign-in requires OPENAI_CHATGPT_CLIENT_ID from OpenAI.");
    purgePending();
    String state=randomUrlSafe(32),nonce=randomUrlSafe(32),verifier=randomUrlSafe(64);
    String challenge=base64Url(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
    pending.put(state,new PendingAuth(username,sessionId,verifier,nonce,Instant.now().plusSeconds(300)));
    Map<String,String> q=new LinkedHashMap<>();
    q.put("client_id",clientId);
    q.put("response_type","code");
    q.put("redirect_uri",redirectUri);
    q.put("scope",planSharingEnabled?PLAN_SCOPES:IDENTITY_SCOPES);
    q.put("state",state);
    q.put("nonce",nonce);
    q.put("code_challenge_method","S256");
    q.put("code_challenge",challenge);
    if(planSharingEnabled)q.put("resource",RESOURCE);
    return URI.create(AUTH+"?"+form(q));
  }

  public void complete(String username,String sessionId,String state,String code,String oauthError) throws Exception {
    PendingAuth tx=state==null?null:pending.remove(state);
    if(tx==null||tx.expiresAt().isBefore(Instant.now()))throw new SecurityException("ChatGPT authorization session expired. Start the connection again.");
    if(!Objects.equals(tx.username(),username)||!Objects.equals(tx.sessionId(),sessionId))throw new SecurityException("ChatGPT authorization did not match this signed-in portal session.");
    if(oauthError!=null&&!oauthError.isBlank())throw new SecurityException("access_denied".equals(oauthError)?"ChatGPT authorization was cancelled or not approved.":"ChatGPT authorization failed: "+oauthError);
    if(code==null||code.isBlank())throw new IllegalStateException("ChatGPT did not return an authorization code.");

    Map<String,String> form=new LinkedHashMap<>();
    form.put("grant_type","authorization_code"); form.put("client_id",clientId); form.put("code",code); form.put("code_verifier",tx.verifier()); form.put("redirect_uri",redirectUri);
    if(planSharingEnabled)form.put("resource",RESOURCE);
    JsonNode token=postForm(TOKEN,form);
    String idToken=text(token,"id_token");
    if(idToken==null)throw new SecurityException("ChatGPT did not return a verifiable identity token.");
    Identity identity=verifyIdToken(idToken,clientId,tx.nonce());
    Set<String> scopes=splitScopes(token.path("scope").asText(""));
    String access=text(token,"access_token"),refresh=text(token,"refresh_token");
    long expiresAt=System.currentTimeMillis()+token.path("expires_in").asLong(3600L)*1000L;

    if(planSharingEnabled&&(!scopes.contains("chatgpt.tokens.use.direct")||access==null)){
      throw new SecurityException("OpenAI signed the account in but did not grant ChatGPT-plan usage to this website client.");
    }
    if(planSharingEnabled&&encryptionKey==null)throw new IllegalStateException("OPENAI_CHATGPT_TOKEN_KEY must be configured before hosted ChatGPT-plan tokens can be stored.");
    save(new Connection(username,identity.subject(),identity.email(),identity.name(),encryptNullable(access),encryptNullable(refresh),scopes,expiresAt));
  }

  public List<Model> models(String username) throws Exception {
    Connection c=ensureAccess(username);
    HttpRequest req=HttpRequest.newBuilder(URI.create(MODELS)).timeout(Duration.ofSeconds(60)).header("Authorization","Bearer "+decrypt(c.accessToken())).header("Accept","application/json").GET().build();
    HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());
    ensureOk(res.statusCode(),res.body());
    JsonNode root=json.readTree(res.body());
    List<Model> models=new ArrayList<>();
    for(JsonNode item:root.path("models")){
      String visibility=item.path("visibility").asText(""); String slug=item.path("slug").asText("");
      if(!slug.isBlank()&&(visibility.isBlank()||"list".equals(visibility)))models.add(new Model(slug,item.path("display_name").asText(slug)));
    }
    return models;
  }

  public String respond(String username,String model,List<ChatLine> history) throws Exception {
    if(model==null||model.isBlank())throw new IllegalArgumentException("Choose a ChatGPT model.");
    Connection c=ensureAccess(username);
    ObjectNode body=json.createObjectNode(); body.put("model",model);
    body.put("instructions","You are ChatGPT inside Electrical Workbench. Answer normally. When discussing electrical code, safety, energized work, protection settings, or field procedures, clearly distinguish general guidance from authoritative requirements and encourage verification against the applicable CEC/OESC, manufacturer instructions, engineering documents, and site procedures.");
    ArrayNode input=body.putArray("input");
    List<ChatLine> safe=history==null?List.of():history.subList(Math.max(0,history.size()-20),history.size());
    for(ChatLine line:safe){if(line==null||line.text()==null||line.text().isBlank())continue;ObjectNode item=input.addObject();item.put("role","assistant".equals(line.role())?"assistant":"user");item.put("content",line.text());}
    body.put("store",false); body.put("stream",true);
    HttpRequest req=HttpRequest.newBuilder(URI.create(RESPONSES)).timeout(Duration.ofMinutes(4)).header("Authorization","Bearer "+decrypt(c.accessToken())).header("Content-Type","application/json").header("Accept","text/event-stream").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
    HttpResponse<Stream<String>> res=http.send(req,HttpResponse.BodyHandlers.ofLines());
    if(res.statusCode()<200||res.statusCode()>299){String error=res.body().reduce("",(a,b)->a+b+"\n");throw new IllegalStateException(openAiError(res.statusCode(),error));}
    StringBuilder out=new StringBuilder(); boolean[] completed={false};
    try(Stream<String> lines=res.body()){
      lines.forEach(line->{
        if(!line.startsWith("data:"))return; String data=line.substring(5).trim(); if(data.isBlank()||"[DONE]".equals(data))return;
        try{
          JsonNode event=json.readTree(data); String type=event.path("type").asText();
          if("response.output_text.delta".equals(type))out.append(event.path("delta").asText(""));
          else if("response.completed".equals(type)){completed[0]=true;if(out.isEmpty())out.append(extractCompletedText(event.path("response")));}
          else if("response.failed".equals(type)){JsonNode err=event.path("response").path("error");throw new StreamFailure(shareErrorMessage(err.path("code").asText(""),err.path("message").asText("")));}
          else if("response.incomplete".equals(type))throw new StreamFailure("ChatGPT ended the response before completion. Please try again.");
          else if("error".equals(type))throw new StreamFailure(event.path("message").asText("ChatGPT returned an error."));
        }catch(StreamFailure f){throw f;}catch(Exception ignored){}
      });
    }catch(StreamFailure f){throw new IllegalStateException(f.getMessage());}
    if(!completed[0])throw new IllegalStateException("ChatGPT stream ended before response.completed.");
    return out.toString();
  }

  public void disconnect(String username){
    Connection c=load(username); if(c==null)return;
    try{if(c.refreshToken()!=null){String refresh=decrypt(c.refreshToken());if(refresh!=null&&!refresh.isBlank())postForm(REVOKE,Map.of("token",refresh,"token_type_hint","refresh_token","client_id",clientId));}}catch(Exception ignored){}
    db.update("delete from chatgpt_web_connection where username=?",username);
  }

  private Connection ensureAccess(String username) throws Exception {
    if(!planReady())throw new IllegalStateException("Hosted ChatGPT-plan usage is not configured for this web portal.");
    Connection c=load(username); if(c==null)throw new IllegalStateException("Connect a ChatGPT account first.");
    if(!c.scopes().contains("chatgpt.tokens.use.direct")||c.accessToken()==null)throw new IllegalStateException("This ChatGPT connection does not include plan usage.");
    if(c.expiresAtMs()>System.currentTimeMillis()+60_000L)return c;
    if(c.refreshToken()==null)throw new IllegalStateException("Your ChatGPT session expired. Reconnect the account.");
    String refresh=decrypt(c.refreshToken());
    Map<String,String> form=new LinkedHashMap<>(); form.put("grant_type","refresh_token");form.put("client_id",clientId);form.put("refresh_token",refresh);form.put("resource",RESOURCE);
    JsonNode token=postForm(TOKEN,form); String access=required(text(token,"access_token"),"ChatGPT token refresh did not return an access token.");
    String nextRefresh=Optional.ofNullable(text(token,"refresh_token")).orElse(refresh); String scopeText=token.path("scope").asText(""); Set<String> scopes=scopeText.isBlank()?c.scopes():splitScopes(scopeText);
    Connection updated=new Connection(c.username(),c.subject(),c.email(),c.name(),encryptNullable(access),encryptNullable(nextRefresh),scopes,System.currentTimeMillis()+token.path("expires_in").asLong(3600L)*1000L); save(updated); return updated;
  }

  private JsonNode postForm(String url,Map<String,String> values) throws Exception {
    HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("Content-Type","application/x-www-form-urlencoded").header("Accept","application/json");
    if(!clientSecret.isBlank())builder.header("Authorization","Basic "+Base64.getEncoder().encodeToString((enc(clientId)+":"+enc(clientSecret)).getBytes(StandardCharsets.UTF_8)));
    HttpResponse<String> res=http.send(builder.POST(HttpRequest.BodyPublishers.ofString(form(values))).build(),HttpResponse.BodyHandlers.ofString()); ensureOk(res.statusCode(),res.body()); return res.body()==null||res.body().isBlank()?json.createObjectNode():json.readTree(res.body());
  }

  private Identity verifyIdToken(String token,String audience,String nonce) throws Exception {
    String[] parts=token.split("\\."); if(parts.length!=3)throw new SecurityException("ChatGPT returned an invalid identity token.");
    JsonNode header=json.readTree(new String(base64UrlDecode(parts[0]),StandardCharsets.UTF_8)); if(!"RS256".equals(header.path("alg").asText()))throw new SecurityException("Unsupported ChatGPT identity signature algorithm."); String kid=header.path("kid").asText();
    HttpResponse<String> res=http.send(HttpRequest.newBuilder(URI.create(JWKS)).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofString());ensureOk(res.statusCode(),res.body());JsonNode key=null;
    for(JsonNode candidate:json.readTree(res.body()).path("keys"))if(kid.equals(candidate.path("kid").asText())&&"RSA".equals(candidate.path("kty").asText())){key=candidate;break;}if(key==null)throw new SecurityException("ChatGPT signing key was not found.");
    PublicKey pk=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(1,base64UrlDecode(key.path("n").asText())),new BigInteger(1,base64UrlDecode(key.path("e").asText()))));Signature sig=Signature.getInstance("SHA256withRSA");sig.initVerify(pk);sig.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));if(!sig.verify(base64UrlDecode(parts[2])))throw new SecurityException("ChatGPT identity signature validation failed.");
    JsonNode claims=json.readTree(new String(base64UrlDecode(parts[1]),StandardCharsets.UTF_8));if(!ISSUER.equals(claims.path("iss").asText()))throw new SecurityException("ChatGPT identity issuer did not match OpenAI.");JsonNode aud=claims.get("aud");boolean ok=aud!=null&&((aud.isTextual()&&audience.equals(aud.asText()))||(aud.isArray()&&contains(aud,audience)));if(!ok)throw new SecurityException("ChatGPT identity token was not issued for this website.");if(!nonce.equals(claims.path("nonce").asText()))throw new SecurityException("ChatGPT identity nonce did not match the sign-in attempt.");long now=System.currentTimeMillis()/1000L;if(claims.path("exp").asLong(0L)<now-5L)throw new SecurityException("ChatGPT identity token has expired.");String subject=claims.path("sub").asText();if(subject.isBlank())throw new SecurityException("ChatGPT identity token did not contain a subject.");return new Identity(subject,claims.path("email").asText(""),claims.path("name").asText(""));
  }

  private void save(Connection c){db.update("delete from chatgpt_web_connection where username=?",c.username());db.update("insert into chatgpt_web_connection(username,subject,email,display_name,access_token,refresh_token,scopes,expires_at,updated_at) values(?,?,?,?,?,?,?,?,?)",c.username(),c.subject(),c.email(),c.name(),c.accessToken(),c.refreshToken(),String.join(" ",c.scopes()),c.expiresAtMs(),java.sql.Timestamp.from(Instant.now()));}
  private Connection load(String username){List<Connection> rows=db.query("select username,subject,email,display_name,access_token,refresh_token,scopes,expires_at from chatgpt_web_connection where username=?",(rs,n)->new Connection(rs.getString(1),rs.getString(2),nullToEmpty(rs.getString(3)),nullToEmpty(rs.getString(4)),rs.getString(5),rs.getString(6),splitScopes(rs.getString(7)),rs.getLong(8)),username);return rows.isEmpty()?null:rows.get(0);}
  private boolean configured(){return !clientId.isBlank()&&!redirectUri.isBlank();}
  private boolean planReady(){return configured()&&planSharingEnabled&&encryptionKey!=null;}
  private void purgePending(){Instant now=Instant.now();pending.entrySet().removeIf(e->e.getValue().expiresAt().isBefore(now));}
  private String encryptNullable(String value){if(value==null||value.isBlank())return null;if(encryptionKey==null)return null;try{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(encryptionKey,"AES"));return Base64.getEncoder().encodeToString(c.getIV())+":"+Base64.getEncoder().encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("Could not protect ChatGPT token",ex);}}
  private String decrypt(String value){if(value==null)return null;if(encryptionKey==null)throw new IllegalStateException("ChatGPT token encryption key is not configured.");try{String[] p=value.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(encryptionKey,"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(p[0])));return new String(c.doFinal(Base64.getDecoder().decode(p[1])),StandardCharsets.UTF_8);}catch(Exception ex){throw new IllegalStateException("Could not unlock ChatGPT token",ex);}}
  private void ensureOk(int code,String body){if(code>=200&&code<=299)return;throw new IllegalStateException(openAiError(code,body));}
  private String openAiError(int code,String body){try{JsonNode err=json.readTree(body==null?"{}":body).path("error");String ec=err.path("code").asText("");String m=err.path("message").asText("");if("subscription_sharing_usage_limit_exceeded".equals(ec)||"subscription_sharing_usage_unavailable".equals(ec))return shareErrorMessage(ec,m);return m.isBlank()?"OpenAI returned HTTP "+code+(ec.isBlank()?".":" ("+ec+")."):m;}catch(Exception ignored){return "OpenAI returned HTTP "+code+".";}}
  private static String shareErrorMessage(String code,String message){return switch(code){case "subscription_sharing_usage_limit_exceeded"->"Your ChatGPT app usage limit has been reached. Open Manage usage to review or change the limit.";case "subscription_sharing_usage_unavailable"->"ChatGPT plan usage is currently unavailable for this account or app connection.";default->message==null||message.isBlank()?"ChatGPT request failed.":message;};}
  private static String extractCompletedText(JsonNode response){StringBuilder t=new StringBuilder();for(JsonNode item:response.path("output"))for(JsonNode part:item.path("content"))if("output_text".equals(part.path("type").asText()))t.append(part.path("text").asText(""));return t.toString();}
  private static boolean contains(JsonNode array,String value){for(JsonNode n:array)if(value.equals(n.asText()))return true;return false;}
  private static String text(JsonNode n,String f){JsonNode v=n.get(f);if(v==null||v.isNull())return null;String s=v.asText();return s==null||s.isBlank()?null:s;}
  private static String required(String value,String message){if(value==null||value.isBlank())throw new IllegalStateException(message);return value;}
  private static Set<String> splitScopes(String value){if(value==null||value.isBlank())return Set.of();return new LinkedHashSet<>(Arrays.asList(value.trim().split("\\s+")));}
  private static String form(Map<String,String> m){StringJoiner j=new StringJoiner("&");m.forEach((k,v)->j.add(enc(k)+"="+enc(v)));return j.toString();}
  private static String enc(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
  private static String randomUrlSafe(int bytes){byte[] b=new byte[bytes];new SecureRandom().nextBytes(b);return base64Url(b);}
  private static String base64Url(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
  private static byte[] base64UrlDecode(String s){return Base64.getUrlDecoder().decode(s);}
  private static byte[] sha256(byte[] in){try{return MessageDigest.getInstance("SHA-256").digest(in);}catch(Exception ex){throw new IllegalStateException(ex);}}
  private static String trim(String s){return s==null?"":s.trim();}
  private static String nullToEmpty(String s){return s==null?"":s;}

  private record PendingAuth(String username,String sessionId,String verifier,String nonce,Instant expiresAt){}
  private record Connection(String username,String subject,String email,String name,String accessToken,String refreshToken,Set<String> scopes,long expiresAtMs){}
  private record Identity(String subject,String email,String name){}
  private static final class StreamFailure extends RuntimeException{StreamFailure(String m){super(m);}}
}
