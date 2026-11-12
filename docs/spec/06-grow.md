# Spec 06: Grow, plus Studio-style features (Phase F)

Status: approved by the manager. **Build after Phase E (Publish)**, on branch `phase-f-grow`, as one PR (split into two
PRs only if the manager asks: Grow first, Studio-style features second).
Reuse good code from `m11-grow` (closed PR #12) and fix its failing Room schema check (see "Reuse").

## App-wide rules this phase relies on
1. App language everywhere (en, hi, Hinglish), light and dark, theme tokens, 44dp targets, content descriptions.
2. **Honesty:** every number is from the platform's API or labelled. Estimates say "estimate" and how they were made.
   Missing data is said to be missing. Nothing in Creatora posts, edits or deletes without the user's approval of that
   exact action. Copy never promises reach, virality or earnings.
3. All AI through `MeteredAiService` (app language, tone, goal steering); every AI button shows its credit cost first
   (spec 04). Limits come from the central `PlanLimits` (spec 04), enforced in the app now and on the server later.
4. Several accounts: every screen follows the account chosen on Home. Phase F covers YouTube; other platforms show
   "not available yet" (Instagram and Facebook comments can be added later through their official APIs).
5. **Design:** Creatora's own system (tokens, cards, chips, icons, spacing). The Studio-style features offer the same
   *functions* as YouTube Studio but must **not look like a copy of it**: no table with columns and sort arrows, no Studio
   tab strips, no Studio wording where a plainer one exists. They use card lists, large thumbnails, our chips and bottom
   sheets and our colours.

## What the YouTube API can and cannot do (read first)
| Wanted | Reality | Scope here |
|---|---|---|
| Views, watch time, subscribers, CTR, retention, audience, traffic, revenue | YouTube Analytics API (own channel): yes | real data |
| "When your viewers are online" | **No API.** Only daily totals exist | weekday pattern only, labelled |
| Reply to a comment, hold or publish a comment in the review queue | Data API: yes (needs the `youtube.force-ssl` scope) | real |
| Comments on/off for new uploads, "hold potentially inappropriate comments" channel setting | **No API** for these defaults | link to Studio, no fake toggle |
| YouTube's own **blocked words** list | **No API** | Creatora has its own filter (below), said plainly |
| Edit a video's title, description, tags, thumbnail, visibility | `videos.update`, `thumbnails.set`: yes (`youtube.force-ssl`) | real |
| Channel country, made-for-kids default | `channels.update` (branding and status): yes | real |
| Default upload settings (category, visibility, tags) | **Not stored by YouTube's API** | kept in Creatora, applied to Creatora's own uploads |
| Partner Program status, strikes, copyright claims, Content ID | **No API** (a rejected upload's reason is the only policy signal) | link to Studio |
| Team members, permissions, brand partners | **No API** | "Manage in YouTube Studio" link |
| Draft (unfinished) uploads | Not exposed; only processed uploads | Creatora's own project drafts are shown instead |

**New scope:** `youtube.force-ssl` is added through incremental authorization the first time the user uses a feature that
needs it (reply, edit, test, channel defaults), with a plain explanation before the Google screen. It is a sensitive scope:
it joins the Google verification and API audit already listed in spec 05 ("Approvals"). Until verified, the app works in
Google's test mode for the allow-listed testers and says so.

**Quota** (Data API, 10,000 units a day for the whole project): list calls cost 1; `comments.insert`, `videos.update`,
`thumbnails.set`, `channels.update`, `setModerationStatus` cost 50 each. So 50 approved replies cost 2,500 units. Every
screen caches (Firestore `users/{uid}/cache`, with time to live and an "updated" time, as in Phase C), shows what an action
costs when it is not trivial, and shows the cache with its age when the quota is gone. The Analytics API has its own, smaller
quota: its limits are checked against Google's docs at build time and its calls are batched and cached the same way.

## 1 Analytics (all plans)
Creatora's own layout: a header with the account and period chips (**7 / 28 / 90 days**), then a vertical stack of cards.
- **Overview cards:** views, watch time, subscribers (gained minus lost), CTR, average view duration and percentage viewed,
  each with the change against the previous period (Phase B `Change`), drawn as a small bar chart with the app's `chartBar`
  token (Compose canvas, no chart library; `GrowCharts` from m11 is the starting point).
- **Per-video report card:** pick a video (the account's recent uploads, searchable) to see its views, watch time, CTR,
  average % viewed, traffic sources, the **audience retention curve** (`audienceRetention`, one call for one video) with the
  first 30 seconds marked, and how it compares with the account's median for similar age (the Phase C outlier score,
  labelled estimate).
- **Audience:** age and gender (`viewerPercentage`), top countries, devices and operating systems. **Online times:** not
  available from YouTube, so the card shows views by weekday over the period and says "YouTube does not share the hours
  your viewers are online".
- **Traffic sources:** `insightTrafficSourceType` as a ranked list with bars.
- **One-line AI explanation under each chart.** One button, "Explain these charts" (one AI action, cost shown), writes one line
  per card from the real numbers only (quoted), cached for 24 hours. Without it the cards still show everything.
- Data is cached 6 hours (overview) and 24 hours (per video and audience); Refresh says what it costs.

## 2 Comments inbox
- **Inbox** of the account's comments (`commentThreads.list`, newest first, 1 unit a page), across platforms where an API
  exists (YouTube now). Filters: kind, video, replied or not. Each row: commenter, text, video, time, the AI's kind, and a
  draft when there is one.
- **AI classification** into: **question, praise, video request or idea, work or collab request, spam or abuse** (and
  "other"). One AI action classifies a batch of up to 50 comments (cost shown); results are stored so a comment is never
  classified twice. A short rule-based pre-filter (links, repeated text, banned words) marks obvious spam without AI.
- **Reply rules (the user sets them):** how many comments the AI may **draft** per video and per day, and **which kinds**.
  Default: only the useful kinds (video or topic requests, questions, work or collab requests); praise, spam and abuse are
  skipped (praise can be switched on). Limits per plan in `PlanLimits` (Free and Pro placeholders *). Drafts are made when the
  user taps "Draft replies" (one AI action per batch, cost shown); never in the background.
- **Drafts are in the user's tone** (the account's profile tone plus up to 10 of the user's own earlier replies as style
  examples, fetched from the API) and the comment's language.
- **Nothing posts without approval.** Each draft can be edited, **approved** (posts one reply) or dismissed; **Approve all
  selected** posts the ticked ones, one by one, with a progress list, the quota cost shown first, and a stop button. A failed
  reply shows YouTube's reason and a retry. Comments the user did not select are never touched.
- **Comments to video ideas:** any comment can be saved as an idea in one tap (source "comments", the comment as the
  reason); video-request comments offer it first. They appear in the Phase C idea bank.
- **Moderation queue** (where the API allows): comments waiting for review can be **published** or **rejected** with
  `setModerationStatus` (after the user's tap). The channel's own "hold for review" switch is not in the API, so a note
  links to Studio for it.
- **Creatora's blocked words and links filter:** the user's own list (words, phrases, "any link"); matching comments are
  hidden into a "Filtered" tab of Creatora's inbox and are never deleted or rejected on YouTube unless the user approves.
  The screen says: "YouTube's own blocked-words list can only be changed in YouTube Studio; this filter works inside
  Creatora."

## 3 A/B test for titles and thumbnails (**Pro only**)
Free sees the card locked with an Upgrade button. A test has: the video, what is tested (title or thumbnail), variant A
(current) and B (the user's or AI-suggested, cost shown), block length (3, 5 or 7 days) and number of rounds (at least 2:
A, B, A, B), so weekday effects spread over both variants.
- Creatora switches the live title or thumbnail at each block change (`videos.update` or `thumbnails.set`, 50 units each, it
  works through a scheduled job and tells the user at each switch by notification). The user approves the start and sees
  exactly what will change when. Stopping restores variant A unless the user picks B.
- Results use real Analytics numbers per block: impressions, CTR (`videoThumbnailImpressionsClickRate`), views and average %
  viewed. The screen shows both variants' numbers and a plain verdict: "too early", "B looks better", "A looks better" or "no
  clear difference", from a two-proportion comparison of CTR over the impressions (stated, with the minimum impressions
  needed). **It is not a controlled experiment** and says so. Winner applied only on the user's tap.
- The m11 test log is kept as the history list. Data: `users/{uid}/abTests/{id}`.

## 4 Channel audit
- A **health score out of 100** from rule-based checks on real data, each with a weight, a status, the reason, and **Fix it**:
  upload rhythm (gap between uploads, from the Phase B streak data), titles (length, keywords), thumbnails present,
  descriptions (first two lines, links, not empty), tags, channel description and keywords (`brandingSettings`), playlists
  in use, Shorts and long-video mix, comment reply rate, retention and CTR against the account's **own** median (no invented
  industry benchmarks), and the account-status items below.
