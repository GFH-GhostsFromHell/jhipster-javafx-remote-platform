# Electrical Workbench — Device Security Center (third app)

This standalone JavaFX desktop app uses the existing Electrical Workbench Spring Boot portal for a **consent-based, read-only device-encryption dashboard**. It is separate from the electrical calculator desktop app.

## Scope

- Windows desktop checks the system drive's BitLocker state with **read-only** `manage-bde.exe -status`.
- The device owner explicitly checks a consent box and clicks **Report status to cloud**.
- The portal stores only a random, app-specific device ID, device alias, operating-system name, volume, encryption status, and report timestamp, scoped to the authenticated user.
- Authenticated dashboard: `/device-security.html`; API: `GET/POST /api/device-security/devices`.
- Uses the existing account login for Basic-over-HTTPS API calls; desktop does **not** persist the password.
- A Windows status check may return `UNKNOWN` if BitLocker is unavailable, permissions are insufficient, or the output cannot be verified.

## Excluded intentionally

The app does **not** encrypt/decrypt disks, extract BitLocker recovery passwords, send decryption tokens, lock the workstation, manipulate recovery protectors, accept remote commands, or depend on the cloud for access to local data.

For legitimate recovery, use OS-supported key backup/escrow with device-owner approval and an independent offline recovery method. An enterprise version would additionally require device enrollment, strong device identities, role-based access control, key-management infrastructure, and access auditing; those are not implemented in this starter.

## Run locally

1. Install Java 21, Maven, and (on Windows) Windows BitLocker utilities.
2. Start the existing Spring Boot portal separately with its database and credentials.
3. From this folder run `mvn clean javafx:run`.
4. Optionally set `PORTAL_URL=https://your-deployed-portal`.
5. In the desktop app, select **Check Windows status**, enter a device alias and existing portal login, check owner consent, then click **Report status to cloud**.
6. Open `https://your-deployed-portal/device-security.html` and sign in.

You can run the Maven unit tests with `mvn test`. No MSI or production recovery-key service is produced by this source-only first version.

## Architecture

```text
JavaFX desktop (status only)
     | owner-initiated HTTPS POST using cloud account
     v
Existing Spring Boot cloud portal
     | user-scoped JPA repository
     v
Existing PostgreSQL / H2 database
     |
Cloud dashboard (authenticated GET)
```

The new table is bootstrapped additively because the existing portal uses `hibernate.ddl-auto=none`.
