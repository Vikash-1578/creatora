# Spec 05: Publish and Schedule (Phase E)

Status: approved by the manager. **Build after Phase D (Create)**, on branch `phase-e-publish`, as one PR.
Reuse good code from `m10-publish` (closed PR #11) and fix its failing Room schema check (see "Reuse").

## App-wide rules this phase relies on
1. App language everywhere (en, hi, Hinglish), light and dark, theme tokens, 44dp targets, content descriptions.
2. **Honesty:** nothing is posted without the user's tap on a final Publish or Schedule button that names the
   destinations. Estimates are labelled (best time, scores). Failures show the platform's real reason. Copy never
   promises reach, virality or results.
3. All AI through `MeteredAiService`; every AI button shows its credit cost first (spec 04).
4. Limits are read from the central `PlanLimits` (spec 04); enforcement is in the app now and moves server-side later.

## Platform reality (read first, it shapes the scope)
| Platform | How Creatora can post | What the user must know |
|---|---|---|
| **YouTube** | Data API `videos.insert` (resumable upload), `thumbnails.set`, `status.publishAt` for scheduling, scope `youtube.upload` | Until Google audits Creatora's API project, **uploaded videos are locked to private**. Creatora says so before upload and after it, and offers **"Open in YouTube to make public"**. An upload costs 1,600 quota units of the project's 10,000 a day, so the project needs a quota extension (see "Approvals") before many users post. |
| **Instagram** | Graph API content publishing, **only for Professional (Business or Creator) accounts** linked to a Facebook Page; needs Meta app review; the video must be reachable by URL (Firebase Storage signed URL) | Personal accounts cannot be posted to by API: they use the share sheet. |
| **Facebook Pages** | Pages API (`pages_manage_posts`), after Meta app review | Pages only, not personal profiles. |
| **TikTok** | Content Posting API after TikTok's audit; before it, posts are private only | Until approved: share sheet. |
| Moj, Josh, ShareChat, Snapchat and anything without a public posting API | **Share sheet**: the finished video goes to the app via Android's share intent (FileProvider) and the caption is copied to the clipboard | Creatora cannot see the result; the post stays "shared, not tracked". |

**Scope of Phase E:** YouTube is real (upload, thumbnail, native scheduling, status). Instagram, Facebook and TikTok use
the share sheet now, behind the same `PlatformPublisher` interface as the Phase A adapters, so their API publishers drop in
when approved. This is stated in the app ("Posts to Instagram go through the share sheet for now") and never hidden.

## 1 Where: destinations
- On the Publish screen the **Connected accounts** list (Phase A, several per platform) is shown as **multi-select chips**:
  platform icon plus account name. Link-only and coming-soon platforms are selectable and marked "share sheet".
- **Free: one destination per post.** Selecting a second shows the limit and Upgrade; Pro selects several.
- **Auto-adapt per destination** (a `PlatformSpecs` table, values checked against each platform's docs at build time, with
  a "last checked" date; they change): aspect ratio (9:16, 16:9, 1:1), maximum length, caption zone safe area, cover and
  thumbnail size and format, title/caption/description/hashtag/tag limits.
  - **Aspect ratio:** crop to the target with a focus point (default centre, draggable) or **blurred fill** (the picture
    letterboxed over a blurred copy). Rendered once per distinct target by the existing Media3 renderer.
  - **Length:** over the maximum: the user chooses **trim** (pick the part) or **split into parts** (even or at sentence
    breaks), with a warning that says the limit and the platform.
  - **Safe zones:** captions move into each platform's safe zone (extends `captionZone()`).
  - Text limits: counters show used/limit and the app trims suggestions to fit; it never cuts the user's own text silently.
- A **per-platform preview** (phone frame with the platform's overlay areas marked) is shown before publishing, for each
  selected destination.

## 2 Details
One screen with a card per field, **all fully editable**, each with **Regenerate** (one AI action, cost shown):
- **Title:** 5 options with a score and the reason (Creatora's check, labelled), pick or edit.
- **Description** (the first two lines are the ones people see; they are checked), **hashtags**, **tags**, **chapters**.
- **Thumbnail:** 2 or 3 options from the thumbnail maker (brand kit applied), pick or upload.
- **Per-platform captions** (limits per platform).
- The AI is steered by the user's **goal**, the **account's history** (its titles and what did best, from the Phase C
  outlier data when it is cached) and, when present, the Research keyword the project was made for.
- The fields start from what Create already produced (title, description, tags, chapters), so nothing is typed twice.

## 3 Pre-publish checklist (Video Score)
Uses the same engine as Create's Video Score (`domain/score`, spec 04) plus publish-only items. Each failing item has a
reason and **Fix it** (AI fix with its cost shown, or jump to the field):
title length and keyword; thumbnail text readability (measured contrast and size); first 2 lines of the description; tags
present and not stuffed; **AI-content disclosure** (YouTube `status.containsSyntheticMedia`; the user answers, the AI
pre-suggests "yes" when AI images, voices or clips were used); **Made for kids** (`status.selfDeclaredMadeForKids`, must
be answered, never defaulted); **music and footage licences** (every item has a licence entry). Publishing is allowed with
warnings, but never with the two questions unanswered or with no rendered video. Labelled "Creatora's checks".

## 4 When
- **Now** or **Schedule** (date and time picker, local time zone shown).
- **Best time to post**, shown with its reason. YouTube's API does not expose "when your viewers are online", so Creatora
  uses what it does have: the account's own **daily views by weekday** (Analytics `day` dimension, last 90 days) and the
  **upload hour of the account's own videos against their views**. The suggestion reads, for example, "Your videos
  published on Saturday evenings got the most views in the last 90 days (based on 14 videos)". With fewer than 8 videos
  or no Analytics access it falls back to **niche defaults, labelled "general guidance, not from your data"**. It is a
  suggestion; the user picks the time.
- Scheduling by platform: **YouTube:** the video is uploaded now with `publishAt` (YouTube publishes at the time, even with
  the app closed). **Share-sheet platforms:** a reminder notification at the time opens the share sheet (m10's reminder
  worker). **API platforms later:** a Cloud Function with Cloud Tasks posts at the time.

## 5 Content calendar (all plans)
- Month and week views. Each day shows scheduled posts (platform icon, account, title, time) and published ones.
  Published items come from our own posts and from the account's recent uploads (Phase B data).
- **Drag to reschedule** (long-press, drag to another day, then pick the time or keep it). For YouTube posts this calls
  `videos.update` (`publishAt`); for reminders it moves the reminder. A post already live cannot be dragged.
- **Empty days** show a quiet "Suggest an idea" chip: one AI action (cost shown) that proposes an idea for that day from the
  idea bank, Research and the goal, with Save and Create. Never filled automatically.
- Tap a post to open its details, status, and edit or cancel (cancel always asks first).
- Data: `users/{uid}/scheduledPosts` (already read by Home's Upcoming): one document per post and destination.

## 6 After posting
Status line per destination: **Queued → Uploading (percent) → Processing → Live**, or **Failed** (platform's reason, a
**Retry**), or **Uploaded as private, waiting for Google's approval** (YouTube before the audit) with the **Open in
YouTube to make public** button. Share-sheet posts show "Shared, not tracked".
- Uploads run in a WorkManager foreground job (data sync) with resumable upload, so they survive leaving the app or a
  process restart.
- **First 24 hours:** a live views number per post, polled with `videos.list` (1 quota unit) every 2 hours for 24 hours
  and shown as "views so far, as of {time}". No chart is drawn from sparse points.
- **Notification** when a post goes Live or Fails (new category in the existing notification settings, on by default
  for failures, user-controlled).

## Plan limits (in `PlanLimits`, spec 04)
| Limit | Free | Pro |
|---|---|---|
| Publishes a day | **1** | 10 * |
| Platforms per post | **1** | several at once (up to 5 *) |
| Content calendar | yes | yes |
Counted when a publish is started by the user (a failed upload that is retried does not count twice). Day is the phone's
local day. Enforced in the app now, server-side later. `*` placeholders for the manager to confirm.

## Data model
- `users/{uid}/scheduledPosts/{id}`: `postId` (groups destinations), `projectId` (local), `accountKey`, `platform`, `title`,
  `scheduledAt`, `publishedAt`, `status` (queued, scheduled, uploading, processing, live, failed, awaiting_public,
  shared), `error`, `remoteId`, `remoteUrl`, `privacy`, `views24h` (value, asOf). Home's `UpcomingPost` keeps reading
  `accountKey`, `platform`, `title`, `scheduledAt`.
- Publish details per project (title, description, hashtags, tags, chapters, chosen thumbnail, per-platform captions,
  AI-content and made-for-kids answers) stay on `Project` in Room.
- No new Firestore rules (owner-only already covers it). Files for the API publishers later go to Firebase Storage under
  `users/{uid}/uploads/` with owner-only rules, added in that later phase.

## Reuse from `m10-publish` and the schema check
- **Reuse:** the checklist (`domain/publish/Publish.kt`, extended with reasons and Fix it and merged with the Video Score
  engine), `postText`, the reminder worker (`data/schedule/Reminders.kt`, now for share-sheet platforms), the calendar and
  schedule dialog (`ui/calendar`, `ui/publish`) rebuilt on Firestore and the new design, and the tests for them.
- **Change:** scheduled posts move from Room to Firestore; the old Room `ScheduledPost` table is left in place (no
  destructive migration) and simply no longer written.
- **Schema check:** the branch fails CI because its Room version 5 (new `Project` publish columns) was never committed.
  Fix: take the next free Room version after Phase D, use an **AutoMigration**, and commit the schema JSON the CI prints.

## Approvals the manager should start now (they take weeks)
1. **Google:** OAuth verification for the sensitive `youtube.upload` scope, then the **YouTube API Services audit and a quota
   extension** (an upload costs 1,600 units; the default is 10,000 a day for the whole project). Until then uploads stay private.
2. **Meta:** app review for Instagram content publishing and Pages posting (and business verification).
3. **TikTok:** Content Posting API audit.
The app works without them (share sheet, private YouTube uploads) and says so.

## Tests
Unit tests for: platform specs table and per-destination adaptation (aspect, length trim and split, safe zone, text limits),
destination selection with the Free one-platform limit, daily publish limit and rollover, AI field generation and
Regenerate per field, title scoring, the checklist items with reasons and Fix it targets (including the two required
answers), best-time calculation (enough data, too little data and the labelled fallback), scheduling per platform (YouTube
`publishAt` request, reminder for share sheet), calendar month and week building, drag reschedule (allowed and refused),
empty-day idea suggestion, status transitions (Queued to Live, Failed with retry, private awaiting approval), the 24 hour
views polling, notifications, parsing of every API response, the private-upload notice and the empty, loading and error
states. Previews in light and dark for each screen.

## Decisions to confirm
1. The `*` numbers (Pro publishes a day, platforms at once).
2. Share sheet for Instagram, Facebook and TikTok in this phase, API publishers when approved [yes].
3. YouTube best time from weekday views and own upload hours, since the API has no viewer-online data [yes].
4. Start the Google, Meta and TikTok approvals now [recommended].
