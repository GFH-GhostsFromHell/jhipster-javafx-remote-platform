# Electrical Workbench — iOS (iPhone + iPad)

A native SwiftUI client for the Electrical Workbench platform, paired with the existing HTTPS portal and web calculators. The iOS app includes an **offline quick conduit bender** (offset, rolling offset, and verified-deduct 90-degree stub), measurement-first guidance, a native tool launcher, and a WebKit client for advanced calculations, 3D bend previews, account management, and shared subscription-gated web features.

## Build

Requires a Mac with Xcode 15.3 or newer (use current stable Xcode), and [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```sh
brew install xcodegen
cd ios-client
xcodegen generate
open ElectricalWorkbench.xcodeproj
```

Choose the **ElectricalWorkbench** scheme and a simulator, or set your Apple developer team and a unique bundle identifier under Signing & Capabilities to run on a real iPhone. GitHub Actions builds the **unsigned Simulator .app** on macOS when iOS files change. Download the artifact from the workflow's **Actions** page; it is **not** installable on an ordinary iPhone.

## Cloud integration

- Existing web service: `https://remote-javafx-portal.onrender.com`
- Account sign-in and email registration use the existing portal pages; WebKit keeps the same cookie store for embedded tools.
- Cloud calculators, the advanced conduit lab with rotatable 3D preview, and paid-feature authorization remain server-controlled.
- The native quick bender works without a connection but **does not synchronize a measured shoe calibration** with Android; enter your verified deduct.
- A full native parity port of every Android calculator, deep-link OAuth support, in-app billing, and production subscriptions are future work.

## Before an App Store or TestFlight release

1. Enrol in the Apple Developer Program, select an Apple Development/Distribution team, and configure app signing, provisioning, and App Store Connect. Unsigned CI artifacts are simulator-only.
2. Add a production app icon, privacy policy, and App Store metadata. Check Apple's current review requirements (including **4.2** minimum native functionality and **3.1** digital subscription / In-App Purchase rules); this client has a native field calculator and navigation but App Review approval is not guaranteed.
3. Verify Google/OAuth and Apple Sign In flows if offered: third-party Google login may not work inside WKWebView. The username/password portal sign-in is the supported first path. Complete StoreKit subscriptions or an applicable Apple billing exception before exposing web checkout inside an App Store binary.
4. Test on physical iPhone/iPad for text scaling, soft-keyboard overlap, on-device authentication, 3D WebGL performance, tool-specific bend marks, offline states, and existing-account permissions.

The build workflow intentionally does not upload or publish anything to App Store Connect without a developer-owned signing configuration.
