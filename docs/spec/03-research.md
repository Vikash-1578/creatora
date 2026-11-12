# Spec 03: Research (Phase C)

Status: approved by the manager. **Build only after Phase B (Home)**, on branch `phase-c-research`, as one PR.
Reuse good code from `m9-research` (closed PR #10) and fix its failing Room schema check (see "Reuse").

## App-wide rules this phase relies on
1. All UI text and all AI replies use the app language (default: the device language); strings in en, hi and Hinglish.
2. Several accounts: Research follows the account selected on Home (the same switcher). Phase C covers YouTube
   accounts; other platforms show "not available yet".
3. **Honesty:** every estimate is labelled "estimate" with how it was made. Nothing is shown as measured unless the
   API measured it. Missing data is said to be missing. **Never promise virality or guaranteed results**: copy says
   "aim", "plan", "progress", "likely", never "will go viral".
4. All AI is steered by the user's goal (set in onboarding, see spec 01 "Your goal") and creator profile.
5. Light and dark themes, theme tokens, 44dp touch targets, content descriptions.

## Tabs

### 1 My Channel (viral analysis)
- Ranks the user's own videos by **outlier score**: views compared with the channel's median for videos of the same
  age. The YouTube API gives only current totals, so "same age" is approximated by age bands (0 to 7 days, 8 to 30,
  31 to 90, over 90): a video is compared with the median of the channel's other videos in its band; a band needs at
  least 4 videos, otherwise the overall median is used and the row says "not age-adjusted". Labelled **estimate**.
- **Winners:** the top outliers. For each, the AI explains **why** it did well, using only real data: topic, title
  pattern, length, publish day and time, traffic sources and audience (Analytics `insightTrafficSourceType`,
  `viewerPercentage` by age and gender, `country`), and for hook strength the audience-retention curve of the first
  30 seconds (Analytics `audienceRetention`, `elapsedVideoTimeRatio`) plus CTR (`videoThumbnailImpressionsClickRate`).
  Thumbnail: the AI sees the thumbnail image only if image input is added to the AI layer (see decisions); until
  then the explanation says the thumbnail was not analysed.
- **"Make more like this":** generates 3 to 5 follow-up ideas in the same pattern, each with a reason citing the
  winner's numbers, and **Create** (starts a project) or **Save** (idea bank).
- **"What's not working":** the bottom outliers with the same data and a short AI explanation and a fix.
- Cost: one AI action per explained video, cached; "Make more like this" is one action.

### 2 Competitors
- **Discovery:** automatic suggestions for the user's niche from (a) `search.list` for channels with niche keywords
  from the creator profile and the goal, and (b) channels featured by the user's channel and by known competitors
  (`channelSections`). YouTube has no "similar channels" API; this is the official substitute. Plus **manual add by
  link** (reuses Phase A link detection).
- **Plan limits:** Free 3 competitors, Pro 20 (placeholders confirmed by the manager). Counted per user.
- **Competitor detail:** name, photo, subscribers, total views, video count, **upload frequency** (from upload
  dates of the last 50 uploads), **average views** (last 15 videos), **growth** (from our own snapshots, see below)
  and **top videos** (the most viewed among the last 50 uploads; labelled as such, not all-time).
- **Growth** needs history: the app stores a snapshot of each competitor's public counts at most once a day
  (Firestore `users/{uid}/competitors/{id}/snapshots/{day}`). Until there are two snapshots the card says
  "tracking since {date}", never a made-up growth number.
- **Viral competitor videos:** the competitor's outliers (same outlier rule as My Channel). For each, the AI explains
  why it went viral: concept, hook (from title and description, since retention is private), title, thumbnail (if
  image input exists), publish timing and **comment sentiment** (top 50 comments via `commentThreads.list`, summarised
  by the AI, labelled as a summary of the top comments only).
- **"Use this concept"** opens a Create project with the concept as the brief and the user does it; **"Let AI make
  it"** opens Create and runs the AI script draft (`scriptDraft`) with the concept. Both keep the user's own topic,
  language and goal; copying a video is not offered.

### 3 Trends
- What is moving now for the user's niche and region: `videos.list?chart=mostPopular` by region and by the video
  category that matches the niche; **rising searches** from Creatora's own aggregated searches (needs a Cloud
  Function; until it exists the section says "Not enough searches yet", no fake list); and **competitor outliers
  from the last 7 days** (from the cached competitor data).
- **"Upload this next":** 3 concrete suggestions from those sources and the goal, each with a reason that names its
  sources and a **potential score** labelled "AI estimate". One AI action a day, cached.
- Tap an item to open the video or turn it into an idea.

