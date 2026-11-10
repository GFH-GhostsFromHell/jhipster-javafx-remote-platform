package com.gfh.industrial.hmi;

import java.net.URI;

/** A pasted code identifies an HTTPS portal, one registered workstation, and a random one-use secret. */
public record PairingCode(String serverUrl,String workstationId,String pairingSecret) {
    public static PairingCode parse(String raw) {
        if(raw==null || raw.length()>1024)throw new IllegalArgumentException("Setup code is missing or too long");
        String[] fields=raw.trim().split("\\|",-1);
        if(fields.length!=4 || !fields[0].equals("ICS1")) {
            throw new IllegalArgumentException("This is not an Industrial Control Studio setup code. Copy the full code from the portal.");
        }
        String url=validServer(fields[1]);
        if(!fields[2].matches("[A-Za-z][A-Za-z0-9_-]{1,47}"))
            throw new IllegalArgumentException("Setup code contains an invalid PC ID");
        if(!fields[3].matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("Setup code is incomplete. Copy the entire code from the portal.");
        return new PairingCode(url,fields[2],fields[3]);
    }

    public static String validServer(String raw) {
        if(raw==null || raw.isBlank() || raw.length()>512)throw new IllegalArgumentException("Enter a server address");
        try {
            URI uri=URI.create(raw.trim());
            String host=uri.getHost();
            if(host==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null ||
               (uri.getPath()!=null && !uri.getPath().isBlank() && !uri.getPath().equals("/")) ||
               uri.getPort()>65535 || uri.getPort()==0 ||
               !(uri.getScheme().equals("https") ||
                  (uri.getScheme().equals("http") &&
                     (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1"))))) {
                 throw new IllegalArgumentException("Use HTTPS, or HTTP only on localhost");
            }
            return uri.toString().replaceAll("/+$","");
        } catch (IllegalArgumentException ex){
            throw new IllegalArgumentException("Setup code contains an invalid portal address. Use HTTPS for a remote portal.",ex);
        }
    }
}
