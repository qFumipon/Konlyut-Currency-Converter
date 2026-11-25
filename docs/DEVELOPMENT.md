# Development

## Local Windows environment

The original development PC uses JDK 21 at `D:\Android\jdk\jdk-21.0.13.1+2`, Android SDK at `D:\Android\Sdk`, Android Studio at `D:\Android\android-studio`, and Gradle's local cache at `D:\Android\GradleHome`. The optional, ignored `gradle-local-env.bat` sets these paths only for the wrapper process. Another machine should use its own JDK and SDK paths.

Open `D:\CurrencyConverter` in Android Studio and let it sync with the included Wrapper. The SDK path belongs in ignored `local.properties` or `ANDROID_HOME`. Do not commit machine paths, passwords or keystores.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat assembleDebugAndroidTest
```

Android instrumented tests need a connected device or emulator. `assembleDebugAndroidTest` only compiles them. A Debug APK uses the local Android debug signing key and is not the public release artifact.

## Update configuration

Set `-Pkonlyut.updateRepository=OWNER/Konlyut-Currency-Converter` on the build command after the public owner is known. Do not substitute an invented owner. The property is validated and embedded as a public repository address; it is not a secret. Without it, update checks are disabled and the manual action explains why.

The update protocol is implemented in `data/GitHubUpdates.kt`. Unit tests use synthetic GitHub JSON and local HTTP interceptors; no real release or app installation is part of automated tests.

## Data compatibility

Keep `applicationId=com.maxim.currency`, increment `versionCode` for every installable update, and migrate `SettingsCodec` when changing saved data. Settings and themes are local; neither Gradle builds nor ordinary APK updates delete app data. A new Release certificate cannot update an existing Debug installation. See `RELEASING.md` before first public install.
