# Cloud Kepware + JavaFX HMI demonstration

This is a **deployment guide, not a claim of a running cloud server**. The GitHub project contains the optional OPC UA bridge but does not install proprietary Kepware, provision Windows VMs, buy licenses, or issue certificates.

## Demo deployment topology

```text
PC A JavaFX ──HTTPS/443──┐
PC B JavaFX ──HTTPS/443──┼── trusted reverse proxy ── Spring Boot portal + HMI API
Web browser ─HTTPS/443───┘                              |
                                                    read-only
                                             OPC UA SignAndEncrypt
                                                       |
                                       Kepware KEPServerEX, Windows VM
                                                       |
                                     Kepware Simulator Driver (no PLC)
```

For a simple proof of concept, the portal and Kepware can run on the same Windows cloud VM, with the OPC UA endpoint bound only to loopback. The app's HTTPS listener/reverse proxy is the **only public inbound service**. Port 49320 and any Kepware configuration interfaces must not be publicly reachable. Use Windows firewall, endpoint protection, backups, logging and restricted access to the VM.

Before exposing anything outside a lab/VPN, replace the prototype's Basic-auth-only administrative UI with MFA-backed sessions, CSRF protection, hardened tenant/device authorization, short-lived tokens, rate limits, certificate lifecycle management and a formal security review. **Do not deploy this prototype directly to the open internet.**

## Run a showcase without any Kepware or PLC
1. Install Java 21/Maven on the demo host.
2. Configure distinct long `STUDIO_ADMIN_PASSWORD` and `STUDIO_VIEWER_PASSWORD` environment variables.
3. Leave `STUDIO_SOURCE_MODE=SIMULATION`.
4. Start the portal: `cd industrial-studio/portal; mvn spring-boot:run`. Visit `http://127.0.0.1:8090/`.
5. Sign into the portal as an administrator, edit OPC tag labels and screen widgets, and register a workstation. Copy its one-time enrollment secret.
6. On the demo PC, run `cd industrial-studio/desktop; mvn javafx:run`, enter the approved portal URL, viewer credentials, workstation ID and enrollment secret.
7. Assign a different layout to each registered PC. The client downloads its assigned screen on the next polling cycle. Live values are synthetic and visibly labeled.

## Use a real Kepware simulator on a private Windows cloud VM
1. Obtain an authorized compatible KEPServerEX installation and license/evaluation entitlement from PTC. Confirm hosting and trial-license conditions before creating a VM.
2. Install KEPServerEX with the Simulator Driver, create `Channel1.Device1` and tags such as `PumpSpeed`, `TankLevel` and `MotorCurrent`. Set some tags to change to demonstrate graphs/gauges.
3. Verify the exact OPC UA NodeIds using Kepware's OPC Quick Client or an authorized UA client; the example `ns=2;s=...` values are **placeholders** and are not guaranteed to match your server.
4. Configure Kepware OPC UA with **Basic256Sha256 / SignAndEncrypt**. Disable anonymous browsing outside the isolated demo if site policy requires user accounts; this starter currently uses an anonymous OPC UA Session over a certificate-authenticated secure channel.
5. Generate a unique client X.509 application certificate and private key in a password-protected PKCS#12 file. Import/trust the application certificate in Kepware's Trusted Clients. Export Kepware's actual server certificate to a separate `.der` or `.cer` file. Confirm endpoint hostnames match its SAN and that the selected endpoint URL is advertised exactly by Kepware.
6. Save the files in a protected directory outside version control. Give the portal service identity only the required read permissions; do **not** upload private keys or passwords to the web portal.
7. Supply these environment variables to the Spring Boot server process (not the JavaFX clients):

   ```powershell
   $env:STUDIO_SOURCE_MODE="KEPWARE"
   $env:STUDIO_KEPWARE_ENDPOINT="opc.tcp://127.0.0.1:49320"
   $env:STUDIO_KEPWARE_CLIENT_PKCS12="C:\\industrial-studio\\secrets\\client.p12"
   $env:STUDIO_KEPWARE_CLIENT_PKCS12_PASSWORD="<set from secret manager>"
   $env:STUDIO_KEPWARE_SERVER_CERT="C:\\industrial-studio\\secrets\\kepware-server.der"
   ```

8. Restrict the portal's network access to Kepware's private endpoint using firewall rules. The URL entered in the browser tag editor is *display/configuration metadata*, not an override of the pinned, server-side `STUDIO_KEPWARE_ENDPOINT`.
9. Restart the portal and inspect `/api/studio/status`. The OPC UA read worker polls the configured tags approximately every 1.5 seconds and serves a cached snapshot to all enrolled HMI clients; a connection problem shows **DISCONNECTED** and no valid readings.
10. Verify each enrolled JavaFX PC gets only the tags referenced by its assigned screen. Check behavior on a forced Kepware disconnection, bad certificate, and revoked/disabled PC.

The bridge is **read-only**: no OPC UA write or method-call API is exposed. It does not implement persistent, signed releases or a true remote cloud provisioning workflow yet.

## Factory or mine conversion
Run Kepware and middleware on protected OT / industrial DMZ servers, not an internet-addressable VM. Keep PLC programming, safety and interlocks on-site. Use an approved outbound gateway or private tunnel to the central web portal for **configuration** and allowed telemetry. Put OT access behind the site's authorization, change management and IEC 62443/NIST OT security assessments. Never route real PLC command traffic through the current showcase.

## Before production
- Implement better device credential enrollment, rotation and revocation (the demo has one-time secrets and enable/disable).
- Replace username/password Basic authentication with MFA for admin and short-lived device identities; use TLS 1.3 and mTLS where applicable.
- Run cloud-only penetration tests, network partition tests, and OPC interoperability tests with licensed Kepware.
- Add historical trends, data provenance, OPC status codes, source timestamps, alarms with acknowledgments, and subscription-based batching for high tag counts.
- Add signed installer/update distribution independent of screen deployments; review cloud provider's Windows and license terms.

Sources:
- PTC KEPServerEX Simulator Driver documentation: https://support.ptc.com/help/kepware/drivers/en/kepware/drivers/SIMULATOR/HelpContents.html
- PTC OPC UA Configuration tutorial: https://support.ptc.com/help/kepware/features/en/kepware/features/OPCUACONFIGMNGR/quick-start-tutorial.html
- Eclipse Milo client security documentation: https://github.com/eclipse-milo/milo/wiki/Client-Security
- NIST SP 800-82 Rev. 3: https://csrc.nist.gov/pubs/sp/800/82/r3/final
