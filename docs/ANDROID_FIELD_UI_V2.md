# Electrical Workbench Android — field-first UI v2

## Design changes

This pass reworks the **Android native UI** and the Android embedded-web
responsive safety net. Android is the priority because it is the primary
jobsite device; changing the web, iOS and Windows themes independently without
device testing would introduce different risks.

**Palette and contrast**: `ElectricalUiTheme.kt` is the shared native Material 3
palette. Body text, secondary text, primary button text, warning and error
text use explicit high-contrast roles on dark cards and backgrounds. Sign-in,
update gate, native sizing/industrial/field calculators and the main Android
shell now use the same visual system.

**Common navigation**: The first screen prioritizes the offline conduit bender,
offline circuit sizing, equipment troubleshooting and global tool search.
Projects, Safety, Study and Code updates are secondary one-tap actions. The
Find a Tool screen defaults to a small set of common tasks, with one search,
one category picker, an offline filter and a Show all action. Favourites,
recently used and account-scoped storage remain available. Online 3D bending
and advanced web calculators remain discoverable.

**Less scrolling**: The Study, Field, Projects, Career and Safety subareas
use a single section selector instead of a horizontally scrolling tab bar.
Each task card is one tap target instead of containing a separate full-width
Open button. Circuit Sizing is a three-step wizard (Circuit → Conductor →
Result) and its saved revision/PDF controls are expanded only when needed.
Transformer load cards summarize existing values and expand for editing.
Conduit measurement instructions collapse behind a button while preserving
the tape-zero datum and warning by default. Android WebView action buttons
can share a row where room exists.

**Offline behavior**: Native bending, circuit sizing, pull-box calculation,
transformer sizing, field relay arithmetic and selected troubleshooting
remain offline. Web/3D tools still require network access. No electrical
math, licensed-code table values, cloud entitlement logic, signing behavior,
or project synchronization rules have been changed.

## Automated checks
- Android Gradle: offline arithmetic tests, compilation, APK packaging.
- `scripts/validate-android-usability.cjs`: WCAG contrast checks for the
  core palette, path-to-tool contracts, centralized visual theme presence,
  removal of horizontally scrolling section tabs and preservation of
  the expandable safety-critical input sections.
- Existing web navigation contract checks still validate stable routes.

## Physical QA still needed
- Test narrow phone (~320dp), typical phone (~390dp), tablet and split screen.
- Test dark color contrast outdoors, 1.3x/1.5x system font scale,
  TalkBack reading order and hit targets.
- On Circuit Sizing: enter *verified* values on all three steps, recalculate,
  edit prior inputs and ensure stale results are rejected on save.
- Confirm favourite, recent, account switching and offline launch of native
  conduit/circuit/troubleshooting tools.
- Test Transformer editing/deletion, new loads and the collapse/expand state.
- Verify cloud-related errors, account registration/recovery, PDF exports and
  native navigation after returning from a WebView.
- Perform final install/upgrade and Android signing/keystore compatibility
  on a real test device before describing it as field-proven.

These are usability tests, not approval of CEC/OESC code compliance.
