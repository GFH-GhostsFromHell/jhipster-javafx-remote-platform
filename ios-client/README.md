# Electrical Workbench — iOS 0.3.0

Native SwiftUI client for **iPhone and iPad (iOS 17+)**, sharing the production Electrical Workbench cloud portal with Android, desktop and web. The app compiles for iOS Simulator in GitHub Actions.

## What changed in 0.3.0

- Native password account sign-in to `/api/client/bootstrap`, server-verified subscription features and role/override checks. Password stays in memory for the active app session; a password-free, 24-hour offline identity cache allows verified basic tools only. Native cloud sign-in also attempts to transfer a portal session cookie into WebKit for web tools.
- Twelve quick native electrical calculators: transformer, motor, voltage drop, busbar, capacitor, power-factor correction, fault current, three-phase power, 4–20 mA, welders, refrigeration and PVC expansion. Code-driven factors must be entered/verified; full service/CEC worksheets still use the portal.
- Native Safety workspace: stand-down timer, PPE comparison, approach boundary comparison, hazard reports, glove inspections, and maintenance records.
- Native Learn, Field, Projects and Career workspaces: CEC section navigator and formulas, logic trainer, nameplates, commissioning, blueprint scaling, grounding wizard, material takeoffs, project logs, cable planning, training/hours/credential records and wage calculator.
- Native hosted ChatGPT user interface using the **existing Electrical Workbench portal OAuth/ChatGPT service** and its subscription permissions. Connecting to ChatGPT still requires a supported portal OAuth flow; native iOS sign-in does not automatically sign users into ChatGPT.
- Enterprise Team membership and tier/seat counts from `/api/enterprise` and `/api/enterprise/membership`, with management actions delegated to the existing server-authorized web manager.

**Feature parity is functional for these workspace categories, not yet a claim of identical native implementation or cross-device sync.** The Android app has locally persisted records; iOS native local records are independent, and neither native store currently synchronizes with the other. Real-device QA, app-store billing compliance, Apple signing and TestFlight submission still require deployment work.

## Feature coverage

| Function | iOS offline (native) | iOS online (same portal as other apps) |
|---|---|---|
| Offset and rolling offset | Yes: tape marks, 10-ft stock, angle and shrink | Advanced bending worksheet |
| 90° stub-up | Yes: verified shoe deduct / calibration | Visual & 3D bender |
| Back-to-back 90° | First ARROW mark + explicit second STAR datum | Full step-by-step instructions |
| Three-point saddle | Traditional references or **geometry-only** with warnings | Visual bend lab |
| Four-point saddle | Geometric layout, **not verified shoe marks** | Advanced bender / 3D |
| Kicked 90° | First verified 90 and kick geometry, **not a guessed kick mark** | Advanced bender / 3D |
| Shoe calibration | Yes: measured deduct and gain stored on device | Web calibration stored in web profile |
| Bend gain, precut length, parallel rack | Yes | Full fabrication worksheets |
| Pull-box sizing | Straight / angle / U / mixed / back-wall | Full CEC pull-box worksheet |
| 309A study quizzes | 25 offline questions, 5 per task category, local progress | Other study features only where available on portal |
| Troubleshooting | 21 offline topics; local cases & sharing | Web knowledge base |
| CEC 2024 calculators | 12 native engineering and formula worksheets | Full server-side CEC, service demand and guided worksheets |
| Quotes, projects, drawing takeoffs, control simulation | Native project log, material takeoffs, basic blueprint scale and simplified control trainer | Full hosted Field Suite |
| 3D multi-bend conduit preview | — | Existing interactive web/3D experience |
| User management and subscription tiers | Native verified profile, feature permissions, sign-out, business membership readout | Existing admin and team manager |
| Account verification and password reset | — | Existing account web screen |
| App update checking | Checks release feed at launch, foreground and every 30 min while active | Opens App Store **only after** a real store URL is configured |

Cloud tools use the current server-side feature/override map. Native iOS now requires a verified account; offline basic tools require a password-free, recently verified identity cache (24-hour limit). Premium tools fail closed while offline. Native and cloud gating must still be validated on real devices and against revoked credentials.

### Safety and accuracy

- All physical bend instructions need the exact shoe's manufacturer references, measured deduct/gain, sufficient clearances and confirmation of springback.
- A pencil mark can lie inside a 10-ft EMT length even when the total developed multi-bend geometry or the physical bending shoe requires more room. Stock and shoe-access validation on complex bends still require field confirmation.
- The four-point saddle's native A/B/C/D values are geometric points and **must not be used directly as ARROW/NOTCH bender marks** without an appropriate shoe method.
- Kick rise by itself does not provide a reliable physical kick position.
- The app includes original study guidance, not licensed CSA code tables or an official Red Seal exam.

