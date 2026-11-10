# Cloud communication threat model — Industrial Control Studio

**Status: architectural proposal, NOT implemented or penetration-tested.**

## Trust zones
1. OT network: Kepware and PLCs are isolated; no inbound public access; safety logic stays inside certified safety systems.
2. Edge PC/industrial DMZ: OPC UA reader connects to Kepware with SignAndEncrypt and mutually approved application certificates. A separate outbound client publishes **allowlisted telemetry** only.
3. Cloud control plane: portal accounts, configuration drafts, revisions, signed releases, and audit records. Enforce MFA/SSO, tenant isolation, and server-side authorization.
4. JavaFX PCs: authenticated subscribers to cloud/edge data, no direct access to PLC addresses, strong session expiration and device identity.

## Remote data path
- Edge initiates outbound TLS 1.3 with **mutual TLS** to cloud broker. Pin organization CA/trust anchors and manage renewals/revocations.
- Cloud-to-JavaFX uses authenticated HTTPS/WebSocket over TLS 1.3, identity provider MFA, short-lived access credentials and authorization per site/tag.
- Do not forward OPC UA raw channels into the public internet or expose TCP 49320 publicly.
- OT/IT firewalls and a DMZ constrain east-west movement; harden gateway host, log and monitor all access.
- Use server-sent timestamps, OPC UA status/quality, sequence numbers and server-enforced timeouts. Stale data must be visible and cannot count as healthy.
- Cloud outage: local operation continues. Edge buffers bounded non-sensitive readings, drops obsolete telemetry, reconnects with jitter. No unbounded memory queues.

## Security around custom Java
Arbitrary Java can bypass simple allowlists if it shares the portal or edge runtime. It must remain **non-executable as a draft** in the first version. For a future runtime: signed jar, distinct low-privilege OS/container/VM isolation, no access to raw PLC credentials, no unrestricted file/system/network permissions, independent resource limits, and a typed rules SDK. Source review, automated tests and an approval workflow must precede activation. Do not use the deprecated Java SecurityManager as a sandbox.

## Remote actuation (future, disabled by default)
Use a physically authorized, separate plant-local command gateway, with exact tag-level allowlists, limits, equipment-mode/state preconditions, authenticated actor and device, step-up MFA, short-lived one-time nonce, on-prem acknowledgement, and event logging. Require separate operator approval for elevated-risk actions. Never queue/replay commands after loss of connection. Do not use this pathway for E-stops, interlocks, motion safety or protective functions. Verify process and site safety engineering requirements before enabling anything.

## Secure update distribution
Use a separate release channel and app IDs from Electrical Workbench. Publish immutable manifests; verify artifact signature, release provenance, hash and product identity before executing installers. Stage on a test/standby environment, support rollback and require site approval for changes affecting OT operations. Do not hot swap Java on a PLC runtime.

## Standards and guidance (subject to site requirements)
- NIST SP 800-82 Rev.3, Guide to Operational Technology Security: https://csrc.nist.gov/pubs/sp/800/82/r3/final
- OPC Foundation, Recommendations for Using OPC UA in a Secure Way: https://opcfoundation.org/wp-content/uploads/2017/11/OPC-UA-Security-Advise-EN.pdf
- CISA Primary Mitigations to Reduce Cyber Threats to OT (2025): https://www.cisa.gov/sites/default/files/2025-05/fact-sheet-primary-mitigations-to-reduce-cyber-threats-to-operational-technology-508c.pdf
- IEC 62443-series and site-specific controls should inform design review and acceptance testing.

## Prototype security limitations
- The portal prototype is **loopback-only by default**; browser page is public but all data APIs require Basic authentication and admin role for edits. There is no session/MFA/CSRF model for network deployment.
- JavaFX client currently permits Basic authentication with HTTPS remote endpoint, but the prototype is **not an approved remote gateway** and contains no HMI or custom logic commands.
- The simulated values do not model valid quality, time synchronization or alarm acknowledgement.
- An update **check** exists but no installer/signed update delivery.
- Cloud connection, certificates, OPC UA subscription and industrial command workflow are NOT implemented.
