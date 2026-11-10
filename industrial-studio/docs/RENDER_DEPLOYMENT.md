# Render: Industrial Control Studio cloud configurator

**Deployment boundary:** Cloud manages screen and tag configuration only; the image hard-fails if configured for direct Kepware OPC UA access. It is a TEST/DEMO platform until access control is hardened.

## Setup

Do not reuse the existing Electrical Workbench Render service or its database. Create a separate Render Web Service, separate PostgreSQL and credentials.

- Repo: `https://github.com/GFH-GhostsFromHell/jhipster-javafx-remote-platform`
- Branch: `feature/industrial-control-studio-starter` (until isolated to its own maintained repo)
- Runtime: `Docker`
- Docker context: repository root `.`
- Dockerfile path: `industrial-studio/portal/Dockerfile.render`
- Service: `industrial-control-studio` or a free name available in your workspace
- Region: same as database, preferably Ohio for Ontario users
- HTTP port: `PORT` (default 10000) bind to `0.0.0.0`.
- Use a separate PostgreSQL database; Render's free PostgreSQL currently expires 30 days after creation. For persistent work, use a paid backed-up DB and production change control.

### Required environment variables

```
STUDIO_BIND=0.0.0.0
STUDIO_SOURCE_MODE=SIMULATION
STUDIO_CLOUD_CONFIG_ONLY=true
STUDIO_DB_URL=jdbc:postgresql://<internal-database-host>:5432/<db-name>
STUDIO_DB_USER=<separate-database-user>
STUDIO_DB_PASSWORD=<secret-database-password>
STUDIO_ADMIN_USER=studio-admin
STUDIO_ADMIN_PASSWORD=<random 20+ character value>
STUDIO_VIEWER_USER=studio-viewer
STUDIO_VIEWER_PASSWORD=<different random 20+ character value>
STUDIO_CLIENT_BASE_URL=https://<render-service>.onrender.com
```

Configure secrets in Render's Environment UI, never in Git or downloadable PC ZIPs. Render's Spring Boot server is behind a managed TLS proxy. Do not use the onrender.com cloud host to reach Kepware or a PLC.

### Operational limitations

- Demo uses HTTP Basic authentication over Render HTTPS for the web configurator. It lacks enterprise SSO/MFA, device certificates, WAF/rate limiting, audit trails and fine-grained site tenancy. Suitable only for a limited non-operational test with no sensitive customer or process data.
- No live industrial telemetry should be sent to this deployment. No PLC control is supported.
- The HMI installer must be configured with the cloud URL and an independently provisioned workstation enrollment token and viewer password.
- Raw client IP addresses and admin details should not be logged, and secrets must not be stored in downloaded ZIPs.
- The free Web Service can spin down after inactivity and takes time to wake. It has no persistent local filesystem; a free database expires after 30 days.
- Do not enable the optional KE PWARE source mode in Render. Future production topology needs an OT edge gateway that publishes read-only, allowlisted data **outbound**, with mTLS, segmentation, device identity, attestation, policy review and stale-data handling.

### Verification

- Visit the deployed HTTPS home page, which should show "READ-ONLY SIMULATOR".
- Sign in using the separately supplied administrator credentials.
- Save a screen layout and create a PC; verify the data persists after restart (PostgreSQL).
- Download a PC setup ZIP. Verify it contains only JSON settings and guidance, not passwords, tokens or executable code.
- Check `/api/studio/status` using the authorized account: mode should be `SIMULATION`, readOnly true, opcUaConnected false.
- Validate unauthenticated /api/studio/screens or /api/studio/config returns 401, viewer cannot edit, and mismatched workstation token returns 403.
- Never commission an HMI based solely on this demo validation.

Reference: https://render.com/docs/free and https://render.com/docs/web-services.
