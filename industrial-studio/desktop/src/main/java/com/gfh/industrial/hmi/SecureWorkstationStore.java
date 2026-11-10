package com.gfh.industrial.hmi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Windows-only DPAPI CurrentUser storage. No plaintext workstation token on disk,
 * in process arguments, or in setup ZIPs. Mac/Linux sessions remain memory-only.
 * Production OT still requires managed device certificates and audited enrollment.
 */
public final class SecureWorkstationStore {
    private static final ObjectMapper JSON=new ObjectMapper();

    public record Session(String serverUrl,String workstationId,String workstationName,
                          String screenId,String deviceSecret) {}

    private SecureWorkstationStore() {}

    public static Path path() {
        return WorkstationSetup.configPath().resolveSibling("workstation.auth.json");
    }

    public static boolean supported() {
        return System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    public static void save(Session session) throws IOException {
        save(session,path());
    }

    static void save(Session session,Path file) throws IOException {
        if(!supported())throw new IOException("Secure automatic sign-in currently requires Windows DPAPI");
        PairingCode.validServer(session.serverUrl());
        if(!session.workstationId().matches("[A-Za-z][A-Za-z0-9_-]{1,47}") ||
           !session.deviceSecret().matches("[A-Za-z0-9_-]{43}"))
            throw new IOException("Invalid device identity");
        String encrypted=dpapi(true,Base64.getEncoder().encodeToString(
            session.deviceSecret().getBytes(StandardCharsets.US_ASCII)));
        byte[] json=JSON.writeValueAsBytes(new Stored(1,session.serverUrl(),session.workstationId(),
            session.workstationName(),session.screenId(),encrypted));
        if(json.length>8192)throw new IOException("Device profile too large");
        Files.createDirectories(file.getParent());
        Path tmp=Files.createTempFile(file.getParent(),"workstation-", ".tmp");
        try {
            Files.write(tmp,json,StandardOpenOption.TRUNCATE_EXISTING);
            try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {Files.deleteIfExists(tmp);}
    }

    private record Stored(int version,String serverUrl,String workstationId,String workstationName,
                          String screenId,String encryptedSecret) {}

    public static Session load() throws IOException {return load(path());}

    static Session load(Path file) throws IOException {
        if(!supported() || !Files.isRegularFile(file))return null;
        if(Files.size(file)>8192)throw new IOException("Saved PC enrollment exceeds allowed size");
        JsonNode v=JSON.readTree(Files.readAllBytes(file));
        if(v==null || v.path("version").asInt()!=1)throw new IOException("Unsupported saved workstation profile");
        String server=PairingCode.validServer(v.path("serverUrl").asText());
        String id=v.path("workstationId").asText(""), name=v.path("workstationName").asText(""),
               screen=v.path("screenId").asText(""),cipher=v.path("encryptedSecret").asText("");
        if(!id.matches("[A-Za-z][A-Za-z0-9_-]{1,47}") ||
           cipher.isBlank() || cipher.length()>2048 ||
           name.length()>100 || screen.length()>48)
            throw new IOException("Saved workstation profile is invalid");
        byte[] secretBytes=Base64.getDecoder().decode(dpapi(false,cipher));
        String secret=new String(secretBytes,StandardCharsets.US_ASCII);
        if(!secret.matches("[A-Za-z0-9_-]{43}"))throw new IOException("Saved workstation token is invalid");
        return new Session(server,id,name,screen,secret);
    }

    public static void forget() throws IOException {Files.deleteIfExists(path());}

    private static String dpapi(boolean encrypt,String input) throws IOException {
        // The secret is supplied via STDIN. No long-lived credential is passed in process arguments.
        String code=encrypt ?
          "Add-Type -AssemblyName System.Security; $s=[Console]::In.ReadToEnd().Trim(); "+
          "$b=[Convert]::FromBase64String($s); "+
          "$v=[Security.Cryptography.ProtectedData]::Protect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser); "+
          "[Console]::Out.Write([Convert]::ToBase64String($v))" :
          "Add-Type -AssemblyName System.Security; $s=[Console]::In.ReadToEnd().Trim(); "+
          "$b=[Convert]::FromBase64String($s); "+
          "$v=[Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser); "+
          "[Console]::Out.Write([Convert]::ToBase64String($v))";
        Process process=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",code).start();
        try(var stdin=process.getOutputStream()){
            stdin.write(input.getBytes(StandardCharsets.US_ASCII));
        }
        byte[] output;
        try(var stdout=process.getInputStream()){output=stdout.readNBytes(8192);}
        try{
            if(!process.waitFor(10,TimeUnit.SECONDS)){
                process.destroyForcibly();
                throw new IOException("Windows secure storage timed out");
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Interrupted while saving PC registration",e);}
        if(process.exitValue()!=0 || output.length==0)throw new IOException("Windows credential protection failed");
        String result=new String(output,StandardCharsets.US_ASCII).trim();
        try{Base64.getDecoder().decode(result);}catch(IllegalArgumentException e){throw new IOException("Invalid secure storage result",e);}
        return result;
    }
}