- **Fix it:** opens the right place (Create, the content editor, channel defaults) or drafts the fix with the AI (cost
  shown) for the user to approve (for example a better channel description).
- The score is rule-based and costs no AI credits; the plain-language summary is optional and one action.
- **Free: 1 audit a month. Pro: unlimited** (`PlanLimits`). Counted when an audit is run; audits are saved
  (`users/{uid}/audits/{id}`) so the history and the next-available date show.

## 5 Earnings (if monetized)
- Estimated revenue for 7 / 28 / 90 days, **per day** and **per video**, and **RPM** (estimated revenue per 1,000 views,
  computed by Creatora from the two real numbers). Money comes from Analytics `estimatedRevenue` (US dollars, YouTube's
  estimate) and respects Home's hide-amounts eye. If the revenue query is refused (not monetized or no access) the screen
  says so and shows the Monetization journey instead; never a zero.
- Where: **Grow, Earnings**, and the same card reachable from **Profile, Earn**.

## 6 Monetization journey
Progress toward the YouTube Partner Program tiers, computed from API data and labelled **"Estimate. Check YouTube Studio for
your official status."** (YouTube's rules change; the numbers live in one config with a "last checked" date.)

| Tier (as given) | Subscribers | Plus either | Or |
|---|---|---|---|
| 1 (early access to some features) | 500 | 3 public uploads in 90 days and 3,000 watch hours in 365 days | 3 million public Shorts views in 90 days |
| 2 (ad revenue) | 1,000 | 4,000 watch hours in 365 days | 10 million public Shorts views in 90 days |

