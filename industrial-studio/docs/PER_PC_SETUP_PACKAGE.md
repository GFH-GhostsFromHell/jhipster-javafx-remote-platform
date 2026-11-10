# Per-PC setup packages — Industrial Control Studio

**Implementation status:** feature-branch prototype only. There is no publicly deployed control service or production installer.

## What an administrator does

1. Start the local Industrial Studio portal with distinct, strong admin and viewer passwords.
2. Specify `STUDIO_CLIENT_BASE_URL` at server startup. This is the URL entered into downloaded HMI client profiles:
   - `http://127.0.0.1:8090` is allowed for testing **only on the same host as the portal**.
   - For separate PCs, use a trusted **HTTPS reverse proxy or managed industrial gateway**. Do not expose a raw OT service to the internet.
3. In the **HMI screens & PCs** panel create or choose a screen layout, then register each workstation and assign the right screen.
4. Provide the **workstation enrollment secret** through a separate, approved secure channel. It is only displayed once at registration and the backend stores a one-way hash.
5. Choose **Download PC setup ZIP** for a workstation. The server requires an administrator account and refuses disabled/unrecognized workstations.

The downloaded `IndustrialHMI-<device-id>-Setup.zip` contains:
- `workstation.json`: non-sensitive server address, PC ID, friendly label, viewer username and an **informational** screen ID
- `Install-Industrial-HMI.ps1`: **settings-only** script copying the JSON into the current user's local configuration directory; does not silently execute installers or change execution policy
- `README.txt`: setup and security guidance

**It does not contain:** installer executables, account passwords, workstation tokens, Kepware credentials, Java rules, or PLC source.

## What a workstation operator does

1. Obtain and verify the generic Windows MSI from a **trusted, approved distribution**. The new GitHub Actions workflow produces an **unsigned TEST artifact**, not a production-approved signed release.
2. Install the common `IndustrialControlStudioHMI-Setup.msi`.
3. Launch JavaFX and click **Import PC setup ZIP**. Alternatively, inspect/run the included PowerShell copier under local IT policy. The JSON is stored under `%LOCALAPPDATA%\IndustrialControlStudio\workstation.json`.
4. JavaFX automatically fills the middleware URL, assigned PC ID and viewer username. The screen shown in the package is only a label: the server always determines the actual authorized screen.
5. Enter the separately issued viewer account password and workstation enrollment secret to connect. These are not stored in the ZIP or workstation.json.
6. When an administrator edits or reassigns the PC's HMI screen, the client refreshes the server-authorized screen definition automatically on the next polling cycle. No MSI rebuild is required.

## Security boundaries

- An arbitrary ZIP can be tampered with in transit or by an untrusted local user. Only import packages received from the trusted portal over verified TLS; production should additionally sign configuration metadata with trusted deployment identities.
- The JavaFX importer **does not extract the ZIP**. It reads only `workstation.json`, rejects files over 16 KiB, enforces the format, forbids credentials/unknown fields, and validates the HTTPS/localhost-only URL.
- A copied PC ID cannot retrieve another PC's screen. The server validates the actual workstation token and viewer credentials on every request.
- This is not device-certificate enrollment, passwordless login or a secure remote-control product. Enterprise production requires per-device mTLS, user SSO/MFA, revocation/rotation, verified MSI signing, deployment approval, audit logging and isolated OT boundaries.
- The GitHub Actions installer is **unsigned** and should only be used in a controlled, non-production test environment.
- Never embed long-lived credentials or instructions that execute downloaded Java code in workstation setup packages.

## Release packaging

GitHub Actions: `.github/workflows/build-industrial-hmi-msi.yml`

The build runs Java 21, Maven, JUnit and Windows `jpackage`, then uploads:
- `IndustrialControlStudioHMI-Setup.msi`
- `IndustrialControlStudioHMI-Setup.msi.sha256`

These artifacts share the same executable across PCs. Administrators download the per-PC ZIPs from the configurator; the ZIP config and the MSI are intentionally distributed separately. A future production bundling service can combine them only after publisher-signature verification and organization-approved artifact pinning.
