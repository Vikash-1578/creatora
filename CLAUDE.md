# CLAUDE.md

Guidance for Claude (and humans) working on **Creatora**, an all-in-one AI content creation Android app
(research, create, edit, publish, grow).

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Architecture:** MVVM, single activity (`MainActivity`), Navigation Compose for all screens
- **Build:** Gradle Kotlin DSL (`*.gradle.kts`) with a version catalog at `gradle/libs.versions.toml`
- **SDK levels:** minSdk 26, compileSdk and targetSdk = latest stable (currently 36)
- **Java/JVM target:** 17
- **Package name / applicationId:** `com.creatora.app`
- **CI:** GitHub Actions (`.github/workflows/android-build.yml`) builds a debug APK on every PR and push to `main`

## Package structure

All code lives under `app/src/main/java/com/creatora/app/`:

```
com.creatora.app
├── MainActivity.kt      # the only Activity; sets the theme and CreatoraApp()
├── ui/                  # Compose screens, ViewModels, navigation, theme
│   ├── CreatoraApp.kt   # root composable, owns the NavController
│   ├── navigation/      # NavHost and route definitions
│   ├── theme/           # Color.kt, Theme.kt, Type.kt
│   └── <feature>/       # one package per feature: <Feature>Screen.kt + <Feature>ViewModel.kt
├── data/                # repository implementations, APIs, local storage, DTOs, mappers
├── domain/              # models, repository interfaces, use cases (plain Kotlin, no Android)
└── core/                # shared utilities: result types, dispatchers, extensions
```

Dependency direction: `ui → domain ← data`, and everything may use `core`. `domain` must not depend on
Android, Compose, or `data`.

## Coding rules

- **Compose only.** No XML layouts, no Fragments, no View-based UI. XML is allowed only for resources
  (strings, launcher icon, manifest, window theme).
- **No hardcoded colours.** Use `MaterialTheme.colorScheme.*` in composables. Raw colour values may only
  be defined in `ui/theme/Color.kt` (and `res/values/colors.xml` for the icon/window background).
- **No hardcoded strings.** All user-visible text goes in `app/src/main/res/values/strings.xml` and is read
  with `stringResource(R.string.…)`.
- **Light and dark theme.** Every screen must look correct in both. Wrap screens in `CreatoraTheme` and add
  both a light and a dark `@Preview` for each screen.
- Use typography from `MaterialTheme.typography`, not ad-hoc font sizes.
- Add new dependencies through `gradle/libs.versions.toml`, never with inline version strings.
- ViewModels expose UI state as `StateFlow`; screens collect it with `collectAsStateWithLifecycle()`.
- Register every new screen as a route in `ui/navigation/CreatoraNavHost.kt`.

## Workflow rules

- **Always work on a new branch** for every change.
- **Open a pull request** for every change; CI must build the debug APK successfully.
- **Never push to `main` directly.**
- **Never commit API keys or secrets.** Keep them in `local.properties` or environment variables / GitHub
  Actions secrets (all git-ignored or outside the repo). Do not commit keystores, `.env` files or
  `google-services.json`.

## Common commands

```bash
./gradlew assembleDebug     # build the debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # install on a connected device/emulator
./gradlew test              # unit tests
./gradlew lint              # Android lint
```
