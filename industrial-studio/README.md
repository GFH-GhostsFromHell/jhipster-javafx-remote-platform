# Industrial Control Studio — safe starter

Separate Java 21 / Spring Boot / JavaFX application modelled on the existing Electrical Workbench portal and desktop structure. It is deliberately isolated under `industrial-studio/`; it does not alter the production electrician app.

## Simple Windows setup and update checks (current approach)

1. Download the one common Windows test MSI from the **Build Industrial Studio HMI Windows MSI** GitHub Actions run. **No per-PC configuration ZIP is required.**
2. In the cloud configurator, register an individual workstation, assign its HMI screen, and copy the single-use pairing code.
3. Open the installed desktop app, paste the code, and select **Connect this PC**. The app stores the token with Windows DPAPI and reconnects automatically for that Windows account.
4. HMI screen/configuration updates are **checked at startup and every 3 seconds while paired**. After initial pairing, changed screen definitions require the operator to approve **Update now**. Stale readings are withheld pending that approval.
5. **Software installer release checks run on startup even before pairing and every 15 minutes while open.** A release notification appears only if a higher version is published with approved GitHub Releases URL, SHA-256 and publisher metadata. The app **never downloads, executes or installs an MSI automatically**; admins must verify Windows Authenticode and the checksum independently.

The portal does not currently publish a signed production MSI, so the desktop will normally show **No newer approved signed release**. Its auto-update capability is a *notification and version-check mechanism*, not a silent binary updater.

For an older workstation the **Advanced** section still supports manual credentials and legacy ZIP import, but neither is part of normal setup. The portal is currently a simulated cloud demo, with **no running licensed Kepware server or real PLC connection**.

## Starter capabilities
- Web portal to configure endpoint metadata, OPC UA node mappings, display names, engineering units and alarm limits.
- Web-based HMI screen designer (Text, Value, Gauge and Status widgets) with revisioned screens and per-workstation assignments.
- Independently enrolled JavaFX PCs download their assigned screen definitions and scoped simulator readings using a workstation secret plus viewer login.
- Admin-only **Download PC setup ZIP** for each enrolled workstation: prefills portal URL, workstation identity and screen reference in the JavaFX client; the package contains no credentials. The standard Windows MSI is built separately by a dedicated GitHub workflow.
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

Run the **desktop** with `cd industrial-studio/desktop && mvn javafx:run` (or use the separately built Windows MSI from GitHub Actions). Provide the portal URL, viewer credentials, registered workstation ID and one-time enrollment secret. In the portal, open **HMI screens & PCs**, create screen layouts and register each PC. The JavaFX app downloads the layout assigned to that specific PC; editing the layout does not require rebuilding its installer. Current snapshots are **simulated**. See [per-PC HMI setup](docs/PER_WORKSTATION_HMI.md).

## Preconfigured workstation setup packages

1. Set `STUDIO_CLIENT_BASE_URL` to the secure address that client PCs will use (HTTPS is mandatory for remote hosts; default loopback works only on the same PC).
2. In **HMI screens & PCs** register and assign the device. Save its separately delivered one-time-displayed workstation secret.
3. Click **Download PC setup ZIP** in the workstation row. This package includes workstation.json, a settings-only PowerShell copier and instructions. It intentionally excludes credentials, executable Java and the MSI.
4. Download the common **IndustrialControlStudioHMI-Windows-UNSIGNED** build artifact from the experimental `Build Industrial Studio HMI Windows MSI` GitHub Actions workflow, where available. It is unsigned and only for isolated testing; verify hashes and obtain a signed approved build before any industrial deployment.
5. Install the generic MSI, then choose **Import PC setup ZIP** in the JavaFX app (or copy its workstation.json using the included script). The server, workstation ID and user name are pre-filled; the server remains authoritative for screen assignment.
6. Enter the separately issued viewer password and workstation enrollment secret. Reassign or edit the layout in the portal; the client will download the new screen without rebuilding the MSI.

See [per-PC setup package design](docs/PER_PC_SETUP_PACKAGE.md).

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

## Public industrial HMI showcase

The independent read-only public showroom at `/showcase.html` demonstrates three simulated monitoring stations: underground dewatering, ore conveyor, and 600 V substation. It fetches `GET /api/studio/showcase` every two seconds. **This feed never queries Kepware, real OPC UA, site configuration, the workstation registry, or the database.** All readings are synthetic and visibly marked SIMULATED. The cloud showcase page is distinct from the authenticated administrator's per-PC HMI screen designer.

## Safeguard your HMI designs before the demo database expires

In the cloud administrator's **System and releases** page, select **Download design backup (.json)**. This records tag mappings, inert Java source drafts, screen definitions and workstation IDs/assignments, but **never** includes passwords, pairing codes, device secret hashes, or credentials. Store this file in a protected location; these configuration details may be sensitive. The **Restore designs** action can recreate screen/tag definitions on a new instance. On a new database, enroll PCs again; their previous secrets are not portable. Restoring layouts is not transactional and needs administrative review.

This application-level design backup is **not** a PostgreSQL dump. For a complete migration, use a full `pg_dump` export and `pg_restore` on a staging database, with verified versions, TLS, and restricted source access. See `industrial-studio/docs/RENDER_DATABASE_MIGRATION.md`.
