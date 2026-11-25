# Konlyut: Currency Converter

[Русская документация](README_RU.md)

Konlyut is a native Android currency converter written in Kotlin, Jetpack Compose and Material 3. It has no ads, accounts, subscriptions or analytics. The package ID remains `com.maxim.currency` to preserve the app's data across compatible updates.

## Features

- Convert between a personal, reorderable list of currencies. Results are rounded to at most two decimal places for display; calculations retain higher precision.
- Use the latest saved rates offline and see when they were last updated. Rates come from ExchangeRate-API's public endpoint.
- Convert selected or shared text, or paste an amount manually. Russian and English currency names and common abbreviations are recognized.
- Create, edit, import, export and share custom color themes. Built-in light and dark themes stay unchanged.
- Check GitHub Releases for stable updates automatically (at most once per 24 hours while the app is open) or manually. The APK is installed only after user confirmation.

The interface supports **English and Russian**. Android 8.0 (API 26) or newer is required.

## Install an APK

Download a signed release APK from the project's GitHub Releases page once the repository is published. Open it on the phone and follow Android's installer. The first public asset will be named `Konlyut-v1.0.0.apk`. No public release exists yet.

A locally built Debug APK is in `app/build/outputs/apk/debug/app-debug.apk`. Debug and Release signing certificates differ; Android will not install a Release APK over an existing Debug installation. Before removing Debug, use **Settings → Transfer settings → Export settings**. Import the file after installing Release to restore currencies, amounts, preferences and themes. Cached exchange rates are downloaded again.

## Updates

The update source is a build-time setting, `konlyut.updateRepository=OWNER/Konlyut-Currency-Converter`. No owner has been configured in this source tree. Until one is set, the app makes no update requests and its manual check explains that the repository is not configured. There is no embedded GitHub token.

The app reads the latest stable GitHub Release, expects a `Konlyut-vVERSION.apk` asset with GitHub's SHA-256 digest, then verifies its size, digest, package ID, increasing Android `versionCode`, version name and signing certificate before requesting the Android installer. The user can cancel a download or postpone an update. No background service is used.

For release setup, see [Release guide](docs/RELEASING.md) and the local hidden-prompt script in `scripts/build-release.ps1`. Live update installation still needs a published pair of signed APKs and an Android device for validation.

## Build from source

Install JDK 21, Android SDK with platform 37 and build tools 36.0.0, then set `JAVA_HOME` and `ANDROID_HOME` or create `local.properties` with `sdk.dir`. The Gradle Wrapper is included.

```powershell
cd D:\CurrencyConverter
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

The project currently uses Android Gradle Plugin 9.5.2, Kotlin 2.4.21 and Gradle Wrapper 9.6.0. Local portable tools on this development PC are described in [Development guide](docs/DEVELOPMENT.md). Release signing variables are intentionally absent from source control.

## Source layout

- `app/src/main/java/com/maxim/currency/ui/`: Compose screens, keyboard and theme editor.
- `app/src/main/java/com/maxim/currency/domain/`: conversion and theme models.
- `app/src/main/java/com/maxim/currency/data/`: rates, settings, theme files and GitHub updates.
- `app/src/main/res/`: localized app labels, manifest resources and FileProvider paths.
- `app/src/test/`: JVM and Robolectric tests.

See [security audit](SECURITY_AUDIT.md), [third-party notices](THIRD_PARTY_NOTICES.md), and [MIT license](LICENSE). This project has not been published to GitHub yet.
