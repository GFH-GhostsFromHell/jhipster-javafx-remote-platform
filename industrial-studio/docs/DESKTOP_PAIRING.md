# Register a Windows desktop HMI in three steps

This is a read-only Industrial Control Studio prototype; do not use it to operate industrial equipment.

## On the Render configurator (administrator)

1. Open `https://industrial-control-studio-demo.onrender.com`, sign in as `studio-admin`, and select **HMI screens & PCs**.
2. In **Set up a desktop PC**, enter a friendly name such as "Pump Room PC". Leave the optional PC ID blank so it is generated automatically, and select the screen this PC should display.
3. Click **Register & get setup code**, then **Copy setup code**. The code expires after 15 minutes and works only once. Never post this code publicly.

To move an existing PC to a new device or repair its connection, use **Pair / reconnect PC** next to its name. Generating a new code does not immediately invalidate the existing PC; using the code replaces the previously issued credential for that workstation.

## On the Windows desktop PC

1. Install the current **Industrial Control Studio HMI Windows MSI** from the project's GitHub Actions **Build Industrial Studio HMI Windows MSI (unsigned prototype)** run. The installer is unsigned and is for controlled development only.
2. Open Industrial Control Studio. In **Set up this PC**, paste the complete code copied from the portal into the setup box.
3. Click **Connect this PC**. The app registers the device, downloads the assigned screen and readings, and encrypts the PC credential using Windows DPAPI under the current user account.

On subsequent launches on the **same Windows user profile**, the HMI reconnects automatically without requiring the admin, viewer or device password. The setup code is not stored or reused.

## Common problems

| Message | What it means | Action |
|---|---|---|
| Setup code incomplete or invalid | Part of the code wasn't copied | Click Copy setup code in the portal and paste the **whole** code |
| Setup code expired or already used | The code is only valid for 15 minutes / one device enrollment | Select Pair / reconnect PC to generate another |
| This PC was disabled or re-registered | Credentials were revoked or the device was turned off | Verify the PC is enabled in the portal, then pair again |
| Server hasn't deployed the feature | The Render service is behind the desktop client version | Check the latest successful Render deployment |
| Windows credential protection failed | Your Windows account could not save the token using DPAPI | The HMI may run for the current session; re-pair after restart and review Windows user policy |
| HTTP 503 / service unavailable | Render may be restarting or waking the free app | Open the portal URL in a browser and retry |
| No HMI values | Could be offline or bad-quality samples | Never trust stale data; verify service and PLC/OPC UA status separately |

## Security and backwards compatibility

- Device pairing uses a cryptographically random 256-bit secret, single-use and short-lived. The portal stores only its SHA-256 digest. The HMI exchanges the code for a **different** device token over HTTPS.
- The long-lived device token is stored encrypted with Windows DPAPI (current Windows account), not in the ZIP file or command-line arguments. No access credential is included in a downloaded setup ZIP.
- The backend checks each workstation token and enabled state every time it serves a screen or readings.
- The manual viewer-password setup and old configuration-only ZIP import remain under **Advanced**, but are not required for normal registration.
- Pairing codes should be delivered only to approved physical PCs and operators. This is **not** hardware-bound attestation; copied device tokens remain sensitive. Production OT deployments require mTLS machine certificates, identity lifecycle management, audit trails, rate limiting, SSO/MFA, replay protection and independent safety reviews.
- On macOS and Linux the current prototype does not store secrets between launches; use Windows for the test HMI.
- The cloud deployment is an experimental configurator / read-only simulator, not a certified SCADA or safety system. Keep PLCs and Kepware on an isolated OT network.


## Per-PC configuration updates (new)

1. In **HMI screens & PCs**, choose a PC from **Manage a registered PC**.
2. Its status displays **Update available** or **Up to date**, with the last applied time.
3. Saving a screen layout, changing the global tag configuration or editing a PC's assignment automatically changes its desired revision. The app checks on startup and roughly every three seconds while connected.
4. An administrator may also click **Request update for this PC** to ask a single selected PC to refresh, without changing any other PCs.
5. On the desktop, the operator sees **Update available** and clicks **Update now**. The app downloads the declarative layout and tag readings, checks matching revisions and acknowledges successful application to the portal.
6. The portal updates the per-PC status to **Up to date** after the authenticated desktop's acknowledgement.

For a previously registered Windows PC, the last accepted configuration fingerprint is saved locally without device credentials. After restart, the app compares it against the server **before** displaying readings. It never silently installs a new revision. At first pairing only, the initial assigned screen loads automatically to keep setup simple.

**Safety:** Whenever a configuration change is awaiting acceptance, the prototype clears old readings rather than showing values under an outdated layout. There is no automatic PLC write, HMI script execution, remote process restart or MSI binary auto-installer. Software installer upgrades remain separate from revisioned configuration updates.

## Automatic software-update check (November 2026)

The JavaFX client checks the **public, metadata-only** `GET /api/studio/public-app-manifest` endpoint **on startup even before a PC is paired**, again when a paired PC connects, and **every 15 minutes while the app stays open**. No API keys or device tokens are required for release metadata. The check is also available under **Connection settings and help → Check software updates now**.

Each MSI embeds the full `0.1.<GitHub Actions build number>` version into its Java application resource. The client compares all three version components numerically. It only advertises a newer release when the server explicitly declares a signed MSI and provides a SHA-256 digest, publisher name, and approved URL under this repository's GitHub Releases path. These are publisher assertions, not cryptographic validation by the client. The client opens the release's GitHub page for a person to review, but **never downloads or silently installs executables**. Verify Authenticode publisher and SHA-256 before installing. The repository's CI-built MSI is **unsigned** and is not a publicly approved update.

To publish a legitimate signed release later, an administrator must deliver a properly signed MSI to this repository's GitHub Releases and configure these Render environment variables:

- `STUDIO_DESKTOP_RELEASE_PUBLISHED=true`
- `STUDIO_DESKTOP_RELEASE_VERSION=0.1.<build>`
- `STUDIO_DESKTOP_RELEASE_URL=https://github.com/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases/download/<tag>/<file>.msi`
- `STUDIO_DESKTOP_RELEASE_SHA256=<64 hexadecimal digits>`
- `STUDIO_DESKTOP_RELEASE_PUBLISHER=<expected verified Windows signing identity>`

Do not set the publication flag until the released MSI's signature and hash have been independently verified and the appropriate site IT team approves rollout. Changing HMI screen configurations is a separate, user-approved workflow and does not install software.
