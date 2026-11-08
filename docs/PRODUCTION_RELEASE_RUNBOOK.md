# Electrical Workbench — Production release and account safety

## Deployment identity and health

- Render service: `remote-javafx-portal`, linked to GitHub branch `main`.
- Public, read-only production endpoint: `/api/public/build-info`. It reports
  `RENDER_GIT_COMMIT` (full commit SHA), whether that value is available, and
  explicit `numericDatasetVerified=false` / `codeComplianceVerified=false`.
  It never reports environment variables, credentials, customer or billing data.
- `/api/public/client-health` and `/api/public/auth-options` are read-only
  unauthenticated smoke checks. Authenticated project and billing endpoints must
  **not** be tested using production users' passwords in CI.
- `scripts/verify-live-portal.cjs --expected=<full main SHA>` compares the
  live commit with the release and checks health/auth/public login HTML. It
  refuses to call a mismatched release successful.

## GitHub / Render automatic deploy recovery

Render service configuration currently requests `autoDeployTrigger=commit`,
but previous November 2026 pushes failed to launch new Render builds. First
check that the Render GitHub installation retains access to the repository
and that the service's connected Git credential is authorized.

The `Verify Live Production Release` GitHub Action runs after successful
`Build Web Portal` jobs on main, manually, and on a six-hour cadence.
It waits for normal Render deployment and checks the exact deployed SHA.
When auto-deploy has not fired, it supports **one** authenticated fallback
deploy-hook POST after the initial checks, then verifies the release again.

**One-time action by a GitHub repository administrator:**

1. In Render > `remote-javafx-portal` > Settings, copy the **Deploy Hook**
   URL. Treat it as a secret; never paste it into an issue or source code.
2. In GitHub repository Settings > Secrets and variables > Actions, create the
   repository secret **`RENDER_DEPLOY_HOOK_URL`** with that URL.
3. Confirm GitHub installation permissions and the Render service's linked
   branch `main`. Leave Render's automatic deploy setting on Commit.
4. Run `Verify Live Production Release` manually once to confirm the
   current commit and public health probes.

If the hook secret is not provided, the workflow fails clearly when production
is behind; it **does not** claim to have repaired Render's integration.
It never requests a specific commit in the deploy hook (which could disable
automatic deploys). The hook targets the service's current linked branch.

Render deploy hooks: https://render.com/docs/deploy-hooks
GitHub provider connections: https://render.com/docs/git-provider

## Account/security and data synchronization

- `/api/profile` and bootstrap account lookups must never manufacture an
  ADMIN tier for a normal client on missing user or temporary database errors.
  Only an authenticated principal holding `ROLE_ADMIN` may use an in-memory
  admin fallback when that principal has no client-account row.
- Account sync is scoped to the Spring Security principal, not a user-supplied
  username. It uses record IDs, revisions, conditional updates and tombstones.
- Validate a whole batch before database writes; transaction rollback protects
  against mid-batch failures. Never merge two different payloads under one
  immutable saved-worksheet ID.
- Tests cover invalid registration/recovery inputs, tier availability,
  sync validation, missing customer profiles, build info and local smoke checks.
- The production environment is **not** a test fixture. Completing a full
  signup/email/password reset, Stripe checkout, Google login, or device
  handoff still needs dedicated test accounts, configured mail/Stripe test
  credentials, a sandbox/staging instance, and explicit test authorization.
  These external workflows are not claimed verified from unit tests alone.

## Manual staging matrix

1. Register a fresh staging-only account; receive a real email verification
   code; verify and sign in; repeat invalid/expired code cases.
2. Request username reminder and password reset, confirm delivery, reset and
   sign in. Verify enabled accounts and disabled accounts differ appropriately.
3. Confirm Tier 1–4 feature access, denied controls, and admin-only management
   paths without charging a real payment instrument.
4. Save a circuit offline on Android, reopen on Windows and iOS after network
   recovery, then reopen in the browser. Assert original revision and full
   assumptions survive. Repeat conflicting edits and account switches.
5. Export original revision as PDF from every platform and check inputs,
   calculation steps, source status and sign-off blanks.
6. Measure Android/iOS real-device button sizes, keyboard overlap, text zoom
   and screen-reader labeling at small screen widths.

These checks require real staging/device runs; GitHub CI alone does not prove them.
