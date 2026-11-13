# Electrical Workbench — Device Security Center (third app)

A separate JavaFX Windows desktop application backed by the existing Spring Boot Electrical Workbench cloud portal and PostgreSQL/H2 database.

## What's implemented

The desktop provides four readable tabs:

1. **Device status:** read-only Windows BitLocker status, and voluntary owner-scoped cloud status reporting.
2. **Encrypt file:** manually choose **one** regular data file, generate a fresh AES-256-GCM key, independently save and re-enter its complete offline recovery code, and create an authenticated encrypted copy. A live progress bar, elapsed time, and estimated remaining time are shown. Restore a selected `.gfhenc` copy with the offline recovery key.
3. **Cloud recovery:** optional password-wrapped backup of an independently saved recovery code. The backup is encrypted **on the desktop** using PBKDF2-HMAC-SHA256 (210,000 iterations) and AES-256-GCM. The server stores ciphertext only. The owner asks an operator to approve each one-time release; an approval expires after 15 minutes. The owner unwraps the recovered code using the independent escrow passphrase on the desktop.
4. **Support chat:** password-encrypted owner/operator messages using the same interoperable browser/Java AES-GCM format. Messages are encrypted before transmission, server stores ciphertext only. Operator reviews for recovery assistance can be approved/denied with an audit record.

The web portal page is `/device-security.html`. Only users with `ROLE_ADMIN` can view operator inboxes and approve requests. A device owner can access their own status, messages, backup ciphertext, and review requests only.

**Cloud portal operators are NOT the sole route to recovery.** The owner independently saves the original offline recovery code and can decrypt without contacting the cloud.

## Very important scope and limitations

- This is **selected-file encryption**, not full-disk or system encryption. The Windows OS/boot files are not touched.
- Original plaintext files are **intentionally preserved**; no folder traversal, bulk encryption, recursive scans, source deletion, overwrite, remote commands, startup persistence or lockout features.
- Thus creating an encrypted copy **does not secure the original file**. Use this first version only for storing/sharing an encrypted *copy*, or on test files. Production migration/secure deletion is NOT implemented.
- Each file is limited to **64 MiB** in this first version because the JVM's GCM decryption implementation may buffer ciphertext before authenticity verification. Test-volume encryption and a reviewed chunked encryption format would be required for large files.
- Each new encryption operation uses a random 256-bit AES key and 96-bit GCM nonce. A bad key or tampered ciphertext fails authentication and deletes the incomplete restored output.
- Encrypted-copy format has magic `GFHENC01`, a random nonce, then AES-GCM ciphertext and tag. Never modify encrypted files.
- The offline recovery code is a base64url encoding of the 256-bit AES key; it is displayed temporarily, never stored in the desktop app's preferences or in the encrypted file.
- When opting into cloud backup, the **separate escrow passphrase is also not saved** on the desktop or uploaded to the server. Losing both your independent recovery code and the escrow passphrase can make a backup unrecoverable.
- Chat participants must exchange a separate **strong chat passphrase out of band** (a trusted independent channel); there is no automatic key exchange or identity verification beyond account sign-in.
- The encrypted chat/key-wrap scheme is a first-version interoperable protocol, **not independently security-audited**, and lacks production secure messaging features such as forward secrecy, device key verification and passphrase rotation.
- Only an authenticated owner may receive the opaque recovery backup; after a specific admin approval, the owner can retrieve the ciphertext **once within 15 minutes**. The admin cannot see the plaintext key from the portal.
- Cloud backup requests are associated with the owner's manually entered **file label**; verify the correct file/key pairing before relying on them.
- The Windows status probe is `manage-bde.exe -status` only; it never changes BitLocker settings.

## Use the desktop app

1. Install Java 21 and Maven (for development), or extract the Windows Portable build from GitHub Actions.
2. Run `mvn -f device-security-desktop/pom.xml clean javafx:run` from the repository root. Configure `PORTAL_URL` if your portal uses a different HTTPS address.
3. In **Device status**, enter your cloud username/password if you want cloud features. Passwords are kept in memory only during the session.
4. In **Encrypt file**, choose one *non-critical* test file, generate a recovery key, save it somewhere independent of this computer, and **re-enter the complete key**. Click **Encrypt a separate copy**. Confirm the `.gfhenc` copy decrypts correctly with the offline key before using it for anything important.
5. To test optional escrow, open **Cloud recovery** and paste the independently saved offline key. Enter a different long escrow passphrase, check the offline-backup declaration, and upload the encrypted backup. Do not put either secret in chat.
6. To request a cloud release, choose the backup and request operator approval. The cloud operator reviews this at `/device-security.html`. After approval, retrieve once within 15 minutes, providing the *same* escrow passphrase, and use the returned recovery code to restore a copy.
7. In **Support chat**, share a separate strong passphrase with the operator through an independent trusted channel, then send/refresh encrypted messages.

## Build and tests

- Windows CI workflow: `.github/workflows/build-device-security-desktop.yml`; builds and uploads the standalone Windows Portable ZIP (unsigned).
- Desktop test suite: `mvn -f device-security-desktop/pom.xml test`.
- Portal build/tests: `mvn -f portal/pom.xml test`.
- Portal creates status, chat, review, encrypted-escrow and release-audit tables additively on startup because `hibernate.ddl-auto=none`.

## Architecture

```text
User-selected data file  ->  AES-256-GCM encrypted COPY (.gfhenc)
      |                         |
      | offline key             | optional separate password-wrapped key
      v                         v
Independent owner backup     Authenticated Spring Boot portal (ciphertext only)
                                 |
                             PostgreSQL/H2
                                 |
                       Operator approval + audit
                                 |
                   Single-use owner ciphertext retrieval
                                 |
                 Local desktop unwrap using escrow passphrase

Support chat: JavaFX AES-GCM <-> ciphertext-only portal <-> Web Crypto AES-GCM
Device status: JavaFX read-only BitLocker status -> portal -> owner dashboard
```

This prototype should undergo a security and UX review before being presented as production-grade encryption or enterprise key management.
