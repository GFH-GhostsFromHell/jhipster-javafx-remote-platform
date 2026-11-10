# Industrial Control Studio — safe starter

Separate Java 21 / Spring Boot / JavaFX application modelled on the existing Electrical Workbench portal and desktop structure. It is deliberately isolated under `industrial-studio/`; it does not alter the production electrician app.

## Starter capabilities
- Web portal to configure endpoint metadata, OPC UA node mappings, display names, engineering units and alarm limits.
- Web-based HMI screen designer (Text, Value, Gauge and Status widgets) with revisioned screens and per-workstation assignments.
- Independently enrolled JavaFX PCs download their assigned screen definitions and scoped simulator readings using a workstation secret plus viewer login.
- Revisioned, persistent configuration using optimistic concurrency (H2 local or PostgreSQL).
- Browser-editable **Java source drafts** for a future isolated rules worker. Drafts are stored but **never compiled or executed** by the portal.
- JavaFX desktop HMI reads the service, automatically refreshes values, exposes connection/quality/staleness, and keeps credentials in memory.
- **SIMULATION** (default) or optional **KEPWARE** (private Windows VM with trusted certificates) source mode. The optional connector is read-only; no PLC writes are implemented or exposed. A real Kepware server, VM and certificate enrollment are NOT provisioned.
- Update manifest API and desktop update-check hook; no release/installer exists until a dedicated workflow ships one.
- All services default to loopback-only; never expose the starter directly to the public internet.

## Architecture

```text
Browser configurator -> Spring Boot config API + database
                                  |
                            revisioned config
                                  |
          (future, plant-local) OPC UA edge adapter -> Kepware -> PLCs
                                  |
JavaFX desktop HMI -> authenticated /api/studio/live (currently simulation)
```

Run the **portal** on a designated test/edge PC (not Render): `cd industrial-studio/portal && mvn spring-boot:run`. Set `STUDIO_ADMIN_PASSWORD` and `STUDIO_VIEWER_PASSWORD` to different high-entropy passwords of 12+ characters. Visit http://127.0.0.1:8090/ and sign in using the portal's in-page form. Its REST API uses HTTP Basic over the local machine only. For remote use, put it behind TLS, network isolation/VPN, formal RBAC, and a CSRF-resistant authentication approach.

Run the **desktop** with `cd industrial-studio/desktop && mvn javafx:run`. Provide the portal URL, viewer credentials, registered workstation ID and one-time enrollment secret. In the portal, open **HMI screens & PCs**, create screen layouts and register each PC. The JavaFX app downloads the layout assigned to that specific PC; editing the layout does not require rebuilding its installer. Current snapshots are **simulated**. See [per-PC HMI setup](docs/PER_WORKSTATION_HMI.md).

## Important safety boundary
This is a non-production, read-only prototype. Do **not** use the sample data for operation, alarms, trips, interlocks, personnel safety, or any live control decision. Never use user-supplied Java in the portal JVM: a Java `SecurityManager` is not an acceptable isolation mechanism. Before commissioning, create a separate low-privilege, resource-limited and network-denied rules-worker process, signed deployment packages, two-person approvals, event audit trails, alarm validation, and a safety review. PLC writes must require explicit allowlisted points, limits, authorization, confirmation, quality validation, and a fail-safe edge policy. Safety logic remains in the PLC/safety system.

For a real Kepware link use Eclipse Milo 1.1.x or a newer stable client, with trusted certificates and Sign & Encrypt, site-approved user credentials, monitored subscriptions, and conservative reconnect/stale-data behavior. No insecure fallback to SecurityPolicy.None in production. Plant-to-cloud synchronization is outbound for **configuration only**, not cloud-to-PLC command delivery.

## Delivery status
- Phase 1: simulator, configuration API, source drafts, JavaFX HMI, per-PC read-only screen assignment and update manifest: **prototype implemented in this branch; CI and industrial security validation required**.
- Phase 2: optional read-only OPC UA read worker using Eclipse Milo, with configured trust/PKCS12 certificate and synthetic-data fail-closed behavior; **not yet validated against a running Kepware instance**. Tag browser, production credential/certificate manager, alarm historian, and subscriptions are still pending.
- Phase 3: isolated signed Java rules runtime and simulated test harness.
- Phase 4: reviewed and explicitly authorized PLC writes, field acceptance tests, staged updates, MSI installer, and signed releases.

This is a new product, not a rebranding or direct deployment of Electrical Workbench. Existing sign-in/sync/updater concepts are reused as patterns; accounts, releases and databases are separate until intentionally integrated.

## Cloud demo deployment
See [the cloud Kepware showcase guide](docs/CLOUD_KEPWARE_SHOWCASE.md). Windows VM / Kepware installation and licensing are separate prerequisites; this GitHub branch contains no running cloud Kepware service.
