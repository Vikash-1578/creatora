# Spec 04: Create (Phase D)

Status: approved by the manager. **Build after Phase C (Research) is merged**, on branch `phase-d-create`, as one PR.
Extends the existing M6 wizard (`ui/create/Wizard*`, `WizardStep`) and the M8 renderer (`domain/render`,
`data/render/Media3VideoRenderer`, thumbnail maker). Nothing is rebuilt from zero.

## App-wide rules this phase relies on
1. All UI text and all AI output use the app language (en, hi, Hinglish). Strings in all three; light and dark previews.
2. **Honesty:** AI estimates are labelled; the Video Score is "Creatora's checks", never a prediction of views; copy says
   "aim", "plan", "likely", never "will go viral". Missing data is said to be missing. Nothing is posted or deleted
   without the user's approval.
3. All AI goes through `MeteredAiService` (language, tone, profile and goal steering, usage meter, limits).
4. Several accounts: a project can be for one account (chosen on start, defaulting to Home's selection); the account's
   goal and history steer the AI.
5. Touch targets 44dp, content descriptions, theme tokens, no hard-coded strings or colours.

## Formats
`VideoFormat` already has all six. Start screen tiles, in this order:

| Format | Free | Pro | Steps used |
|---|---|---|---|
| Shorts / Reels | yes (limits below) | yes | all 7 |
| Long video | locked (Pro) | yes | all 7 |
| Cartoon story | locked (Pro) | yes | all 7, Cast is central |
| Video ad | locked (Pro) | yes | all 7; Brief asks product, audience, call to action, length 15/30/60 s |
| Post / Carousel | locked (Pro) | yes | 1 Brief, 2 Script (one text per slide), 5 Visuals (one image per slide), 6 Review, 7 Generate (slides as images). Cast and Voice are skipped |
| Avatar video | **"Coming later" for everyone** | same | not built: a disabled tile with a one-line explanation. No preview, no fake output |

A locked tile shows a lock and "Pro". Tapping it opens a sheet that says what Pro adds and has an **Upgrade** button
(Settings, Billing). Locked is never hidden.

## Plan limits: one central `PlanLimits`
All limits live in one place (`domain/account/PlanLimits.kt`, replacing the numbers now spread over `Plan`). Screens and
ViewModels read it; nothing hard-codes a number. The first commit of Phase D moves the existing limits there (no
behaviour change), then adds the new ones. **Enforcement is in the app for now; server-side enforcement (Cloud
Functions and security rules) comes later and reads the same table.** Numbers marked * are placeholders for the manager
to confirm; the others were given.

| Limit | Free | Pro |
|---|---|---|
| AI actions a month (existing meter) | 50 | 1000 * |
| Connected accounts | 1 | 5 * |
| Daily ideas | 3 | 10 * |
| Competitors | 3 | 20 * |
| Formats | Shorts / Reels only | all (Avatar: coming later) |
| Videos created a day | **1** | 10 * |
| Longest video | **30 seconds** | 10 minutes * |
| AI images a day | **5** (per day, not per month) | 50 * |
| AI video clips | **none** | a monthly allowance in credits * |
| Premium AI voices | **none** (on-device voice only) | yes, paid in credits |
| Own-voice recording, stock footage, own media | yes | yes |
| Publish limits (see spec 05) | 1 a day, 1 platform a post | more a day *, several platforms at once |

"Videos created a day" counts a project the first time its render finishes (re-rendering the same project does not count
again). The day is the phone's local calendar day (same as Home's daily ideas). Counters live in
`users/{uid}/usage/daily_{yyyyMMdd}` (`shorts`, `aiImages`) next to the monthly AI counter; the client writes them, the
server will own them later.

What the user sees when a limit is reached: the exact limit, when it resets (for daily ones), what Pro adds, and an
Upgrade button. Work already done is kept; only the limited action is blocked. Longer than 30 s on Free: Brief and Script
show "about 45 s, Free allows 30 s" (estimate: words at 2.3 per second until the narration exists, then the real
duration), with **Shorten with AI** (costs credits, shown) or Upgrade.

## AI credit cost
Every button that starts an AI action shows its cost before the tap ("Uses 1 credit"). Costs are in `PlanLimits` too
(placeholders *): text action 1, AI image 3, premium voice 2 per 1,000 characters, AI video clip 20. Credits count in the
same usage meter as Phase A (90% dialog, banner, block at 100%); a daily cap (AI images) is shown beside it. Anything
above 5 credits (and Auto mode as a whole) asks for confirmation first with the total. At the limit the real numbers still
show and the AI part says why it is blocked.

## Start: three ways in, two modes
The Create tab keeps the format tiles. After a format is chosen, the **start sheet** asks:

