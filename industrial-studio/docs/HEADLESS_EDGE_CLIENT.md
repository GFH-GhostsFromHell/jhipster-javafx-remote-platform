# Industrial Control Studio — headless edge collectors (experimental)

## What is implemented

Industrial Studio now supports two separate client types:

- **JavaFX HMI**: a visual, read-only Windows desktop, with per-PC HMI layouts.
- **Headless edge collector**: a Java 21 console/JAR application requiring no display. Each
  collector has its **own cloud ID, enrollment secret, data source, input tag mapping,
  script draft, revision, recent data and simulated output proposals**.

The collector's network design is outbound-only: it reads from an approved **site-local
Kepware OPC UA server**, then sends selected high-quality numeric telemetry to the
existing Industrial Studio cloud REST API over HTTPS. The server evaluates the collector's
restricted math rules and displays **dry-run proposed outputs** to an administrator.

No cloud endpoint distributes direct PLC commands. No endpoint carries arbitrary Java
or JavaScript for execution on the edge. **There is no OPC UA write method anywhere
in the headless collector.** This is the foundation for business-rule prototyping,
not a PLC controller.

## Topology

```text
Factory or mine OT network                             Cloud / Render
PLC(s) <-- native drivers --> Kepware OPC UA Server
                                      ^
                                      | Private OPC UA / Basic256Sha256 /
                                      | SignAndEncrypt, approved certs & user
                                      |
                            Headless Java edge collector
                                      |
                                      | OUTBOUND HTTPS + per-device token
                                      +------------------------> Industrial Studio API
                                                                | versioned settings
                                                                | bounded cloud math
                                                                | recent telemetry
                                                                +--> administrator UI
                                                                +--> dry-run outputs ONLY
```

The headless collector **does not reach a PLC directly by EtherNet/IP, Modbus,
Profinet, or any other driver**. Kepware supplies its certified/authorized
driver interface on the local OT side. The headless client is an OPC UA client,
not an OPC UA server. The cloud server is *not* a Kepware server.

## Set up a simulated collector

1. Sign in to the cloud portal as administrator.
2. Open **Headless data collectors**, enter a unique collector ID and name, and
   click **Create headless collector**.
3. Store the displayed **one-time secret** in the approved local service credential
   mechanism. It is not kept as plaintext by the backend.
4. Change tag labels, source NodeIds and the per-client script draft as needed.
   The initially provisioned tags are copied from the shared tag mapping for convenience,
   but subsequent changes belong only to this collector ID.
5. Save the collector configuration. The server increments its revision.
6. Build the headless Java 21 JAR:

```shell
cd industrial-studio/edge-agent
mvn -B verify
java -jar target/industrial-studio-edge-agent-0.1.0-SNAPSHOT-agent.jar
```

Before starting the Java process, provide the following environment variables
from your operating system's secure service configuration:

```text
STUDIO_EDGE_CLOUD_URL=https://industrial-control-studio-demo.onrender.com
STUDIO_EDGE_ID=<exact registered agent ID>
STUDIO_EDGE_TOKEN=<one-time secret issued by the cloud, stored securely>
```

Avoid putting secrets in shell history, source code, installer ZIPs, logs, or
Windows shortcuts. For a long-running deployment, configure an IT-approved
Windows Service supervisor or service account to manage this Java process with
restarts, logging and automatic startup. **The provided JAR is not itself
installed as a Windows Service or as a signed installer.**

Each cycle (about 5 seconds) fetches the collector's revision, generates
synthetic numeric readings in `SIMULATION` mode and uploads them to the cloud.
You can view the latest readings and proposed rule outputs in the admin panel.
No telemetry historian, retention controls or production reliability guarantee
are part of this demo.

## Local Kepware read-only mode

Select `KEPWARE_READ` for that collector in the cloud. **Do not enable until
the site has reviewed the environment and certificate configuration.** The
collector refuses a real OPC UA read unless all local safeguards are present:

