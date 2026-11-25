# Release process (publication pending)

1. The build is configured for `qFumipon/Konlyut-Currency-Converter` in `gradle.properties`. The repository must be public for unauthenticated update checks. Do not change its visibility or publish a release until the project owner authorizes it.
2. Set `versionName` in `app/build.gradle.kts` using semantic versions: `1.0.0`, `1.0.1`, `1.1.0`, `2.0.0`. Always increase Android `versionCode` above every previously distributed APK. The first public version is `1.0.0`, code **8**.
3. Reuse the one long-lived private Release signing key outside this repository. The owner created `D:\Android\Signing\konlyut-release.p12` on 2026-11-25. On this Windows PC, run `powershell -ExecutionPolicy Bypass -File .\scripts\build-release.ps1` from the project folder. The script reuses that file, asks for the password through a hidden local prompt, and builds the signed APK. It never replaces an existing keystore. Do not send the password in chat or put it in a script.
4. Back up the keystore to encrypted offline storage and keep the password separately in a password manager. Record the **public** certificate SHA-256 fingerprint (`keytool -list -v ...`) for future verification. Losing the key prevents normal upgrades. Never replace it casually.
5. The script sets `KONLYUT_SIGNING_STORE_FILE`, `KONLYUT_SIGNING_STORE_PASSWORD`, `KONLYUT_SIGNING_KEY_ALIAS`, and `KONLYUT_SIGNING_KEY_PASSWORD` only in its temporary process environment, then clears them. The Gradle file reads them but never stores them in source. The Release packaging task fails if any is absent.
6. Run tests and lint, then rebuild with `scripts/build-release.ps1` and verify the signed APK with `apksigner verify --verbose --print-certs` and `aapt2 dump badging`. Compare its certificate SHA-256 with `92d72c8460a4b1d7004c09b11475e1c6684438ed7f2472f89c9d1f49531b1d58`. Rename the verified output to `Konlyut-v1.0.0.apk` only when preparing an authorized first release.
7. Create a stable GitHub Release tagged `v1.0.0`, titled `Konlyut v1.0.0`, attach **only** the verified signed APK, and fill in `docs/RELEASE_NOTES_TEMPLATE.md`. The client reads GitHub's `digest: sha256:...` for that exact asset. Confirm the digest appears in the public Release API before announcing an update.
8. On an Android device with a Release-signed `1.0.0` installed, publish a higher-code test release in a controlled environment and exercise the complete check, download, certificate verification and system installer flow. This cannot be claimed before publication and a device test.

## Security gates

- Never add a keystore, password, `.env`, Gradle user home, or local settings to Git or the source ZIP.
- Check `git ls-files` and the complete `git log` for secrets before first publication. Removing a secret from the latest file does not erase it from history.
- Do not use an unsigned APK or the debug certificate as a public release.
- Do not change the package ID, signing certificate or versionCode sequence after launch.
