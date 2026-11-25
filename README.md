# Konlyut: Currency Converter

[Русская версия](README_RU.md)

**Konlyut is an app I vibe-coded with AI.**

I'm not a programmer, so OpenAI Codex wrote almost all the code. My part was coming up with ideas and features, tweaking the interface, and asking it to fix one more thing. Then another. Then another.

I started building this converter for myself. I just wanted a handy app without ads, subscriptions, or other nonsense.

Eventually I decided to put it on GitHub. Maybe someone else will find it useful too.

Naturally, a vibe-coded app may have bugs. If you find one or have an idea for an improvement, suggestions are welcome.

By the way, AI wrote this text too. Apparently I don't do much around here myself.

**And the author of this whole mess is a fictional character pretending to be real.**

## What is Konlyut?

**Konlyut** is a free Android currency converter with no ads, accounts, or subscriptions.

It converts an amount into several currencies at once. Choose the currencies you need, reorder them, and enter an amount in any one of them; the others update immediately.

Rates refresh over the internet. When you're offline, the app uses the last saved rates.

You can also convert amounts selected in other apps and customize the interface colors, including sharing your own themes.

## Features

- **Instant conversion.** All selected currencies update as you type.
- **Your currency list.** Add, remove, and reorder currencies.
- **Offline use.** Convert with the last saved rates.
- **Text conversion.** Recognize amounts, symbols, codes, and currency names.
- **Appearance options.** Light, dark, and custom color themes.
- **Theme sharing.** Import and export themes as files.
- **Two languages.** Russian and English.
- **GitHub updates.** Check for new versions automatically or manually.

## Installation

Konlyut requires Android 8.0 or newer. To install it, open [GitHub Releases](https://github.com/qFumipon/Konlyut-Currency-Converter/releases), download the APK from the latest stable release, and confirm installation on your device. A local Debug build appears at `app/build/outputs/apk/debug/app-debug.apk`.

## Updates

The app is configured for `qFumipon/Konlyut-Currency-Converter`. In Settings, choose an automatic check when the app opens (no more than once every 24 hours) or checks only when you tap the button. Android always asks you to confirm installation.

Update checks use public stable GitHub Releases; no token is built into the app. Installing an actual update between two versions still needs testing on a phone. See the [release guide](docs/RELEASING.md).

## Build from source

Install JDK 21 and Android SDK platform 37 with build tools 36.0.0. Set `JAVA_HOME` and `ANDROID_HOME`, or create a local `local.properties` containing `sdk.dir`. The Gradle Wrapper is included.

```powershell
cd D:\CurrencyConverter
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

The app uses Kotlin, Jetpack Compose, and Material 3. Its `applicationId` remains `com.maxim.currency`; the first release version is `1.0.0` (`versionCode` 8). The Release signing key and passwords are not part of this repository. See [development notes](docs/DEVELOPMENT.md) and the [release guide](docs/RELEASING.md) for more detail.

## License

Konlyut's source is available under the [MIT license](LICENSE). Library licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). See the [security review](SECURITY_AUDIT.md).
