# Security review — 2026-11-25

Scope: local source tree before any GitHub publication. This is a source and automated-test review, not a professional penetration test or a live update installation test.

## Findings and controls

- **Signing:** No private Release key or password is in source. Gradle reads temporary `KONLYUT_SIGNING_*` environment variables; Release packaging refuses to proceed without them. The owner created the permanent key at `D:\Android\Signing\konlyut-release.p12` through hidden local prompts. The existing Debug certificate cannot update to the new Release certificate.
- **Update source:** The GitHub owner is deliberately unset. A build property validates the future `OWNER/Konlyut-Currency-Converter` address. No token is embedded. Automatic checks are skipped until configured and are limited to once per 24 hours after a successful check; manual mode starts no automatic check.
- **Release selection:** The client uses GitHub's latest stable Release endpoint, rejects draft/prerelease JSON, requires a semantic tag and an exact `Konlyut-vVERSION.apk` asset. It rejects absent or malformed GitHub SHA-256 digests, unexpected paths, oversized metadata and APKs over 100 MB.
- **Download:** HTTPS only, with redirects limited to GitHub and GitHubusercontent hosts. Downloads go to app-private cache, have size and SHA-256 checks, and leave no partial file after cancellation/failure. An APK with another package ID, a non-increasing `versionCode`, mismatched version name or different signer is rejected before the system installer.
- **Installation:** Uses a narrow FileProvider cache path and a temporary read-only content URI. The user explicitly starts installation and confirms it in Android. Unknown-source permission is requested only at that point. No privileged or silent installation exists.
- **Permissions:** The app explicitly declares `INTERNET`, `ACCESS_NETWORK_STATE`, and `REQUEST_INSTALL_PACKAGES`. The last one is required for the user-initiated update installer on Android 8+. The merged APK also contains AndroidX's internal `com.maxim.currency.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No broad storage, overlay, advertising ID, contacts, or location permission is requested.
- **Data:** Settings and rates stay in app-private storage. `allowBackup=false` prevents automatic cloud extraction. No advertising or analytics SDK was found in direct Gradle dependencies. The user-initiated theme export contains theme colors and name. The separately initiated settings backup contains the user's chosen currencies, amount, preferences and themes; the user chooses its destination through Android's file picker. Rates are not included.
- **Network:** Existing rate and new update requests use HTTPS. The rate source is `https://open.er-api.com/v6/latest/USD`; update requests use `https://api.github.com` and GitHub Release assets. An external attribution link in Settings opens `https://www.exchangerate-api.com`.
- **Git and ZIP:** No Git history existed before this preparation. Local builds, backups, logs, caches, keystores, passwords and `local.properties` are ignored and excluded from the review ZIP. Recheck the entire history after any future commits and before publication.

## Remaining verification

- A real GitHub repository owner and public Releases do not exist yet. API interaction and full update installation cannot be verified against a live release.
- The permanent key exists and a signed local Release APK has been built. A separate encrypted backup of the key and its password has not been verified. The public GitHub owner is still unset in this APK, so rebuild with the repository property before publishing an update-enabled first release.
- A connected Android device is needed to test unknown-source permission, FileProvider handoff and an actual Release-to-Release update.
- Debug-to-Release migration cannot preserve app-private data in place because certificates differ. Export a settings backup before uninstalling Debug and import it into Release; rates can be downloaded again.

## Checks run locally

- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` and `lintDebug` completed. **76** JVM/Robolectric tests passed. Instrumented tests were compiled but could not run because `adb devices` found no device or emulator.
- The Debug APK has package `com.maxim.currency`, version name `1.0.0` and code `8`; `apksigner verify` passed. `assembleRelease` was deliberately tried without credentials and stopped at `packageRelease` with the expected signing configuration error. No unsigned APK is approved for distribution.
- The owner then built `app/build/outputs/apk/release/app-release.apk` with the permanent key. `apksigner verify` passed for SDK 26–37 using APK Signature Scheme v2. The APK has package `com.maxim.currency`, version name `1.0.0`, code `8`, one RSA-3072 signer, and certificate SHA-256 `92d72c8460a4b1d7004c09b11475e1c6684438ed7f2472f89c9d1f49531b1d58`. The certificate is valid from 2026-11-25 to 2054-04-12. The Debug certificate has a different SHA-256 fingerprint.
- The source scan found no key, password value, GitHub token, advertising SDK or analytics SDK in the project. The resolved Debug runtime dependency list had no Firebase, AdMob, Play Services Ads or analytics package. Lint reported two non-security warnings (AGP version and KTX style).
- Local Git commits contain only the reviewed source and documentation. The source-review ZIP is generated from the latest commit, excluding local builds, caches, historical backups, logs and signing material.
