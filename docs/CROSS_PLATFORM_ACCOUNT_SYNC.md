# Electrical Workbench — Cross-platform account record synchronization

Implemented in the November 2026 account-sync feature. This describes what the code supports; a successful build is not proof that any particular phone, desktop installation or production database has received a new release.

## How the shared account works

All clients use the Electrical Workbench portal over HTTPS. A server-authenticated account is identified using the **authenticated principal** from Spring Security; the client never sends a username to select whose records to modify. The PostgreSQL table `account_sync_record` stores `username`, `bucket`, `record_id`, `revision`, `payload`, `deleted`, and `updated_at`. Records and deletion tombstones are returned on the same signed-in account's GET request.

Endpoints:

- `GET /api/sync/{bucket}`: retrieve the latest records with revisions and tombstones (up to 5,000 rows per bucket; page support is not yet implemented).
- `POST /api/sync/{bucket}/batch`: send at most 200 records with their last-known revision. Accepts new records at revision 0, returns accepted revisions and any conflicts.
- Only whitelisted buckets can be read or modified. A record's JSON must be 64,000 characters or less. This feature is for **text and structured records**; it is not a photo or document-storage service.

### Current client behavior

| Client | Save locally without internet | Synchronizes when online | Cloud record access |
|---|---|---|---|
| **Android** | Existing native workspace SharedPreferences arrays, bender shoe preferences | After sign-in, on app foreground/active 5-minute cycle, on native saved records, manual Sync from Profile | Profile Sync + Field Suite web workspace |
| **iOS 0.4** | Native UserDefaults field/safety/troubleshooting records, quiz answers, bender shoe | After verified password sign-in, foreground/active refresh, native record changes, manual Sync from Account | Account Sync section and embedded web records viewer |
| **Windows desktop** | Existing per-user JSON records and bender shoe Java Preferences | After login, record saves, every 5 minutes while app is running | Desktop native workspace and web records viewer |
| **Web** | Browser localStorage field projects and bender profiles | On page load, save, 60-second interval, return to foreground or online event | `/account-records.html` shows and exports all server collections |

The same subscription and account permissions were already shared across platforms. This addition syncs **record data** through the authenticated PostgreSQL account rather than through individual browsers or mobile devices.

### Collections

The server supports account-scoped projects, material takeoffs, equipment nameplates, commissioning, inspection, trade hours, credentials, quiz history, unsafe conditions, glove inspections, maintenance, storm stand-downs, safety assessments, bender profiles, troubleshooting cases, saved calculations, panel and equipment designs, favorites/presets, and preferences. **Not every client creates every collection.** The web account records viewer can read any available collection. The iOS client currently handles a subset used in its native workspaces; Android and desktop handle their respective native collection arrays.

Imported Android, iOS, and Windows records may use slightly different property names; adapters retain the original server JSON wherever possible and normalize essential native fields (for example, `hours` and `title/name`) for their own screens. The hosted Field Suite's full project payload is richer than native per-item project summaries. Do not treat the normalized native view as a lossless editor for every advanced web field.

### Offline and conflict policy

- New local records get stable `_syncId` identifiers. Existing unsynced records are assigned IDs during initial synchronization.
- The server uses **optimistic revisions**. A write based on an obsolete revision is rejected. The local copy and a conflict backup are retained for the user rather than silently applying last-writer-wins.
- A deletion produces a **tombstone** only for a record previously mirrored on that device. Another offline device can later read and honor it. The server retains tombstones; compaction is not implemented.
- iOS conflict export is under **Account → Export conflicting edits**. Browser local conflict backups are exportable on the records page. Android/desktop record conflicts are preserved in their respective local preference/file stores; a dedicated interactive resolution editor is future work.
- Offline edits stay on the device. Sync requires a functioning internet connection, current server credentials, and a database; it does not continue running after the app process exits.
- On first-time synchronization, pre-existing identical-looking records with **different IDs are not automatically deduplicated**. Review duplicates when migrating old local data.
- Multi-device edit, delete, account revocation and reconnect scenarios should be **tested with real signed-in accounts** before relying on the system for operational or safety-critical records.

### Data safety, backup and limitations

- The server stores JSON documents in your configured PostgreSQL instance. Its retention and backup depend on the hosting provider. The app does not create an external disaster-recovery backup.
- The web page permits JSON exports; keep exported account/safety records secured.
- Passwords are not written to the sync payload. Native API clients hold passwords only for the current signed-in process. Web sync uses the existing session cookie.
- Raw photos, attached image binary data, all historic unsaved calculation inputs, device-specific theme appearance, and every possible Android/Windows application preference are **not** synchronized. No universal Cloud Sync switch has been claimed for those files.
- Native iOS bender calibration is a single active measured shoe plus cloud profile list; multiple-profile selection and manufacturer mark validation still need a physical device. Code tables and permissions are not part of this sync feature.
- The iOS simulator artifact cannot be installed on an ordinary iPhone. Apple Developer signing/TestFlight and actual iPhone/iPad testing remain required.

### Validation

`node scripts/validate-account-sync.cjs` exercises two independent browser client instances with a mock server: initial import, concurrent independent additions, deletion tombstones, and stale-write conflict preservation. It also checks the offline static asset cache does not cache authenticated API responses. GitHub Actions compiles the portal, Android app, iOS simulator, and Windows desktop client. End-to-end tests against the deployed PostgreSQL service and physical signed-in devices remain separate release checks.
