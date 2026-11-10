# Creatora

Creatora is an all-in-one AI content creation app for Android: research, create, edit, publish and grow
your content in one place.

Built with Kotlin, Jetpack Compose and Material 3 (single activity, Navigation Compose, MVVM).
See [CLAUDE.md](CLAUDE.md) for the tech stack, package structure and coding rules.

## Build

Requirements: JDK 17 and the Android SDK (install Android Studio, or set `ANDROID_HOME` /
`sdk.dir` in `local.properties`).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To install it on a connected
phone or emulator:

```bash
./gradlew installDebug
```

Or open the project folder in Android Studio and press **Run**.

## Download a build from GitHub Actions

Every pull request and push to `main` builds a debug APK:

1. Open the **Actions** tab of this repository and pick the latest **Android debug build** run.
2. Scroll to **Artifacts** and download **creatora-debug-apk** (a zip file).
3. Unzip it on your phone and open `app-debug.apk` (allow "Install unknown apps" when asked).