**How do you want to start?**
1. **From an idea:** a picker over the idea bank (Home daily ideas, Research, Trends, competitor concepts) with its
   status. Starting links the idea and marks it Planned (existing `IdeaActions.start`).
2. **My own topic:** one line of text.
3. **From a link:** paste a YouTube or any web link, then "Make my own version" (below).

**How do you want to make it?** (a main option, not hidden)
- **Step by step:** the AI suggests each step, the user approves or edits it, then moves on.
- **Auto mode, AI makes everything:** runs all steps, then shows the whole result for review.

### From a link, "make my own version"
- **YouTube link:** `videos.list` gives title, description, tags and channel (1 quota unit). The transcript is read only
  where allowed: for the user's **own** video (captions API, needs one more scope, asked only then). For other people's
  videos YouTube gives no transcript through the official API, so the AI works from title, description and tags and the
  screen says "no transcript was available, so this is based on the title and description".
- **Any other link:** a plain GET (http or https only, 10 s timeout, 1 MB cap, no login pages, no JavaScript) reads the
  page title, description and Open Graph text. If it cannot be read, say so and ask for the topic instead.
- The AI extracts a **concept** (one sentence), an **angle** and a **hook idea** in new words, steered by the user's topic,
  language and goal. The prompt forbids reusing the source's title, sentences or structure.
- **Never copies:** a guard rejects and regenerates when the concept or title shares more than 60% of its words with the
  source title, and the project keeps only the link as "inspired by" plus the extracted concept, never the source text.
  The screen shows what was used and what was not available.
- Cost: one AI action. The link fetch is free.

### The core rule: every step works both ways
Each step has a manual editor and an AI action, side by side. State per step: `EMPTY`, `AI_DRAFT` (waiting for approval),
`EDITED`, `APPROVED`. Editing an AI draft makes it `EDITED`. Reopening any earlier step never loses later steps; if an
earlier step changes, later steps that depend on it show "this may be out of date" with a one-tap regenerate (never
silently overwritten).

- **Step by step:** entering a step shows the AI draft (if the user allows AI there) or an empty editor; Next approves.
  Every step can be skipped to manual-only with "I'll do this myself".
- **Auto mode:** first a summary of the plan and the **total credit cost estimate** (text, images, voice) with Confirm. It
  then runs Brief, Script, Cast, Voice, Visuals, Review and Generate in order with a progress list. It stops cleanly at
  a plan limit, says which steps are done and which were skipped and why, and goes on with what is possible (for
  example no AI images left today: it uses stock or asks for own media for those scenes). When done it opens the
  **full result screen** (video, script, cast, voice, scenes, Video Score, title and tags) where any step can be
  reopened, edited and re-run. Nothing is published automatically.
- Both modes use the same step screens and the same stored state, so a user can switch from Auto to manual at any time.

## The 7 steps (extending the existing wizard)

### 1 Brief
Existing: title, format, brief. Add: **angle**, **length** (seconds; Free capped at 30), **tone**, **audience**,
**language** (default the app language). "Let AI set it" fills these from the account's goal and profile (and the
Research data of the account when there is some) and shows why ("Your aim is 1,000 subscribers; shorts under 30 s...").
All fields editable. The goal steers AI as everywhere else.

### 2 Script
Existing: hook, body (`structure`), call to action, AI script draft. Add: a per-line editor where each sentence has
**Rewrite** (AI), **Shorter**, **Longer**, **Change tone** (options), each one AI action with its cost shown; manual typing
always works. Pronunciation notes stay. The script is in the app language. A running word count and estimated duration
show against the plan's length limit.

### 3 Cast
Existing characters. Each has a **name, look** (text; Pro can generate a reference image) and **voice** (a voice preset).
Optional for Shorts and Ads, central for Cartoon story. AI: "Suggest a cast from the script".

### 4 Voice
- **Free:** on-device text-to-speech (existing narrator, one clip per sentence). Language and voice follow the project
  language; if the phone lacks the language the step says so and links to the system voice settings.
- **Pro:** premium AI voices (server side, through a Cloud Function so no key is in the app), a preview of 10 seconds,
  and the **credit cost** for the whole script before generating. Locked for Free with Upgrade.
- **Record my own voice** (everyone): record per sentence or the whole script (RECORD_AUDIO asked when first used), play
  back, redo. Recorded audio replaces the narration clip.
- Stored as `NarrationClip`s as today.

### 5 Visuals per scene
A scene is one or more script sentences. Each scene picks a source, all manual or AI:
- **AI image** (Free 5 a day, Pro more): prompt prefilled from the sentence and the cast looks, shows the cost, 2 options.
  Labelled "AI generated". Uses Firebase AI Logic's image model; provider and price to confirm.
