# Creatora roadmap

Goal: a real, working all-in-one content creation app, not a demo.

Each milestone is **one branch, one pull request and a green CI run** (build, unit tests and lint).
Milestones are stacked: each branch starts from the previous milestone's branch, and its PR says which PR
it depends on. Once the earlier PR is merged, the branch is brought up to date with `main`. Only the
reviewer merges PRs.

| # | Milestone | Status |
|---|-----------|--------|
| M1–M3 | Project setup, theme system, navigation shell | Done |
| M4 | Data foundation | PR #5, CI green |
| M5 | Library tab | PR #6 (stacked on #5) |
| M6 | Create flow | PR #7 (stacked on #6) |
| M7 | AI layer | Planned |
| M8 | Video render | Planned |
| M9 | Research tab | Planned |
| M10 | Publish and schedule | Planned |
| M11 | Grow tab | Planned |
| M12 | Home tab | Planned |
| M13 | Profile and Earn | Planned |
| M14 | Release quality (1.0.0) | Planned |

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
