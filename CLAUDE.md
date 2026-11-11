# CLAUDE.md

Guidance for Claude (and humans) working on **Creatora**, an all-in-one AI content creation Android app
(research, create, edit, publish, grow).

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Architecture:** MVVM, single activity (`MainActivity`), Navigation Compose for all screens
- **Build:** Gradle Kotlin DSL (`*.gradle.kts`) with a version catalog at `gradle/libs.versions.toml`
- **Design system:** custom `CreatoraTheme` (colours, typography, shapes, spacing); fonts are Plus Jakarta
  Sans and Inter bundled in `res/font` (OFL licences in `licenses/`)
- **Persistence:** Jetpack DataStore (Preferences) for settings
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
│   ├── CreatoraApp.kt   # root composable: NavController, NavHost and bottom tab bar
│   ├── navigation/      # Routes, TopLevelTab (the 5 tabs), CreatoraNavHost, CreatoraBottomBar
│   ├── components/      # shared UI: ScreenHeader, TabScreen, SubScreenScaffold, list rows, icon buttons
│   ├── theme/           # Color.kt, Type.kt, Dimens.kt, Theme.kt (design tokens)
│   ├── home/ research/ create/ grow/ library/   # the five tab screens
│   ├── profile/ earn/ settings/                 # screens opened from Home's Profile button
│   └── <feature>/       # one package per feature: <Feature>Screen.kt + <Feature>ViewModel.kt
│                        # (a ViewModel only once the screen has state)
├── data/                # repository implementations, APIs, local storage (DataStore), DTOs, mappers
├── domain/              # models, repository interfaces, use cases (plain Kotlin, no Android)
└── core/                # shared utilities and AppContainer (manual dependency injection)
```

`CreatoraApplication` creates the `AppContainer`. ViewModels get dependencies through a `Factory`
that calls `appContainer()` (see `ui/ViewModelFactories.kt`).

Dependency direction: `ui → domain ← data`, and everything may use `core`. `domain` must not depend on
Android, Compose, or `data`.

## Coding rules

- **Compose only.** No XML layouts, no Fragments, no View-based UI. XML is allowed only for resources
  (strings, launcher icon, manifest, window theme).
- **No hardcoded colours.** Use `CreatoraTheme.colors.*` tokens in composables (background, surface,
  surfaceAlt, border, text, textMuted, accent, accentText, accentSoft, onAccent, navBar, inverse,
  onInverse, success, warning, danger, chartBar). Raw colour values may only be defined in
  `ui/theme/Color.kt` (and `res/values/colors.xml` for the icon/window background).
- **No Material dynamic colour.** Creatora uses its own fixed palette. The tokens are also mapped onto
  `MaterialTheme.colorScheme`, so stock Material 3 components match, but prefer the Creatora tokens.
- **No hardcoded strings.** All user-visible text goes in `app/src/main/res/values/strings.xml` and is read
  with `stringResource(R.string.…)`.
- **Light and dark theme.** Every screen must look correct in both. Wrap screens in `CreatoraTheme` and add
  both a light and a dark `@Preview` for each screen.
- **Typography:** use `CreatoraTheme.typography.*` (screenTitle 24sp, sectionTitle 17sp, cardTitle 15sp —
  Plus Jakarta Sans Bold; body 14sp, caption 12sp, tabLabel 11sp — Inter). No ad-hoc font sizes.
- **Shapes and spacing:** corners from `CreatoraTheme.shapes` (radius8/12/14/16/20/24), spacing from
  `CreatoraTheme.spacing` (xs 4, sm 8, md 12, lg 16, xl 20). No ad-hoc dp values for these.
- **Touch targets:** anything tappable is at least `CreatoraTheme.dimens.minTouchTarget` (44dp).
- **Theme setting:** the user's System / Light / Dark choice is saved in DataStore via
  `SettingsRepository` and applied in `MainActivity`. Screens must never read `isSystemInDarkTheme()`
  themselves; use `CreatoraTheme.colors`.
- Add new dependencies through `gradle/libs.versions.toml`, never with inline version strings.
- ViewModels expose UI state as `StateFlow`; screens collect it with `collectAsStateWithLifecycle()`.
- Register every new screen as a route in `ui/navigation/CreatoraNavHost.kt`.

## Navigation rules

- Bottom tabs, in order: Home, Research, Create, Grow, Library (`TopLevelTab`). Each tab is a nested
  graph, so it keeps its own back stack; switch tabs only with `navController.navigateToTab(tab)`
  (saveState / restoreState). Back on any tab returns to Home; Back on Home exits.
- Screens inside a tab go in that tab's `navigation(...)` block and keep the bottom bar. Screens outside
  every tab graph (Profile, Earn, Settings, ...) hide the bottom bar and use `SubScreenScaffold`
  (back arrow + title).
- Wrap navigation click handlers in `dropUnlessResumed { ... }` so double taps don't navigate twice.
- **Edge-to-edge:** tab screens use `TabScreen`, other screens use `SubScreenScaffold`; both pad for the
  system bars. Never hardcode status/navigation bar heights.
- Every icon-only button and every tab has a content description from `strings.xml`.

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
