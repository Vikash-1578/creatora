# Creatora roadmap

Goal: a real, working all-in-one content creation app, not a demo.

Each milestone is **one branch, one pull request and a green CI run** (build, unit tests and lint).
Milestones are stacked: each branch starts from the previous milestone's branch, and its PR says which PR
it depends on. Once the earlier PR is merged, the branch is brought up to date with `main`. Only the
reviewer merges PRs.

| # | Milestone | Status |
|---|-----------|--------|
| M1–M3 | Project setup, theme system, navigation shell | Done |
| M4–M8 | Data foundation, Library, Create flow, AI layer (user keys), Video render | Merged (#5 to #9) |
| UI polish | Home, Research, Grow, Profile, Settings, Library, Create | Merged (#14) |
| M9–M13 | Old local-first plan (Research, Publish, Grow, Home, Profile and Earn) | Superseded. PRs #10 to #13 stay open, untouched; to be mined later |
| **Phase A** | Onboarding, accounts, settings, Firebase, Creatora AI, plans and usage | In progress: spec in `docs/spec/01-onboarding-accounts-settings.md` |
| Phase B+ | Next screens, from the manager's approved specs | Not started |
| Release | Release quality (1.0.0): full Hindi pass, accessibility, R8, signing | Planned |

Direction since Phase A: **cloud-first** (Firebase Auth, Firestore, Storage, Cloud Functions, Crashlytics;
Firebase AI Logic for AI; Free and Pro plans through Google Play Billing). Each phase is one branch and one PR,
built from an approved spec in `docs/spec/`.

### Phase A checklist
- [x] Spec written
- [x] Language picker, welcome slides, Google sign-in
- [x] YouTube authorization and channel fetch, connected accounts and platform adapters
- [x] Creator profile from AI, first insights on Home
- [x] Notification categories and YouTube poll (views milestones and new comments; earnings alerts not wired yet)
- [x] Settings restructure (7 top-level pages; Connected accounts lives inside Account)
- [x] AI usage meter, dialog, banner and limit
- [x] Billing (Play Billing) with honest not-available state
- [x] English, Hindi and Hinglish strings

## Deferred: rework M9–M12

PRs #10 to #13 were closed without merging because they were built for the old local-first plan. The branches are
**kept** (do not delete or change them) and their code is reused when each screen is specced for the cloud-first
app (Firebase, Google sign-in, Firebase AI Logic):

| Branch | Was | Useful code to reuse |
|--------|-----|----------------------|
| `m9-research` | M9 Research | Keyword search, outliers, rivals (competitors), trends, idea bank, swipe file |
| `m10-publish` | M10 Publish | Publish checklist, content calendar, post reminders |
| `m11-grow` | M11 Grow | Channel snapshots, reply drafts, A/B test log |
| `m12-home` | M12 Home | Home cards (continue, next post, ideas, channel snapshot) and notices |

Before any of it is brought back:
- Their Room schema check fails in CI (schema JSON for the new versions was never committed). Fix it first, and
  re-check the migration numbers against whatever the cloud-first screens change in the local database.
- Rebuild each screen on the cloud-first foundations: Google sign-in, the platform adapters, Firebase AI Logic
  through `MeteredAiService` (no user-entered API keys), plans and usage limits, English / Hindi / Hinglish strings.
- Use the approved spec for that screen as the source of truth; the old code is a starting point, not the design.

The parked `m13-earn-profile` work (income tracker, rate card, media kit) was never pushed and is not part of this plan.

## Milestones

### M4 Data foundation
- Replace the hand-written dependency container with Hilt.
- Room database with entities Project, Idea, Asset, Character, VoicePreset, LicenceEntry, ScheduledPost,
  ChannelSnapshot. Repository interfaces in `domain/`, implementations in `data/`.
- CI runs `./gradlew assembleDebug testDebugUnitTest lintDebug`.

### M5 Library tab
- Project list: create, rename, duplicate, delete with undo.
- Filter chips: Projects, Assets, Characters, Voices, Templates.
- Import images, video and audio from the phone with the Android Photo Picker / document picker.
- Licence log entry per asset. Brand kit (logo, colours, fonts). Empty states.

### M6 Create flow
- The 6 format tiles and the 7-step guided wizard: Brief, Script, Cast, Voice, Footage policy,
  Review with cost estimate, Generate.
- Save to the Project in Room after every step; resume from Home "Continue".
- Script editor with word count, reading time, hook / structure / CTA sections and pronunciation notes.
- Voice step uses on-device Android TextToSpeech with preview and per-character pitch / speed presets.

### M7 AI layer
- Provider-agnostic `AiClient` interface with Gemini, Groq and OpenRouter implementations and automatic
  failover.
- The user enters their own API keys in Settings > AI providers. Keys are encrypted with the Android
  Keystore, never in the repo or logs.
- AI Coach chat (floating on Home), idea generation, script draft from brief, title / description /
  hashtag / chapter generation.
- Clear error and "add a key" states when no key exists.

### M8 Video render
- On-device rendering with Media3 Transformer: images / video clips + TTS narration + background music +
  burned-in captions with safe zones.
- Output 9:16, 16:9 and 1:1 MP4, with progress and cancel; save to gallery; share sheet.
- Thumbnail maker (text + image, export PNG).

### M9 Research tab
- YouTube Data API v3 with a user-entered key: keyword search, outliers (video views ÷ the channel's
  average views), competitor tracking saved in Room, idea bank and swipe file.

### M10 Publish and schedule
- Content calendar; scheduled posts with WorkManager reminders and notifications.
- Pre-publish checklist: AI label, made-for-kids, title, thumbnail, captions.
- Share to YouTube / Instagram via Android share intents.

### M11 Grow tab
- Channel stats from the Data API stored as ChannelSnapshot over time, with charts.
- AI reply drafts for pasted comments, A/B test log, "make this next" suggestion from the AI layer.

### M12 Home tab
- Composer that creates a project or asks AI Coach; today's ideas; continue card; scheduled-next card;
  channel snapshot; notifications screen.

### M13 Profile and Earn
- Income and sponsorship tracker, rate card, media kit generator as PDF.

### M14 Release quality
- Onboarding (niche, platform, goal). Hindi translation of all strings.
- Accessibility pass (TalkBack labels, contrast, 48dp targets).
- Baseline Profile, R8 / ProGuard, crash-free startup test.
- Release build workflow that signs with a keystore from GitHub Secrets. App version 1.0.0.

## Standing rules for every milestone

1. Start every milestone with the **ponytail** skill and follow it: smallest correct code, reuse platform
   features, no speculative abstractions. `CLAUDE.md` rules still win.
2. Design follows the Figma file "Creatora app UI" (file key `rwgZE07y4u4X4rpKG62Otz`, light and dark
   rows) and `CreatoraTheme` tokens. Review each new screen with **ui-ux-pro-max**
   (audit only, adapted to Compose) and fix real issues. Run **humanizer** on all new user-facing strings.
3. No fake or hardcoded sample data in the shipped UI. Where data needs a key or the user's content, show
   a real empty state with a clear action.
4. Unit tests for every ViewModel and repository added. CI must be green before reporting done.
5. Light and dark previews for every screen; all text in `strings.xml`.
6. No paid services and no API keys in the repo. Never commit secrets.
7. Don't pause between milestones. Keep fixing and pushing until the PR's CI is green, then immediately
   start the next milestone stacked on it, and tick it in the table above with its PR number. Never merge
   PRs. Stop and ask only for a real blocker that needs the owner: an API key, a signing keystore or
   GitHub secret, a decision only the owner can make, or a CI failure still unfixed after 5 attempts.
