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
- **Cloud (from Phase A):** Firebase (Auth with Google through Credential Manager, Firestore, Crashlytics; Cloud
  Functions and Storage later). AI goes through Firebase AI Logic (Gemini) with App Check, plus a monthly usage
  limit per plan (Free / Pro, Google Play Billing). The old user-key AI providers are only a developer fallback
  behind `-PdevAiKeys=true` (`BuildConfig.DEV_AI_KEYS`). Specs live in `docs/spec/`; ROADMAP.md lists the phases.
- **Persistence:** Room (`data/local/`) for content, Jetpack DataStore (Preferences) for settings
- **Dependency injection:** Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, modules in
  `data/di/`); annotation processing with KSP
- **Tests:** JUnit 4, kotlinx-coroutines-test, Robolectric (Room and Android-dependent tests on the JVM)
- **SDK levels:** minSdk 26, compileSdk and targetSdk = latest stable (currently 36)
- **Java/JVM target:** 17
- **Package name / applicationId:** `com.creatora.app`
- **CI:** GitHub Actions (`.github/workflows/android-build.yml`) runs `assembleDebug testDebugUnitTest
  lintDebug` on every PR and push to `main`, checks Room schemas are committed and uploads the debug APK
- **Roadmap:** milestones and standing rules are in `ROADMAP.md`

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
│   ├── home/            # Home tab: HomeScreen plus one ViewModel per card (channel cards, focus, ideas, goals, trending, inbox)
│   ├── research/        # Research tab: five tabs (My channel, Competitors, Trends, Keywords, Idea bank), competitor page, one ViewModel each
│   ├── create/ grow/                            # tab screens (placeholders until their milestone)
│   ├── create/          # Create tab (format tiles) and the 7-step wizard: WizardViewModel, WizardScreen, WizardSteps
│   ├── ai/ coach/       # Settings > AI providers screen, AI error display, AI Coach chat
│   ├── render/ thumbnail/                       # Generate-step render panel, thumbnail maker
│   ├── library/         # Library tab: LibraryViewModel, LibraryScreen, items and dialogs
│   ├── brandkit/ licences/                      # screens opened from the Library
│   ├── profile/ earn/ settings/                 # screens opened from Home's Profile button
│   └── <feature>/       # one package per feature: <Feature>Screen.kt + <Feature>ViewModel.kt
│                        # (a ViewModel only once the screen has state)
├── data/
│   ├── local/           # Room: Entities.kt (tables + mappers), Daos.kt, CreatoraDatabase, RoomRepositories.kt
│   ├── ai/              # AI clients (Gemini, Groq, OpenRouter), failover, encrypted key store
│   ├── render/          # Media3 video renderer, TTS narrator, thumbnail drawing, gallery saver
│   ├── media/           # ContentResolverUserFiles: copies picked files into app storage
│   ├── settings/        # DataStore-backed SettingsRepository and BrandKitRepository
│   └── di/              # Hilt modules: database, DataStore, repository bindings
├── domain/
│   ├── model/           # plain Kotlin models (Models.kt, ThemeMode)
│   └── repository/      # repository interfaces (Repositories.kt, SettingsRepository)
└── core/                # shared utilities (empty until needed)
```

ViewModels are `@HiltViewModel` with `@Inject constructor(...)`; screens get them with `hiltViewModel()`.
Repositories are bound to their interfaces in `data/di/DataModule.kt`.

**Room:** every table has an entity in `Entities.kt` that mirrors a domain model, a DAO in `Daos.kt`
(extending `CrudDao`) and a repository in `RoomRepositories.kt`. Schemas are exported to `app/schemas/`
and must be committed. Any schema change bumps the database `version` and adds a migration (an
`AutoMigration` when Room can generate it); never use destructive migration. The first CI run after a schema
change fails and prints the new schema file: commit it exactly as printed.

**Imported files** (assets, logo, fonts) are copied into app storage by `UserFiles.import` so they stay
readable; `UserFiles.discard` deletes the copy when the item is deleted. The Library uses the Android photo
picker (images, video) and the document picker (audio, fonts); no storage permission is needed.

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
  `CreatoraTheme.spacing` (xs 4, sm 8, md 12, lg 16, xl 20, xxl 24 between sections). No ad-hoc dp values for these.
- **Tab screens** end with `tabBottomSpace()` (built into `TabScreen`; pass it as bottom `contentPadding` for lazy
  lists) so the last item scrolls clear of the raised Create button. A feature with no data yet uses
  `FeatureCard` (what it does, what is needed, one action), never a blank screen. Cards clip only their picture,
  never their text.
- **Touch targets:** anything tappable is at least `CreatoraTheme.dimens.minTouchTarget` (44dp).
- **Theme setting:** the user's System / Light / Dark choice is saved in DataStore via
  `SettingsRepository` and applied in `MainActivity`. Screens must never read `isSystemInDarkTheme()`
  themselves; use `CreatoraTheme.colors`.
- Add new dependencies through `gradle/libs.versions.toml`, never with inline version strings.
- ViewModels expose UI state as `StateFlow`; screens collect it with `collectAsStateWithLifecycle()`.
- Register every new screen as a route in `ui/navigation/CreatoraNavHost.kt`.
- **Tests:** every ViewModel and repository has unit tests in `app/src/test/`. Use fakes for repositories
  in ViewModel tests (`testutil/`), `MainDispatcherRule` for `viewModelScope`, and an in-memory Room
  database with Robolectric for repository tests.
- **Autosave:** the Create wizard edits an in-memory draft and saves it to Room after a 500 ms pause, on every
  step change and when the screen closes (`WizardViewModel.flush`). New long forms should do the same.
- **AI and API keys:** AI calls go through `AiService` (domain) and the tasks in `AiTasks`; never call a
  provider from a screen. Users enter their own keys in Settings > AI providers. Keys are encrypted with the
  Android Keystore, live in their own DataStore file (excluded from backups), travel only in request headers
  and are never logged, put in URLs, error messages or the repo. Every AI action shows an "add a key" or error
  state through `AiErrors`. New providers implement `AiClient`.
- **Video rendering** runs on the phone with Media3 Transformer (`data/render/`). Planning (sentences, caption
  timing, scenes, safe zones) is pure Kotlin in `domain/render/` and unit-tested; keep new render logic there.
  Media3 is `@UnstableApi`: annotate classes that use it with `@androidx.annotation.OptIn(UnstableApi::class)`.
  Renders go to `filesDir/renders/`, thumbnails to `cacheDir/thumbnails/`; both are shared through the
  FileProvider declared in the manifest.
- **Destructive actions** (delete) hide the item and show an Undo snackbar for 5 seconds before deleting
  for real (see `LibraryViewModel.delete`).
- **Firebase config is optional at build time:** `app/google-services.json` is never committed (CI writes it from
  the `GOOGLE_SERVICES_JSON` secret); without it the app builds and cloud features say they are not set up.
- **Honest AI:** AI never invents numbers. Real metrics are passed in and quoted; missing metrics are shown as
  "not available"; the user approves any post or delete. Every AI call goes through `MeteredAiService`.
- **One language everywhere (app-wide rule):** all UI text and all AI replies use the app language the user chose in
  onboarding or Settings; until they choose, the device language (English, Hindi or Hinglish) is used. Never
  hard-code a language for AI output: it goes through `MeteredAiService`, which adds the language instruction.
- **Many accounts (app-wide rule):** a user can connect several accounts per platform (for example 2 or 3 YouTube
  channels and several Instagram accounts). Accounts are a list keyed by platform plus account id
  (`ConnectedAccount.key`); screens that show account data take the selected account (or "All accounts") and
  never assume one channel. The plan limit counts the total number of accounts.
- **Languages:** every new user-visible string goes in `values/` (English), `values-hi/` (Hindi) and
  `values-b+hi+Latn/` (Hinglish). The in-app language replaces the device language (`core/Localization.kt`).
  Strings from before Phase A are English only until the full translation pass (lint MissingTranslation is off).
- **No fake data in the shipped UI.** When data needs a key or the user's content, show a real empty state
  with a clear action.

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
  `google-services.json`. The one exception is `app/debug.keystore`: debug signing only, public password, not a
  secret, committed so the signing SHA-1 stays stable for Firebase and Google sign-in.

## Tooling

Claude Code plugins and MCP servers are declared at project scope in `.claude/settings.json` and
`.mcp.json`, so they load for everyone working in this repo (including Claude Code on the web) once the
project is trusted. They never override the rules in this file.

- **Ponytail** keeps code minimal, but never at the cost of the rules here: theme tokens, `strings.xml`,
  accessibility (content descriptions, 44dp touch targets) and light + dark support always stay.
- **UI UX Pro Max** is mainly for web UI. For Compose screens, `CreatoraTheme` tokens and the Figma design
  win over its suggestions.
- **Humanizer** is only for user-facing text (`strings.xml`, store listing, README), never for code.
- **Task Observer** may only *propose* skill changes. Do not edit or create skills without the owner's
  approval.
- **Scrapling** (MCP server `scrapling`) runs via `uvx --from "scrapling[ai]" scrapling mcp`, so `uv`
  must be installed. The plain HTTP fetch tools work out of the box; the browser-based fetchers need a
  one-time `uvx --from "scrapling[ai]" scrapling install` to download browsers.
- Also enabled: **claude-code-setup** (automation recommendations) and **diagram-design** (diagrams).
- Not used in this project: OmniRoute, Headroom, REA.

## Common commands

```bash
./gradlew assembleDebug     # build the debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # install on a connected device/emulator
./gradlew testDebugUnitTest # unit tests
./gradlew lintDebug         # Android lint
```
