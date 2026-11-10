package com.gfh.industrial.hmi;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

/** Validation-only signed-release catalogue for the read-only prototype. */
final class DesktopSoftwareUpdates {
    private static final String TRUSTED_BASE =
        "https://github.com/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases/download/";
    record Release(String version, URI url, String sha256, String publisher) {}

    static String installedVersion() {
        try(InputStream file=DesktopSoftwareUpdates.class.getResourceAsStream(
            "/industrial-hmi-build.properties")) {
            if(file==null)return "0.1.0";
            Properties props=new Properties();
            props.load(file);
            String v=props.getProperty("hmi.version","0.1.0").trim();
            return validVersion(v) ? v : "0.1.0";
        }catch(Exception ignored){return "0.1.0";}
    }

    static boolean validVersion(String version){
        return version!=null && version.matches("[0-9]{1,5}\\.[0-9]{1,5}\\.[0-9]{1,8}");
    }

    static boolean newerThan(String published, String installed) {
        if(!validVersion(published)||!validVersion(installed))return false;
        String[] a=published.split("\\."), b=installed.split("\\.");
        for(int i=0;i<3;i++) {
            long x=Long.parseLong(a[i]), y=Long.parseLong(b[i]);
            if(x!=y)return x>y;
        }
        return false;
    }

    static Release safeRelease(JsonNode manifest) {
        if(manifest==null || !manifest.path("released").asBoolean(false) ||
           !manifest.path("signed").asBoolean(false))return null;
        String version=manifest.path("version").asText("");
        String uri=manifest.path("downloadUrl").asText("");
        String hash=manifest.path("sha256").asText("");
        String publisher=manifest.path("publisher").asText("");
        if(!validVersion(version) || !hash.matches("[a-fA-F0-9]{64}") ||
           publisher.isBlank() || publisher.length()>120 ||
           !uri.startsWith(TRUSTED_BASE) || uri.length()>500)return null;
        try {
            URI url=URI.create(uri);
            if(!"https".equals(url.getScheme()) || !"github.com".equals(url.getHost()) ||
               url.getPort()!=-1 || url.getUserInfo()!=null || url.getRawQuery()!=null ||
               url.getFragment()!=null || !url.getPath().endsWith(".msi") ||
               url.getPath().contains("..") || url.getRawPath().contains("%"))return null;
            return new Release(version,url,hash.toLowerCase(java.util.Locale.ROOT),publisher);
        }catch(IllegalArgumentException ignored){return null;}
    }
}
