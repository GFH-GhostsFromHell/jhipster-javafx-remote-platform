package com.gfh.industrial.hmi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Validates and imports a settings-only PC package. Never extracts or runs archive content. */
public final class WorkstationSetup {
    private static final int MAX_JSON_BYTES=16384;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Set<String> FIELDS=Set.of("format","serverUrl","workstationId",
        "workstationName","viewerUsername","assignedScreen","generatedAt");

    public record Settings(String serverUrl,String workstationId,String workstationName,
                           String viewerUsername,String assignedScreen) {}

    private WorkstationSetup() {}

    static Settings parse(byte[] bytes) throws IOException {
        if(bytes==null || bytes.length==0 || bytes.length>MAX_JSON_BYTES) throw new IOException("Invalid setup JSON size");
        JsonNode obj=JSON.readTree(bytes);
        if(obj==null || !obj.isObject())throw new IOException("Expected a JSON workstation profile");
        var names=obj.fieldNames();
        Set<String> present=new HashSet<>();
        while(names.hasNext()){
            String name=names.next();
            if(!FIELDS.contains(name))throw new IOException("Unexpected field in PC setup: "+name);
            present.add(name);
        }
        if(!"industrial-studio-pc-v1".equals(obj.path("format").asText()))
            throw new IOException("Unsupported setup package format");
        String endpoint=obj.path("serverUrl").asText(""),
            id=obj.path("workstationId").asText(""),
            name=obj.path("workstationName").asText(""),
            viewer=obj.path("viewerUsername").asText(""),
            screen=obj.path("assignedScreen").asText("");
        if(!id.matches("[A-Za-z][A-Za-z0-9_-]{1,47}") ||
           !screen.matches("[A-Za-z][A-Za-z0-9_-]{1,47}") ||
           viewer.isBlank() || viewer.length()>100 ||
           name.isBlank() || name.length()>100)throw new IOException("Invalid PC assignment information");
        try{
            URI uri=URI.create(endpoint);
            String host=uri.getHost();
            if(host==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null ||
               uri.getPath()!=null && !uri.getPath().isBlank() && !uri.getPath().equals("/") ||
               uri.getPort()>65535 || uri.getPort()==0 ||
               !(uri.getScheme().equals("https") ||
                 uri.getScheme().equals("http") && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")))) {
                throw new IOException("Remote server must use HTTPS without embedded credentials");
            }
            endpoint=uri.toString().replaceAll("/+$","");
        }catch(IllegalArgumentException e){throw new IOException("Invalid middleware URL",e);}
        return new Settings(endpoint,id,name,viewer,screen);
    }

    public static Path configPath(){
        String local=System.getenv("LOCALAPPDATA");
        if(local!=null && !local.isBlank())return Path.of(local,"IndustrialControlStudio","workstation.json");
        return Path.of(System.getProperty("user.home"),".config","IndustrialControlStudio","workstation.json");
    }

    public static Settings load() throws IOException {
        Path path=configPath();
        if(!Files.isRegularFile(path))return null;
        long size=Files.size(path);
        if(size<1||size>MAX_JSON_BYTES)throw new IOException("Stored PC profile exceeds limit");
        return parse(Files.readAllBytes(path));
    }

    public static Settings importZip(Path zipPath,Path destination) throws IOException {
        if(!Files.isRegularFile(zipPath))throw new IOException("Setup ZIP not found");
        try(ZipFile zip=new ZipFile(zipPath.toFile())){
            ZipEntry item=zip.getEntry("workstation.json");
            if(item==null || item.isDirectory() || item.getSize()>MAX_JSON_BYTES)
                throw new IOException("workstation.json missing or too large");
            byte[] payload;
            try(InputStream in=zip.getInputStream(item)){payload=in.readNBytes(MAX_JSON_BYTES+1);}
            Settings settings=parse(payload);
            Files.createDirectories(destination.getParent());
            Files.write(destination,payload,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            return settings;
        }
    }
}
