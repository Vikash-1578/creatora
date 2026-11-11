# Spec 02: Home (Phase B)

Status: approved by the manager. **Build only after Phase A (PR #15) is merged**, on branch `phase-b-home`, as one
PR. Reuse useful code from the branch `m12-home` (closed PR #13) and, where noted, `m11-grow`.

## App-wide rules this phase relies on
1. **One language everywhere.** All UI text and all AI replies use the app language the user chose in onboarding or
   Settings; the default is the device language (English, Hindi or Hinglish). Every new string exists in
   `values/`, `values-hi/` and `values-b+hi+Latn/`. Dates and numbers follow the language (Hinglish uses the
   English (India) formats, because Android has no Hinglish date patterns).
2. **Many accounts.** A user can connect several accounts per platform. Home never assumes one channel: it follows
   the account selected in the switcher. The plan limit counts total accounts.
3. **Honesty.** No fake numbers. Every metric comes from a platform API, shows its period, and is "not available"
   (with the reason) when the platform did not return it. AI text only quotes numbers it was given. Real empty,
   loading and error states everywhere.
4. Light and dark themes, theme tokens only, 44dp touch targets, content descriptions on every icon-only button.

## Screen, top to bottom

### 1 Header
- Left: greeting "Namaste {name} 👋" (English: "Hello {name} 👋", Hindi: "नमस्ते {name} 👋", Hinglish:
  "Namaste {name} 👋") and today's date, localized.
- Right: 🔔 Notifications, ✨ Creatora AI (opens the Coach), profile photo (opens Profile). The Coach is not a
  floating button.
- `{name}` is the first word of the Google display name; if there is none, the greeting has no name.

### 2 Account switcher
- A row of chips (or a dropdown when there are more than four accounts), **grouped by platform**, showing every
  connected account with its photo and name, plus **All accounts** when there is more than one.
- Everything below follows the selected account. The selection is remembered on the phone.
- **All accounts** sums only where a sum is meaningful: subscribers/followers, views, watch time, estimated
  earnings (one currency), uploads. It never averages CTR or retention across accounts; those cards say "pick one
  account". Ideas, focus, trending and goals have their own rule per account (see below).
- Accounts of platforms that are not built yet (Instagram, Facebook, TikTok) and link-only accounts appear in the
  switcher; selecting one shows a clear "not available yet" or "link only" state instead of numbers.

### 3 Snapshot card
- Subscribers (or followers), views in the last 28 days, watch time (hours) in the last 28 days, estimated earnings.
  Each shows the change against the previous 28 days (↑ ↓ with the amount and percent). If the previous period
  has no data, the change says so.
- Estimated earnings only if the channel is monetized and the revenue scope was granted; otherwise the tile is
  not shown. Amounts are in the currency the platform returns.
- An eye icon hides the amounts (subscribers stay visible; only money is hidden). The hidden state is remembered.
- Data: YouTube Analytics API, `channel==MINE`, two date ranges (28 days and the 28 days before),
  metrics `views`, `estimatedMinutesWatched`, `subscribersGained`, `subscribersLost`, `estimatedRevenue`; the total
  subscriber count comes from the Data API `channels` call. Reuse the delta logic from `m11-grow`
  (`deltaOver`, `dailyViewGains`).

### 4 "Aaj ka focus" (today's focus)
- One concrete tip for the selected account, grounded in real metrics, with the numbers cited and the source
  metric shown (for example "Your last 5 videos got 40% fewer views than the 5 before (1,200 vs 2,000)").
- Buttons **Fix it** (starts a project or opens the relevant thing, depending on the tip) and **Later** (hides it
  until tomorrow).
- Generated once a day per account (one AI action) and cached in Firestore; shown in the app language. If metrics
  are missing it says what is missing instead of giving a tip.

### 5 Daily Ideas
- **Free 3 ideas a day, Pro 10 a day**, per selected account. Each idea has a 🔥 potential score, a one-line
  reason that cites real data (the channel's own best videos, a trending topic), and buttons **Create** (starts a
  project from it), **Save** (keeps it in the idea bank) and **Dismiss**.
- One AI action generates the day's batch (not one per idea); it is cached per account per day. Dismissed ideas do
  not come back. The 🔥 score is the AI's rating shown as "AI estimate", never as a measured number.
- Ideas are stored in Firestore (`users/{uid}/ideas`).

### 6 Continue
- Unfinished Create projects (most recent first), each with title, step and a Continue button. Local projects
  (Room). Reuse the logic from `m12-home` (`continueProject`).

### 7 Recent uploads
- The last 5 uploads of the selected account with views, CTR, average percentage viewed and a status:
  🟢 doing well, 🟡 average, 🔴 underperforming. Status compares the video with the account's own recent median
  (the same rule as Phase A's underperformers: red below 50% of the median, green above 120%, otherwise yellow);
  videos that are too new for a fair comparison say so instead of getting a colour.
- Tap a video to get an AI "why it worked / why it did not" (one AI action, cached), quoting its numbers.
- CTR and retention come from the Analytics API and show "not available" when the API does not return them.

### 8 Upcoming
- Scheduled posts for the selected account, soonest first, with platform, time and title.
- **Data:** scheduled posts live in Firestore (`users/{uid}/scheduledPosts`), not in the old local Room table.
  Phase B builds the model and the list; creating posts belongs to the Publish phase, so until that ships the
  section shows a real empty state ("Nothing scheduled yet") with an action to start a video. No fake posts.

### 9 Goals
- The user sets goals, for example "1,000 subscribers this month" or "4 uploads a week", each with a target and a
  deadline (or a repeating week/month). A goal belongs to one account or to All accounts.
- Progress bar from real data: subscribers gained since the goal was created (the subscriber count is recorded
  when the goal starts), views in the period, uploads counted from the account's upload dates. If the data is not
  available the goal says so and does not show a bar.
- Edit and delete. A celebration (a short animation and message) when a goal is reached, shown once.
- Stored in Firestore (`users/{uid}/goals`). The goal set in onboarding (Phase A, "Your goal") is the first goal
  (`primary`); this card reads, edits and adds to it.
- **30-day plan:** for the primary goal the AI builds a week-by-week plan (uploads, topics, what to test) and
  re-plans every week from real results: how far along the goal is (for example 40% of the target), what worked,
  what to change. Stored in `users/{uid}/goals/{id}/plans/{week}`. The copy says "aim", "plan" and "progress" and
  never promises virality or a guaranteed result; if there is no data yet the plan says so.

### 10 Upload streak
- Consecutive **weeks** (default) or **days** (the user picks) with at least one upload, the best streak ever, and a
  gentle reminder (a card, and the optional "daily ideas" notification) when the streak is about to break.
- Computed from real upload dates of the selected account; for All accounts, an upload on any account counts.
  Uses the last 50 uploads the API returns; the card says when the history is too short to show a long streak.

### 11 Trending in your niche
- Items for the user's niche and region: YouTube `videos.list?chart=mostPopular` for the region (and the video
  category that matches the niche when there is one) plus a niche keyword search. Tap to open the video, or turn it
  into an idea (adds it to the idea bank).
- The niche comes from the creator profile (Phase A). The region comes from the device region and can be changed
  in Settings (Creatora AI > Personalization).
- **Quota:** the Data API gives the whole project 10,000 units a day; `search.list` costs 100 units, `videos.list`
  1. So trending is fetched at most once a day per account and cached; the niche search is used only when the
  category chart has too few matches. Quota exhaustion shows a clear message, never fake items.

### 12 Quick actions
New video, Ideas, Keyword search, Thumbnail. Keyword search and Thumbnail open their screens when those phases
exist; until then they open a "coming in a later update" state, not a dead button.

### No account connected
A large **Connect your channel** card at the top (opens Settings > Account > Connect your accounts). Below it:
general trending ideas for the device region (no personal data), Continue, Quick actions. No snapshot, focus,
goals or streak cards, because they need an account.

## Data model
- Accounts: `users/{uid}/accounts/{PLATFORM_id}` (Phase A), a list keyed by platform plus account id.
- Local (DataStore): selected account key, hide-amounts flag, streak unit (weeks or days), last region.
- Firestore: `users/{uid}/goals/{id}`, `users/{uid}/ideas/{id}`, `users/{uid}/scheduledPosts/{id}`,
  `users/{uid}/insights/{accountKey}_{day}` (focus tip and "why" explanations, cached).
- Firestore rules already allow everything under `users/{uid}` for the owner; no rule change is needed.

## AI usage and limits
Each cached item costs one AI action: Aaj ka focus (1 a day per account), Daily Ideas (1 a day per account),
"why it worked" (1 per video, cached). All of them go through `MeteredAiService`, so the 90% dialog, the banner and
the 100% block apply. At the limit the cards show the upgrade message instead of AI text; the real numbers still show.

## Reuse from the closed branches
- `m12-home`: Continue card logic, next-post logic, the notices behind the bell, Home layout pieces.
- `m11-grow`: delta and daily-gain maths, channel snapshot ideas (not the local Room tables).
- Not reused: the composer (replaced by Quick actions), the Room `ChannelSnapshot` and `ScheduledPost` tables (the
  cloud-first data lives in Firestore), user-entered API keys.

## Notifications bell
Opens a list of the app's own notifications (milestones reached, new comments, plan and usage alerts, posts due
soon). Phase A's background workers post system notifications; Phase B also keeps the last 50 in a local inbox so
the bell has something real to show. An empty inbox says so.

## Decisions to confirm (recommendations in brackets)
1. Free 3 and Pro 10 ideas a day are shown per account, and each batch costs one AI action [yes].
2. Streak counts uploads on the selected account; All accounts counts any [yes].
3. Where scheduled posts live [Firestore; the list is empty until the Publish phase].
4. Trending quota: once a day per account, cached [yes]; a Cloud Function could share one fetch per region and
   niche between users later.
5. Hinglish dates use English (India) formats [yes].

## Tests
Unit tests for: account selection and "All accounts" sums, period-over-period change, hide-amounts persistence,
status colours, streak maths (weeks and days, best streak, gaps), goal progress and the reached-once rule, idea
limits per plan, caching (one AI action a day), trending quota handling, notices, and every empty, loading and
error state. Previews in light and dark for each card.