- **Stock footage and images:** free licensed sources. Images from Openverse (no key). Video from Pexels and Pixabay,
  whose keys must stay on the server, so these two go through a Cloud Function; until it exists the Stock tab shows
  "coming later" for video and works for images. Every pick writes a **licence entry** (source, author, licence, link) to
  the existing licence log; "licensed footage only" (existing project flag) still applies.
- **My own media:** import from the phone (existing).
- **AI video clip:** **Pro only**, credits, behind a feature flag until a provider is confirmed. Free sees it locked.
- Per scene: duration (default from the narration), reorder, remove, "AI suggest visuals for all scenes".
- Brand kit (see extras) can add logo and colours automatically.

### 6 Review and Video Score
A full preview (existing preview) plus the **Video Score**: a list of checks, each pass / warn / fail with a plain reason and
a **Fix it** button (AI fix that costs credits shown, or jump to the step). The score (0 to 100) is the share of checks
passed, weighted, and always labelled "Creatora's checks, not a prediction of views".

| Check | How it is decided |
|---|---|
| Title | length (shown part is about 60 characters; limit 100), the main keyword near the start, not all caps |
| Thumbnail | exists; text size and contrast ratio measured from the generated image (rule based) |
| Hook | the first 2 sentences are short (under 25 words together) and make a promise, a question or a number (AI check, labelled) |
| Length | against the format's guideline and the plan's limit (guidelines, not rules) |
| Keywords | the main keyword (from the topic or a Research keyword) appears in title, hook and description |
| Licences | every footage and music item has a licence entry; AI content is marked |

The same engine powers the pre-publish checklist in Phase E (spec 05); it lives in `domain/score` and takes the project.

### 7 Generate
- **Render** with the existing Media3 renderer (aspect ratio from the format: 9:16 for Shorts, 16:9 for Long, square or 9:16
  for Posts). Progress, cancel, and the clear failure message that exists today.
- **Captions** (existing cues, safe zone per ratio), **music** (the user's own file; stock music "coming later"), **thumbnail**
  (existing thumbnail maker, with an optional AI background image and the brand kit applied).
- **Title, description, tags, chapters:** the AI fills them from the finished script (existing `publishText`), all editable.
- Then **Save** (to the Library, existing) or **Go to Publish** (Phase E). Carousel: slides are exported as images
  (gallery or share).

## Extras
- **Shorts from a long video:** pick a finished long project (or an imported video). The AI proposes 3 moments with start,
  end and a reason, from the script structure and sentence timings; when the video is the user's own and Analytics
  retention is available, the retention curve is used too and the reason says so. The user adjusts the trim handles; the
  result is a new Shorts project (9:16, captions re-timed) that enters the wizard at Review. Free: moments up to 30 s.
- **Brand kit auto-apply:** the existing brand kit (colours, fonts, logo) is applied to captions, the thumbnail and an
  optional logo watermark, per project toggle on by default.
- **Credit cost everywhere,** as above.

## Data model
Projects stay in Room (local, as today). Phase D adds nullable or defaulted columns to `Project`: `mode`
(STEP_BY_STEP or AUTO), `startedFrom` (IDEA, TOPIC, LINK), `sourceLink`, `concept`, `angle`, `tone`, `audience`,
`targetSeconds`, `language`, `stepStates` (JSON), `scenes` (JSON: text, source type, uri, licence id, duration), `creditsUsed`,
`brandApplied`. This is the next Room version with an **AutoMigration** and the schema JSON **committed** (copy the JSON the
CI prints; never destructive migration). Counters and credits go to Firestore as above. No new Firestore rules.

## Tests
Unit tests for: `PlanLimits` (every Free and Pro value, one place), format locking, 1-a-day and 30-second limits (including
the day rollover and "re-render does not count again"), AI image daily counter, estimated duration, credit costs and the
Auto mode total, step state transitions (AI_DRAFT, EDITED, APPROVED, out-of-date marking), Auto mode run (all steps,
stopping at a limit and continuing with what is possible, switch to manual), from-link extraction (YouTube with and without
a transcript, other page, unreadable page, the never-copies guard), idea start linking, per-line script actions, voice
choice per plan, stock licence logging, scene model, Video Score checks with reasons and Fix it targets, Shorts-from-long
moment proposals and trimming, brand kit apply, carousel export, locked and coming-later states, and the empty, loading and
error states. Previews in light and dark for the start sheet, each step and the result screen.

## Decisions to confirm
1. The `*` numbers above (Pro limits, credit costs).
2. Day boundary for "1 a day": the phone's local day [yes].
3. Image provider (Firebase AI Logic image model) and AI video provider (Pro, flag off until chosen).
4. Premium voice provider and the Cloud Function that serves it (Pro, credits).
5. Stock video via Pexels and Pixabay needs a Cloud Function to hold the keys; images via Openverse work now.
6. Auto mode renders the video as its last step [yes], and counts against the Free 1-a-day only when the render finishes.