## Build

Requires a Mac with current Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```sh
brew install xcodegen
cd ios-client
xcodegen generate
open ElectricalWorkbench.xcodeproj
```

Select the **ElectricalWorkbench** target and iPhone/iPad simulator. The [Build iOS Client](../.github/workflows/build-ios-client.yml) GitHub workflow builds and uploads an **unsigned Simulator app ZIP** after every relevant PR/push. This artifact **cannot be installed directly on an ordinary iPhone**.

To install on an actual iPhone, choose a compatible Apple developer team in Xcode and configure a unique bundle ID, provisioning and signing for the device. Production distribution requires Apple Developer Program enrolment, App Store Connect, signing, provisioning, TestFlight and Apple review.

## Accounts and subscriptions

The web WKWebView shares a persistent website data store and session cookies across cloud tools for the same HTTPS origin: \`https://remote-javafx-portal.onrender.com\`. Native sign-in uses the same `/api/client/bootstrap` endpoint as Android. Passwords are kept in active-session memory, **not persisted**. The app attempts to establish a matching WebKit portal cookie. Account creation and recovery remain web forms. Google/OAuth links currently open the system browser; Safari's login cookies are **not automatically shared** with the in-app WKWebView, so test third-party sign-in before treating it as a supported login method.

Android's offline bender calibration and the iOS native shoe profile are **separate** local profiles. Web project data depends on the portal's web storage; local device records do not sync across platforms. Account tiers, feature overrides, and cloud authorization are verified live from the server. Native offline basic access is limited to a 24-hour verified cache; premium features are unavailable offline. A shared cloud sync/merge service for Android and iOS local records does not yet exist.

App Store digital-subscription/IAP rules require a compliant StoreKit purchase or an applicable Apple exception before a production App Store release exposes paid web checkout. An initial code-signed TestFlight build is **not yet published**.

## Optional Apple-signed IPA / TestFlight workflow

The manually dispatched [Sign and Release iOS to TestFlight](../.github/workflows/release-ios-testflight.yml) workflow can package a signed iPhone IPA and optionally submit it to Apple's TestFlight upload service. It is **not runnable** until the repository owner configures Apple Developer credentials and an App Store Connect record for bundle ID `ca.gfh.electricalworkbench`.

GitHub Actions **variables**: `APPLE_TEAM_ID`, `APPSTORE_ISSUER_ID`, `APPSTORE_API_KEY_ID`. **Secrets**: `APPSTORE_API_PRIVATE_KEY` (full `.p8` contents), `APPSTORE_CERTIFICATES_FILE_BASE64` (Apple Distribution `.p12` base64), `APPSTORE_CERTIFICATES_PASSWORD`. Do not commit private keys or signing files to Git. The workflow is manual only; `upload_testflight=false` creates an IPA artifact without uploading to Apple. Set `upload_testflight=true` deliberately when ready and App Store Connect's TestFlight information is configured.

A signed IPA is not the same as App Store approval. Real-device test, app icon, subscription/IAP compliance, privacy policy, App Store metadata, Apple review, and OAuth callback validation are still required.

## Update flow

The iOS app fetches \`/ios-release.json\` from the HTTPS portal on startup, when foregrounded and every 30 minutes while active. When a newer version has a trusted \`https://apps.apple.com/\` URL, users can open that App Store listing. It **does not** silently install an iOS update; the App Store/TestFlight performs installation. Until the app is published, the release manifest deliberately contains no store URL.

## Release checklist

1. Verify physical iPhone and iPad display, Dynamic Type, keyboard/field scrolling, navigation, WKWebView 3D/WebGL and login.
2. Confirm the iOS app version, a real App Store listing, production icon, screenshots, privacy policy, support URL and all App Review metadata.
3. Set up Apple signing, App Store Connect and TestFlight distribution.
4. Resolve StoreKit subscriptions and OAuth handoff, test native/cloud feature permissions and revocation, and add optional cross-platform synchronization of device-local records if desired.
5. Verify real-device physical conduit bends with the exact manufacturer shoe against controlled scrap tests; review Canadian Electrical Code rules independently.

This repository contains an **iOS feature-access implementation**, not a promise of complete offline native parity with every Android/Windows function or an App Store-approved commercial release.