Data: subscribers (`channels.list`), watch hours over 365 days (`estimatedMinutesWatched` divided by 60), Shorts views over 90
days (Analytics `creatorContentType==SHORTS`), uploads in 90 days (the uploads list). Each requirement is a bar with
"have / need" and the remaining gap; the card shows the nearest reachable path ("you are closest through watch hours:
2,310 of 4,000"). Public-only counting and recent policy changes mean it can differ from Studio, which is why it is labelled.
Whether the channel is already in the program cannot be read; it is inferred only as a hint from the revenue query
succeeding and said as such.
**AI plan:** one action (cost shown) writes a plan to the next milestone from the real gaps and the goal (never a promise),
cached for the week.

## Studio-style features (Creatora's own design)
### Content manager (in Library)
A new **Videos** section in Library: all the account's uploaded videos as cards (thumbnail first, title, small status pill,
views, date). A "Needs attention" strip on top (failed or rejected uploads, restricted, private with a schedule, missing
thumbnail or description). Filters sit in a bottom sheet of our chips:
- **Sort by** (newest, oldest, most views, lowest CTR where known);
- **Visibility:** Public, Private, Unlisted, Scheduled (from `status.privacyStatus` and `publishAt`), and **Draft**, which
  shows Creatora's own unfinished projects because YouTube does not expose drafts;
- **Views** (ranges); **Restrictions:** age-restricted, region-restricted, made for kids, rejected or failed upload (from
  `contentDetails` and `status`). Copyright claims are not exposed, and the sheet says so with a Studio link.
- **Edit** in a bottom sheet in Creatora's form style: title, description, tags, thumbnail (pick or from the thumbnail maker),
  visibility and schedule. **Save** asks for confirmation naming what changes (`videos.update`, `thumbnails.set`, 50 units each).
  Several videos at once: long-press to select, then change visibility or add a tag, with a total quota cost shown.
- Note: videos uploaded through Creatora before Google's audit are locked private by YouTube (spec 05); visibility changes on
  those say so.
- List data: uploads playlist plus `videos.list` (about 2 units per 50 videos), cached 1 hour.

### Channel defaults (Settings, Account, channel)
- **Made for kids:** Yes, No, or **Review every video** (the default); Yes and No are written to the channel
  (`channels.update` status) after confirmation; "Review every video" makes Create and Publish ask each time.
- **Country** and channel keywords (`brandingSettings`), edited and saved through the API after confirmation.
- **Default upload settings** (category, visibility, tags, language): stored in Creatora (`users/{uid}/channelDefaults`) and
  applied to Creatora's uploads; the screen says YouTube's own defaults cannot be read or changed by apps.

### Comment controls
Comments on or off for new uploads and the channel's hold-for-review setting are **not available through the API**: one
card explains this and opens YouTube Studio. What Creatora does offer: the moderation queue actions and its own filter (see
Comments inbox). No fake switches.