### 4 Keywords
- Search a keyword or phrase. YouTube gives **no search volume**, so the screen shows a **demand estimate**
  (Low / Medium / High, labelled "estimate from YouTube results, not real search volume") built from real signals:
  views per day of the top results, how many of the top results were uploaded in the last 90 days, and our own
  aggregated search counts when available.
- **Competition** from the real top results: how many come from channels bigger than the user's, how old they are,
  how many exact matches. **Score** = a transparent combination of demand and competition, with the formula shown
  on tap.
- **Related keywords:** the most used tags of the top results (real data from `videos.list` `snippet.tags`).
  **Questions** people ask: AI-suggested, labelled "AI suggestions".
- Not used: unofficial autocomplete endpoints (undocumented, against the terms).

### 5 Idea bank
- Every saved idea from everywhere (Home daily ideas, Research, Trends, competitor concepts) in one list, with a
  status **New / Planned / Made**, the source, and the account it is for. Filter by status and account.
- Statuses: New (saved), Planned (the user marks it, or a project was started and not finished), Made (a project
  from it was marked ready). Create starts a project from an idea and links them.
- Firestore `users/{uid}/ideas` (same collection as Home daily ideas).

## Quota, cache and honesty about cost
The Data API gives the whole project 10,000 units a day. Costs: `search.list` 100, `channels.list` 1,
`playlistItems.list` 1, `videos.list` 1, `commentThreads.list` 1, `channelSections.list` 1. A single search-heavy
user could use a large part of the day's quota, so:
- **Everything is cached in Firestore with a TTL** and the screen shows **"updated {time ago}"** and a Refresh
  button that says what it costs. Cache: `users/{uid}/researchCache/{key}` with `payload`, `cachedAt`, `ttlHours`.
  TTLs: trending 24 h, keywords 12 h, competitor details 24 h, channel analysis 24 h, discovery 7 days.
- Discovery and keyword searches are the only 100-unit calls; they run only on an explicit tap, never on open.
- If the quota is exhausted the screen shows the cached data with its age, or a clear message. Never fake items.
- **Later (Cloud Functions):** research calls move server-side with one shared cache per niche and region, so many
  users share one fetch; the client then reads the cache only. The data model above already separates "cache" from
  "result" so the move does not change the screens.

## Data model (Firestore, all under `users/{uid}`; no rule change needed)
- `competitors/{channelId}` (name, photo, counts, addedAt, source: auto or manual) and its `snapshots/{day}`.
- `ideas/{id}` (title, reason, score, source, status, accountKey, createdAt, projectId).
- `researchCache/{key}`.
- `searches/{id}` (the user's own keyword searches; aggregated later by a Cloud Function for "rising searches").

## AI usage and limits
All AI goes through `MeteredAiService` (limit, language, tone, profile and goal). One cached AI action per explained
video, per "make more like this", per competitor viral video, per keyword AI part and one a day for Trends
suggestions. At the limit the real numbers still show; AI text is replaced by the upgrade message.

## Reuse from `m9-research` and the schema check
- Reuse: keyword search UI and `VideoSearch`/`competitionFor` logic, `findOutliers`, `parseVideoId` and
  `parseChannelQuery`, the Research screen pieces, the idea bank (Saved) and swipe-file UI, `ResearchFormat`
  (compact counts), and the tests for those.
- Change: the branch called YouTube with a user-entered API key. It switches to the Phase A `YouTubeAdapter`
  (the user's own OAuth token); the user-key store is not reused.
- **Schema check:** the branch fails CI because Room schema v5 (competitors, swipe items) was never committed.
  Fix: do not carry those Room tables, because competitors and ideas now live in Firestore, so there is no Room
  migration. If any Room entity is kept, commit the schema JSON that CI prints for it.

## Decisions to confirm (recommendations in brackets)
1. Outlier score uses age bands, not exact same-age curves, because the API gives no per-video history cheaply [yes,
   labelled estimate]; exact same-age needs one Analytics call per video.
2. Keyword demand is a relative estimate, not a volume number [yes; no volume figure is shown].
3. Competitor discovery = niche search + featured channels, since there is no similar-channels API [yes].
4. Thumbnail analysis needs image input in the Firebase AI layer [propose it as a small follow-up inside this phase
   if the manager wants it; otherwise the explanation says the thumbnail was not analysed].
5. "Rising searches" needs a Cloud Function [section says "not enough searches yet" until then].
6. Free 3 / Pro 20 competitors [as stated].

## Tests
Unit tests for: outlier score and age bands (including the fallback), winners and not-working lists, competitor limits
per plan, upload frequency, average views, growth from snapshots (including "tracking since"), cache TTL and
"updated" time, quota-exhausted handling, keyword demand and competition estimates, idea status transitions,
"Use this concept" / "Let AI make it" project creation, parsing of every API response, and the empty, loading and
error states. Previews in light and dark for each tab.
