# Android releases

## 1.2.2 (versionCode 5) - 2026-10-21

- Fix the autofill authentication result so selecting a locked credential can fill after biometric verification.
- Decrypt the vault in the background and retain the unlock operation across activity recreation.
- Fall back to a retryable master-password dialog when biometrics are unavailable or fail.
- Recognize native, HTML and Arabic credential metadata, prioritize focused fields, and enable compatible inline keyboard suggestions.
- Offer an account chooser for unmatched apps and email-only screens. Use consistent domain matching in both the service and unlock screen.
- Bound hierarchy traversal, honor cancellation, isolate request pending intents, and guard service failures.
- Add 14 credential-detection and site-matching regression tests and a reproducible side-by-side diagnostic build.
- Require unit tests and lint in Android CI. CI artifacts no longer overwrite an existing GitHub Release.

Validation: 14 unit tests passed; standard and diagnostic APKs built and signature/version checks passed; lint completed with 0 errors and 61 warnings.

### Downloads and installation

- `ReWord.apk`: standard package `com.reword.app`, version 1.2.2.
- `ReWord-v1.2.2-autofill-test.apk`: independent package `com.reword.app.autofilltest`, labeled **ReWord Autofill Test**. Sync its vault and select it as the autofill provider before testing.
- Both APKs are debug-signed. The original 1.2.1 APK uses a different signing certificate; an in-place update from that APK requires its original signing key, which is unavailable here. The diagnostic APK installs alongside it and keeps the original app's data intact.
- Device validation remains pending on the user's Poco F5 Pro running global HyperOS. Build/unit-test/lint checks cannot verify biometric hardware or app-specific field exposure.
