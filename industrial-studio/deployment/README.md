# Industrial Control Studio local pilot (simulation only)

This is a convenience deployment for **local, isolated demonstration and acceptance testing**, not a production-rated plant control system. It runs the same read-only Spring Boot web configurator and PostgreSQL database outside Render.

## Requirements

- A test PC or VM with Docker Engine/Desktop, Docker Compose, and internet access for initial image downloads.
- 2 CPU cores, 4 GB RAM recommended for a small demonstrator.
- Supported Windows/Mac/Linux host. JavaFX HMI is a **separate** Windows client installer, not bundled inside the server container.
- No external inbound network ports to a PLC or Kepware server. The default portal is reachable **only from the local host** at `http://127.0.0.1:8090`.

## Start

From the repository root:

1. Copy `industrial-studio/deployment/.env.example` to `industrial-studio/deployment/.env.local`.
2. Set independent random passwords in the three `*_PASSWORD` entries (minimum 12 characters for viewer/admin, recommend 32+).
3. Run:

```bash
docker compose --env-file industrial-studio/deployment/.env.local \
  -f industrial-studio/deployment/docker-compose.local.yml up --build -d
```

4. In a browser on the **same computer**, open `http://127.0.0.1:8090/showcase.html` for the independent public **synthetic** sample HMI; visit `http://127.0.0.1:8090/` to design screens and provision workstation IDs.

To stop:

```bash
docker compose --env-file industrial-studio/deployment/.env.local \
  -f industrial-studio/deployment/docker-compose.local.yml down
```

Persistent data stays in the named `studio-pgdata` Docker volume. **Do not use `down -v`** unless deliberately deleting all settings and PCs. Back up the database before upgrades.

## Separate workstation clients

For local-host demonstrations, the desktop HMI connects to `http://127.0.0.1:8090`; other PCs cannot connect until you deploy an approved TLS reverse proxy and properly configure firewall/routing and `STUDIO_CLIENT_BASE_URL`. The raw loopback-only HTTP demo must never be opened to a shared network.

Install the common Windows JavaFX MSI from the repository's experimental Actions build artifact (unsigned prototype). In the web designer, assign layouts to individual PCs and download their settings-only setup ZIP files. Secrets are provisioned separately and never embedded in installers.

## Real Kepware deployment

For a mine or factory, install authorized Kepware on a separate Windows OT server, enable and approve OPC UA certificates, use `SignAndEncrypt`, firewall the OPC UA port away from public networks, and provide approved credentials. The optional Java read-only OPC UA bridge is **not yet validated end-to-end against licensed Kepware** and must be tested in an isolated staging environment. Use certificate files injected as secrets rather than putting them into Docker images or Git.

This demo does **not** provide any PLC write control, safety interlocks, emergency stop, real alarm annunciation, high-availability OPC subscriptions, proper industrial historian, validated safety/security controls, signed installer, or production account management. Obtain site engineering and cybersecurity approvals before any operational deployment.
