package com.gfh.industrial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Per-PC bootstrap ZIP: contains only inert client settings and human-readable install guidance.
 * NO operator password, OPC UA credentials, long-lived device token, or compiled Java/PLC logic.
 * The standard Windows MSI is built and distributed separately.
 */
@RestController
@RequestMapping("/api/studio/workstations")
public final class WorkstationSetupPackageResource {
    private final ScreenApi screens;
    private final ObjectMapper json;
    private final String baseUrl;
    private final String viewerUser;

    public WorkstationSetupPackageResource(ScreenApi screens,ObjectMapper json,
        @Value("${studio.client-base-url:http://127.0.0.1:8090}") String baseUrl,
        @Value("${studio.viewer-user:studio-viewer}") String viewerUser) {
        this.screens=screens;
        this.json=json;
        this.baseUrl=validateBaseUrl(baseUrl);
        this.viewerUser=viewerUser;
    }

    static String validateBaseUrl(String raw) {
        if(raw==null) throw new IllegalArgumentException("STUDIO_CLIENT_BASE_URL is required");
        try {
            URI uri=URI.create(raw.trim());
            String host=uri.getHost();
            if(host==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null ||
               uri.getPath()!=null && !uri.getPath().isBlank() && !uri.getPath().equals("/") ||
               uri.getPort()>65535 || uri.getPort()==0 ||
               !(uri.getScheme().equals("https") ||
                 uri.getScheme().equals("http") && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")))) {
                throw new IllegalArgumentException("Client URL must be HTTPS, or local HTTP on localhost/127.0.0.1");
            }
            return uri.toString().replaceAll("/+$","");
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid STUDIO_CLIENT_BASE_URL: use HTTPS, or localhost HTTP, without a path",e);
        }
    }

    static byte[] buildZip(ObjectMapper json, Map<String,Object> profile) throws IOException {
        byte[] config=json.writerWithDefaultPrettyPrinter().writeValueAsBytes(profile);
        String readme="""
            INDUSTRIAL CONTROL STUDIO — PER-PC SETUP (PROTOTYPE)

            This package configures ONE JavaFX HMI PC. It contains no executable
            installer, passwords or access tokens. Do not use for production OT.

            1) Install the standard IndustrialControlStudioHMI Windows MSI
               obtained separately from the authorized project release/artifact.
               Use only your organization's approved and signed build.
            2) Copy workstation.json into:
                 %LOCALAPPDATA%\\IndustrialControlStudio\\workstation.json
               OR run Install-Industrial-HMI.ps1 (see your organization's policy).
               OR open the JavaFX app and select "Import PC setup ZIP".
            3) Start the JavaFX app. Server URL, workstation ID and viewer
               username will already be selected.
            4) Enter the VIEWER password and workstation enrollment secret
               issued separately by an administrator. Do not include them
               in this setup package or share them by email.
            5) The server assigns the screen; workstation.json cannot
               grant access to another screen or another PC.

            For PCs outside the middleware machine, the server administrator
            must set STUDIO_CLIENT_BASE_URL to a trusted HTTPS gateway.
            A localhost URL works only on the same PC as the portal.

            The ZIP is not code-signed. Production releases require signed MSI
            distribution, device identities, TLS/mTLS, MFA and OT security review.
            """;
        String ps="""
            # Industrial Control Studio - settings-only PC setup.
            # Does NOT download software, change execution policy or embed credentials.
            $ErrorActionPreference = 'Stop'
            $source = Join-Path $PSScriptRoot 'workstation.json'
            if (-not (Test-Path -LiteralPath $source)) { throw 'workstation.json missing' }
            $settings = Get-Content -LiteralPath $source -Raw | ConvertFrom-Json
            if ($settings.format -ne 'industrial-studio-pc-v1') { throw 'Invalid setup format' }
            if ($settings.workstationId -notmatch '^[A-Za-z][A-Za-z0-9_-]{1,47}$') { throw 'Invalid workstation ID' }
            $url = [Uri]$settings.serverUrl
            $local = $url.Scheme -eq 'http' -and $url.Host -in @('localhost','127.0.0.1')
            if (($url.Scheme -ne 'https') -and (-not $local)) { throw 'HTTPS required for remote portal' }
            $destination = Join-Path $env:LOCALAPPDATA 'IndustrialControlStudio'
            New-Item -ItemType Directory -Path $destination -Force | Out-Null
            Copy-Item -LiteralPath $source -Destination (Join-Path $destination 'workstation.json') -Force
            Write-Host ('Saved HMI profile for workstation: ' + $settings.workstationId)
            Write-Host 'Install the separately obtained authorized Windows MSI, then launch Industrial Control Studio.'
            Write-Host 'Enter your viewer password and separately issued workstation enrollment secret.'
            """;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out,StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("workstation.json"));
            zip.write(config);zip.closeEntry();
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(readme.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("Install-Industrial-HMI.ps1"));
            zip.write(ps.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        }
    }

    @GetMapping("/{id}/setup-package")
    public ResponseEntity<byte[]> download(@PathVariable String id) throws IOException {
        if(id==null || !id.matches("[A-Za-z][A-Za-z0-9_-]{1,47}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid workstation ID");
        }
        ScreenApi.Workstation device=screens.workstations().stream()
            .filter(w->w.id().equals(id))
            .findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Workstation not found"));
        if(!device.enabled())throw new ResponseStatusException(HttpStatus.CONFLICT,"Workstation is disabled");
        // Assigned screen is informative only. The real assignment is always resolved by server authorization.
        Map<String,Object> profile=Map.of("format","industrial-studio-pc-v1",
            "serverUrl",baseUrl,"workstationId",device.id(),
            "workstationName",device.name(),"viewerUsername",viewerUser,
            "assignedScreen",device.screenId(),"generatedAt",Instant.now().toString());
        byte[] body=buildZip(json,profile);
        HttpHeaders headers=new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("IndustrialHMI-"+id+"-Setup.zip").build());
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options","nosniff");
        return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType("application/zip")).body(body);
    }
}