### Notifications (new categories in Settings, Notifications)
**Achievements** (subscriber and view milestones, first video over a threshold, streaks; from Phase B data), **Policy alerts**
(only what the API exposes: a rejected or failed upload with its reason, a video becoming region- or age-restricted, a change
of the long-uploads status; copyright claims and strikes are not exposed, so they never claim to be covered), **Earn updates**
(revenue milestone reached, Monetization journey requirement met). Each is a switch, on by default for Policy alerts, and
uses the existing worker and inbox from Phase A and B.

### Account status card
Shows what Creatora can read: channel active, long-uploads status (`status.longUploadsStatus`), made-for-kids setting,
verified or not where returned, last data refresh. For violations, strikes, copyright, monetization eligibility and
account-level restrictions it says "Not available to apps" and opens YouTube Studio. In Grow and in Settings, Account.

### Team, permissions, brand partner access
Not available through any API: one row, "Manage in YouTube Studio", that opens the Studio page. No fake screen.

## Where it lives
- **Grow tab:** a home of cards (health score, inbox count, monetization progress, this week's views) leading to Analytics,
  Comments, Tests, Audit, Earnings. **Library:** the new Videos section. **Settings, Account:** channel defaults and the status
  card. **Profile, Earn:** the Earnings and Monetization cards.
- Routes inside the Grow and Library graphs keep the bottom bar; Settings screens use `SubScreenScaffold`.

## Plan limits (added to `PlanLimits`)
| Limit | Free | Pro |
|---|---|---|
| Analytics, Comments inbox, Earnings, Monetization journey, Content manager | yes | yes |
| AI reply drafts a day | 10 * | 100 * |
| Comment classifications a day | 100 * | 1,000 * |
| A/B tests | **locked (Pro)** | unlimited * |
| Channel audits | **1 a month** | unlimited |

## Data model
Firestore under `users/{uid}`, owner-only like everything else, no rule change: `commentRules` (one document), `comments/{id}`
(id, video, kind, draft, status, classifiedAt, kept 30 days), `blockedTerms`, `abTests/{id}`, `audits/{id}`, `channelDefaults`,
`achievements/{id}`; caches under `cache/` as in Phase C. Commenters' text is public data and is cached briefly; it is deleted
with the account like all of the user's data. Room is not changed.

## Reuse from `m11-grow` and the schema check
- **Reuse:** the chart drawing (`ui/grow/GrowCharts.kt`), the comment draft and A/B test models and status flow (`DraftStatus`,
  `AbTest`, variants), the Grow screen pieces and their tests, adapted to real API data and Firestore.
- **Not carried:** the Room tables, the manual channel snapshots (the API gives real history now) and the user-typed reply
  drafts (replaced by AI drafts the user approves).
- **Schema check:** the branch fails CI because its Room schema for the new tables was never committed. Fix: carry **no** Room
  tables (the data moves to Firestore), so no schema change and nothing to commit. If a Room column is added after all, use an
  **AutoMigration** and commit the schema JSON that CI prints.

## Tests
Unit tests for: Analytics query building and parsing of every report (overview, per video, retention, audience, traffic,
revenue), period changes, report cache TTL and the "updated" time, quota-exhausted handling; comment classification parsing,
the rule-based spam pre-filter, reply rules (per video and per day limits, default kinds), draft generation per plan limit,
approve one and approve all selected (including partial failure and retry), nothing posts without approval, comments to ideas,
moderation queue actions, the blocked words filter; A/B test scheduling, block switching, the CTR comparison verdicts
("too early", clear and no clear difference), Pro lock; audit checks with reasons and Fix it targets, the Free 1 a month limit
and its rollover; RPM and earnings parsing and hiding; Monetization journey progress for both tiers and the labelled estimate;
content manager list building, filters, sort, restrictions, edit requests and bulk edit costs; channel defaults including
"Review every video"; the new notification categories; the account status card and the Studio links; locked, empty, loading and
error states. Previews in light and dark for every screen.

## Decisions to confirm
1. The `*` numbers (reply drafts, classifications, A/B tests for Pro).
2. `youtube.force-ssl` joins the Google verification and audit (spec 05 approvals) [yes].
3. Online times shown as weekday only, since YouTube has no hourly data [yes].
4. Partner Program thresholds kept in one config with a "last checked" date and labelled as an estimate [yes].
5. Phase F as one PR, or Grow first and the Studio-style features second [one PR unless told otherwise].
