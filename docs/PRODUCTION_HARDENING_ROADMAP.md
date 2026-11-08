# Electrical Workbench — production hardening and next release gates

Updated: 2026-11-08. This is an implementation and acceptance checklist, **not** a declaration that every platform is ready for commercial use.

## 1. Deployment and production health (in progress)

- [x] Public build-info endpoint exposes the deployed full Git SHA and explicit `numericDatasetVerified=false`.
- [x] Main-branch GitHub workflow checks public health, authentication options, and landing page after the web build.
- [x] Added distinct **build-only** health verification: if the live SHA is correct but `/index.html` returns 401/403, fail as an access-control incident rather than incorrectly triggering a redundant Render deployment.
- [ ] Resolve actual anonymous `/index.html` HTTP 401 reported by the GitHub Actions production verifier (2026-11-08, run 42506824957). Inspect live request/security logs and proxy configuration. Do **not** silently accept 401 as a successful public login page.
- [ ] Set `RENDER_DEPLOY_HOOK_URL` in GitHub Actions secrets from the account-owned Render deploy hook, or repair the GitHub/Render auto-deploy integration. Never commit hook URLs or credentials.
- [ ] Confirm automatic deployment for three consecutive merges without manual API deployment, and run authenticated browser smoke checks with a dedicated test account.

## 2. Account, subscriptions, saved projects

- [x] Existing CI runs `validate-account-sync.cjs`, `validate-circuit-sync-restore.cjs`, `validate-project-circuit-archive.cjs` and portal Java tests.
- [ ] Add isolated staging integration tests covering sign-up verification email delivery, resend/expiration, password reset, account lockout, subscription tier changes and authorization boundaries. Do not exercise real customer accounts.
- [ ] Verify device-to-device sync with an authenticated staging account and simulated network drops, including offline edits, conflict handling, retries and duplicate-safe recovery.
- [ ] Verify database backup and restore **on a non-production copy**. Free PostgreSQL plans may have retention/expiration limitations; document the actual provider contract.

## 3. CEC 2024 / OESC numeric verification

- [x] Store `OESC_ON_2024` and `AUTHORIZED_TABLE_VERIFICATION_REQUIRED` provenance with saved circuit revisions and in PDF reports.
- [x] Maintain table-by-table evidence rows in `CEC2024_TABLE_VERIFICATION_REGISTER.json`.
- [ ] Obtain a lawful, current copy of CSA C22.1:24 and the applicable Ontario amendments and any effective-date errata.
- [ ] For every numeric table row used by a calculator, record table/rule, edition, installation assumptions, reviewer, date, source reference, and distribution rights. Keep proprietary text outside the public repository unless authorized.
- [ ] Independently validate conductor ampacity, correction/grouping factors, voltage drop, motor/transformer OCPD, service demand, box fill, conduit fill, pull boxes, bonding and special installations against worked edge cases.
- [ ] Keep all output marked **NOT CODE VERIFIED** until the relevant rules, tables and installation conditions have passed review.

## 4. Field usability / mobile parity

- [x] Four-platform 120/208/347/600 V quick presets and industrial worksheet links have been added.
- [ ] Perform physical-device accessibility checks at 320–430 px widths, large system fonts, landscape, and offline mode.
- [ ] Check bend marks and bender orientation against actual bender models; verify minimum bend spacing and 10 ft EMT length with independent physical measurements.
- [ ] Test one-handed input, keyboard overlap, screen reader labels, color contrast, PDF printing and in-app update behavior on all supported platforms.

## 5. Industrial electrical tools

- [x] Existing industrial CT/PT, relay-current translation, 3-phase power, ideal transformer fault arithmetic and 4–20 mA scaling are available with offline/native regression tests.
- [ ] Add instrument-specific commissioning checklists and evidence attachments without presenting estimated transformer faults as actual available fault current.
- [ ] Add relay setting review workflows with manufacturer-specific equipment data and an explicit engineer/qualified-person approval boundary; never auto-apply live protection settings.
- [ ] Verify the exact licensed electrical standards and local utility/mining requirements before advertising any tool as compliant.

## Commercial release criteria

Do not claim an electrician-grade Code compliance certificate, unattended deployment reliability, or production authentication success from arithmetic unit tests alone. Require passing CI, verified live SHA, public sign-in access, authenticated staging flows, a documented table-review process, and physical-device acceptance checks.