```text
STUDIO_EDGE_LOCAL_OPC_READ_ALLOWED=true
STUDIO_EDGE_OPC_ENDPOINT=opc.tcp://<approved-private-Kepware-server>:49320
STUDIO_EDGE_OPC_CLIENT_PKCS12=<path to client identity P12>
STUDIO_EDGE_OPC_CLIENT_PKCS12_PASSWORD=<secret set by OS service manager>
STUDIO_EDGE_OPC_SERVER_CERT=<approved Kepware server X.509 certificate>
STUDIO_EDGE_OPC_USER=<least-privileged Kepware read-only user>
STUDIO_EDGE_OPC_PASSWORD=<secret>
STUDIO_EDGE_APPROVED_NODES_FILE=<local node allowlist path>
```

The approved-nodes file contains **one exact OPC UA NodeId per line**.
Lines beginning with `#` are comments. Every cloud-configured input tag NodeId
must appear in this site-maintained file. Cloud configuration cannot add new
read privileges. It also pins the endpoint URL to a local environment setting
that the cloud cannot change. The OPC UA session uses encrypted and signed
Basic256Sha256, a client certificate, an approved server certificate and a
read-only Kepware identity. Data with bad OPC quality or non-numeric values is
discarded rather than being reported as healthy.

**This OPC UA connector has compiled in CI, but it has not been tested
end-to-end against a licensed/operational Kepware server.** There is no
existing cloud Windows VM or installed Kepware server in this project.

## Rule language and cloud preview

The per-collector editor uses a deliberately restricted math syntax:

```text
# example with an input tag named tankLevel
SIM_WRITE desiredPumpSpeed = CLAMP(tankLevel * 12 + 150, 0, 1800)
```

Rules support only the shown linear scale, offset and clamp operation.
Maximum 20 rules and 8,000 script characters per collector. Outputs cannot
target an input tag. Unknown inputs, invalid syntax, infinite values or
unbounded constants fail validation. No Java, JavaScript, shell commands,
reflection, HTTP calls or external modules can be entered as executable code.

The cloud returns **proposed output values** marked
`DRY_RUN_ONLY` and `SIMULATED_WRITE_NOT_SENT`. These are **never PLC writes**.
Use **Prepare sample inputs → Run dry-run test** to try values before saving
rules for simulation. The cloud also evaluates saved rules when it receives
telemetry from an authenticated collector.

To test more complicated business logic, the next safe extension is a
separately isolated and reviewed rules runtime, with versioned test cases and
approved deployment packages. Cloud-provided executable source must never run
unrestricted on the OT host.

## What is needed for genuine PLC writes later

A separate **site-engineered, locally authorized** write-control subsystem,
not a generic cloud toggle. Required design items include written approval
for each allowed output NodeId, authenticated least-privilege OPC UA writer
identity separate from the reader, maximum change rates and process limits,
interlocks checked at the PLC, controller modes, dual approval, heartbeat,
quality/stale-data rejection, manual override, change windows, immutable
audit trails, command deduplication and explicit rollback/fail-safe behavior.

No safety function, emergency-stop, trip, protection relay or critical
sequencing is allowed to depend on cloud availability. Cloud code is only
an engineering proposal until a qualified site process engineer reviews,
signs, stages and commissions it. Follow applicable ISA/IEC 62443,
NIST SP 800-82 and the mine/factory's safety-management procedures.

Security references:

- https://csrc.nist.gov/pubs/sp/800/82/r3/final
- https://www.ptc.com/en/support/help/-/media/3B99A6D6D01A4628BD4130E2380A30F7.ashx
- https://support.ptc.com/help/kepware/features/en/kepware/features/OPCUACONFIGMNGR/quick-start-tutorial.html

## Current limitations

The cloud headless collector list shows only the most recent sample held in
memory; a Render restart resets last-seen state, but configured clients and
hashed secrets remain in PostgreSQL. A database upgrade/backup is required
before its free-plan expiry. The prototype uses administrator HTTP Basic
authentication and edge bearer-token auth; **it is not a production
industrial edge gateway**. Before site deployment, implement SSO/MFA,
mTLS for edge identities, signed configuration approvals, persistent
encrypted telemetry, high-availability operation, certificate lifecycle
management, structured diagnostics, OT network segmentation, service
management, a test simulator, and field acceptance tests.
