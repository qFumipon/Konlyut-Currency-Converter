plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val updateRepository = providers.gradleProperty("konlyut.updateRepository").orElse("").get()
val releaseStore = providers.environmentVariable("KONLYUT_SIGNING_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("KONLYUT_SIGNING_STORE_PASSWORD").orNull
val releaseAlias = providers.environmentVariable("KONLYUT_SIGNING_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("KONLYUT_SIGNING_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(releaseStore,releaseStorePassword,releaseAlias,releaseKeyPassword).all { !it.isNullOrBlank() }
require(updateRepository.isEmpty() || Regex("[A-Za-z0-9-]+/Konlyut-Currency-Converter").matches(updateRepository)) {
    "konlyut.updateRepository must be OWNER/Konlyut-Currency-Converter"
}
android {
    namespace = "com.maxim.currency"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "com.maxim.currency"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "1.0.0"
        buildConfigField("String", "UPDATE_REPOSITORY", "\"$updateRepository\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    if (releaseSigningConfigured) {
        signingConfigs {
            create("privateRelease") {
                storeFile = file(releaseStore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("privateRelease")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo1.maven.org/maven2")
            it.systemProperty("maven.repo.local", rootProject.layout.projectDirectory.dir(".gradle/robolectric").asFile.absolutePath)
            it.systemProperty("ui.screenshot.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
        }
    }
    lint { abortOnError = true }
}
tasks.configureEach {
    if (name == "packageRelease" || name == "signReleaseBundle") doFirst {
        require(releaseSigningConfigured) {
            "Release signing is not configured. Set KONLYUT_SIGNING_STORE_FILE, STORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD."
        }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("com.google.code.gson:gson:2.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation(platform("androidx.compose:compose-bom:2026.11.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.11.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
