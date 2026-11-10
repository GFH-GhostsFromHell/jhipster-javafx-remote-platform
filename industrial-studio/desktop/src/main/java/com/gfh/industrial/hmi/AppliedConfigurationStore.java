package com.gfh.industrial.hmi;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Only inert revision metadata. No device token or HMI executable content is persisted.
 * A restart checks the server's desired revision against this last accepted fingerprint;
 * it never silently approves an unseen remote screen or tag configuration.
 */
public final class AppliedConfigurationStore {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final int MAX_SIZE=2048;

    public record Applied(int version,String serverUrl,String workstationId,String fingerprint) {}
    private AppliedConfigurationStore() {}

    public static Path path() {
        return WorkstationSetup.configPath().resolveSibling("applied-configuration.json");
    }

    public static String load(String url,String id) throws IOException {
        return load(path(),url,id);
    }

    static String load(Path file,String url,String id) throws IOException {
        if(!Files.isRegularFile(file))return "";
        if(Files.size(file)<1 || Files.size(file)>MAX_SIZE)throw new IOException("Saved update metadata is invalid");
        Applied applied=JSON.readValue(Files.readAllBytes(file),Applied.class);
        if(applied.version()!=1 || applied.fingerprint()==null ||
            !applied.fingerprint().matches("[A-Za-z0-9_:-]{1,200}"))
            throw new IOException("Saved update revision is invalid");
        // Ignore a profile belonging to a different server / PC. No shared revision state.
        return url.equals(applied.serverUrl()) && id.equals(applied.workstationId())
            ?applied.fingerprint():"";
    }

    public static void save(String url,String id,String fingerprint) throws IOException {
        save(path(),url,id,fingerprint);
    }

    static void save(Path file,String url,String id,String fingerprint) throws IOException {
        PairingCode.validServer(url);
        if(id==null || !id.matches("[A-Za-z][A-Za-z0-9_-]{1,47}") ||
            fingerprint==null || !fingerprint.matches("[A-Za-z0-9_:-]{1,200}"))
            throw new IOException("Invalid applied configuration revision");
        byte[] data=JSON.writeValueAsBytes(new Applied(1,url,id,fingerprint));
        Files.createDirectories(file.getParent());
        Path tmp=Files.createTempFile(file.getParent(),"applied-",".tmp");
        try {
            Files.write(tmp,data,StandardOpenOption.TRUNCATE_EXISTING);
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {Files.deleteIfExists(tmp);}
    }

    public static void forget() throws IOException {
        Files.deleteIfExists(path());
    }
}
