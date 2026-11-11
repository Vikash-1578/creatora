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
│   ├── CreatoraApp.kt   # root composable, owns the NavController
│   ├── navigation/      # NavHost and route definitions
│   ├── theme/           # Color.kt, Type.kt, Dimens.kt, Theme.kt (design tokens)
│   ├── themepreview/    # demo screen for checking the theme
│   └── <feature>/       # one package per feature: <Feature>Screen.kt + <Feature>ViewModel.kt
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

## Workflow rules

- **Always work on a new branch** for every change.
- **Open a pull request** for every change; CI must build the debug APK successfully.
- **Never push to `main` directly.**
- **Never commit API keys or secrets.** Keep them in `local.properties` or environment variables / GitHub
  Actions secrets (all git-ignored or outside the repo). Do not commit keystores, `.env` files or
  `google-services.json`.

## Tooling

Claude Code plugins and MCP servers are declared at project scope in `.claude/settings.json` and
`.mcp.json`, so they load for everyone working in this repo (including Claude Code on the web) once the
project is trusted. They never override the rules in this file.

- **Ponytail** keeps code minimal, but never at the cost of the rules here: theme tokens, `strings.xml`,
  accessibility (content descriptions, 44dp touch targets) and light + dark support always stay.
- **Hallmark, Impeccable and UI UX Pro Max** are mainly for web UI. For Compose screens, `CreatoraTheme`
  tokens and the Figma design win over their suggestions.
- **Humanizer** is only for user-facing text (`strings.xml`, store listing, README), never for code.
- **Task Observer** may only *propose* skill changes. Do not edit or create skills without the owner's
  approval.
- **claude-mem** stores memory in `~/.claude-mem` on the machine it runs on. On Claude Code on the web
  that is the session's temporary container, so memory does **not** survive between web sessions (only
  its opt-in, paid Cloud Sync would). Locally it persists. Telemetry is turned off for this project via
  `CLAUDE_MEM_TELEMETRY=0` and `CLAUDE_MEM_TELEMETRY_ERRORS=0` in `.claude/settings.json` (`env`); to
  also turn it off for your machine everywhere, run `npx claude-mem telemetry disable`. On a brand-new
  machine its first SessionStart hook may fail once (its launcher is not installed yet); it works from
  the next session.
- **Scrapling** (MCP server `scrapling`) runs via `uvx --from "scrapling[ai]" scrapling mcp`, so `uv`
  must be installed. The plain HTTP fetch tools work out of the box; the browser-based fetchers need a
  one-time `uvx --from "scrapling[ai]" scrapling install` to download browsers.
- Also enabled: **claude-code-setup** (automation recommendations) and **diagram-design** (diagrams).
- Not used in this project: OmniRoute, Headroom, REA.

## Common commands

```bash
./gradlew assembleDebug     # build the debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # install on a connected device/emulator
./gradlew test              # unit tests
./gradlew lint              # Android lint
```
