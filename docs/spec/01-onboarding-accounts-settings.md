# Spec 01: Onboarding, accounts and settings (Phase A)

Status: approved by the manager. Direction: **cloud-first**. This replaces the old local-first plan
(PRs #10 to #13 stay open and untouched; they will be mined later).

## Platform decisions

| Area | Decision |
|------|----------|
| Backend | Firebase: Auth (Google), Firestore, Storage, Cloud Functions (later), Crashlytics |
| AI | Firebase AI Logic (Gemini Developer API). The key stays on Google's servers, protected by App Check. Users no longer enter API keys |
| Old AI code | The M7 provider code (user keys, failover) stays only as a fallback behind the dev flag `devAiKeys`. The "AI providers" key screen is not in normal Settings |
| Plans | Free and Pro (Google Play Billing) |
| Sign-in | Credential Manager (Google ID token) then Firebase Auth |
| YouTube access | Google Identity `AuthorizationClient`, scopes `youtube.readonly`, `yt-analytics.readonly`, `yt-analytics-monetary.readonly`; `youtube.upload` is requested later (incremental) |

### Firebase project (set up by the manager)
- Project ID `creatora-e2cef`, project number `5839100796`, Android app id `1:5839100796:android:979ce83849ddc4cca4a2f9`
  (package `com.creatora.app`). These are identifiers, not secrets.
- Enabled: Auth (Google provider), Firestore (asia-south1, production mode), Firebase AI Logic with the Gemini
  Developer API, App Check enforced, AI monitoring. Google Cloud: YouTube Data API v3 and YouTube Analytics API.
- **Firestore rules** are in `firestore.rules` (deploy with `firebase deploy --only firestore:rules`, config in
  `firebase.json`): a user can read and write only `users/{uid}` and its subcollections, everything else is closed.
  Refinement: the plan can be created only as `free` and can never be changed by the client, so a modified app
  cannot raise its own plan. Usage counters live in the user's own tree and a modified app could still edit them;
  moving the counter behind a Cloud Function is the fix and is planned.
- **App Check**: Play Integrity in release builds, the Debug provider in debug builds. A debug build prints its
  debug secret to logcat (tag `DebugAppCheckProvider`); register it under App Check > Apps > Manage debug tokens.
- **OAuth consent** is External and In production but unverified: users see Google's "unverified app" warning and
  the project is capped at 100 users until Google verifies the app. The YouTube scopes are sensitive or restricted,
  so verification is needed before a public launch.

### Firebase configuration
- The manager creates the Firebase project and stores `google-services.json` in the GitHub secret
  `GOOGLE_SERVICES_JSON`. CI writes it to `app/google-services.json` when the secret exists.
- The build works without the file: the Google Services and Crashlytics plugins are applied only when it exists.
  Without it the app starts, and sign-in says clearly that cloud services are not set up in this build.
- A fixed debug keystore (`app/debug.keystore`, debug only, not a secret) keeps the signing SHA-1 stable so it
  can be registered once in Firebase and Google Cloud. The SHA-1 is printed in the PR body.
- The web client ID comes from the generated `default_web_client_id` resource (nothing is hard-coded).

## Honesty rules for all AI
1. Never invent numbers. Every figure shown or quoted comes from fetched data.
2. Say when data is missing (for example "retention is not available for this channel").
3. Show the source metric next to every claim.
4. The user approves any post or delete. Nothing is sent or removed automatically.

## Flow

### 1A Splash
Shows the logo while the app reads the saved session. Signed in and onboarding finished: Home. Otherwise the
language picker.

### 1B Language and welcome
- Language picker first: English, हिंदी, Hinglish. Preselected from the device locale (Hindi device: हिंदी,
  everything else: English). The choice is applied at once and saved.
- Three welcome slides in the chosen language, a Skip button, and a large "Continue with Google".

### 1C Google sign-in
- Credential Manager, then Firebase Auth.
- Creates `users/{uid}`: name, photo, email, `plan = free`, language, createdAt. An existing document is kept.
- Then requests YouTube authorization (scopes above) and fetches **all** YouTube channels of that Google account.

### 1D No questions
Niche, formats and language are derived by AI from the titles and descriptions of the connected accounts and
stored as the creator profile. Only if nothing is connected, one short question is asked: "What do you make?".

### 1D2 Your goal
A short step after the accounts are connected (and after the one question, if it was asked):
- **Target for the next 30 days:** pick one of subscribers, views, uploads or earnings, and a number.
- **Main desire in their own words:** free text, optional (for example "I want to quit my job and make videos").
- **Preferred upload frequency:** uploads per week (1, 2, 3, 5 or 7).
- Skip for now is always available; nothing is stored then.
- Stored in Firestore at `users/{uid}/goals/{id}` as the user's primary goal: metric, target, started at, deadline
  (30 days later), the starting value when the platform gave one (subscribers), the desire text and uploads per
  week. The Home "Goals" card (Phase B) reads and edits it.
- **All AI is steered by this goal** (Home focus, ideas, research suggestions): every AI request carries the goal
  and the desire when personalization is on. `MeteredAiService` adds it, next to the language, tone and profile.
- **30-day plan** (Phase B, Home): the AI builds a week-by-week plan toward the goal and re-plans each week from
  real results ("you are 40% of the way to the target; here is what to change").
- **Copy rule, everywhere:** never promise virality or guaranteed results. UI text and AI replies say "aim",
  "plan" and "progress". `MeteredAiService` tells the AI the same.

### 1E Connected accounts
Confirmed approach:
- **YouTube:** Google sign-in finds the YouTube channels of the account automatically. No separate login.
- **Every other platform:** its own "Login with …" button, using that platform's official login. For each platform
  Creatora collects whatever the platform's official API allows and nothing more.
- **Platforms without an API:** link only. The user pastes the profile link; it is saved and nothing is fetched.
- Pasting a link or ID also works for any supported platform; the platform is detected from the link.
- Platform adapter architecture: one interface, one implementation per platform.

| Platform | Connect with | State in Phase A |
|----------|--------------|------------------|
| YouTube | Google sign-in (automatic) | Full: profile, about, videos, titles, views, likes, comments, analytics (CTR, retention, traffic) and estimated revenue when monetized |
| Instagram | "Login with Instagram" (Instagram API with Instagram Login, professional accounts only) | Adapter with a real "coming soon" state. Personal accounts get a how-to-convert help text |
| Facebook Pages | "Login with Facebook" | Adapter, "coming soon" |
| TikTok | "Login with TikTok" | Adapter, "coming soon" |
| Moj, Josh, ShareChat, Snapchat | Paste a link (no suitable API) | Link only: the link is saved, nothing is fetched |

- **Several accounts per platform are allowed** (for example 2 or 3 YouTube channels, several Instagram accounts).
  Accounts are a list keyed by platform plus account id; there is no "primary" account in the data. The plan limit
  counts the total number of accounts across platforms: Free 1, Pro 5 (placeholders). Once the limit is reached,
  connecting another shows the upgrade message.
- After connecting: a preview card (photo, name, subscribers, video count) with "Is this you?". When the Google
  account has several channels, each one gets its own card and the user confirms the ones they want (up to the
  plan limit). "Skip for now" is always available.
- Home shows "Analysing your channel…", then first insights: for each underperforming video, why it
  underperformed and how to improve it. The AI is given the real metrics and must cite them; the card also shows
  the raw numbers.

### 1F Notifications
- A permission request (Android 13+), then one toggle per category: daily ideas, render done, new comments,
  view milestones (the user sets the threshold), earnings update (estimated), plan and usage alerts.
- Comments, milestones and earnings are polled server-side later (Cloud Functions). For now: the settings, the
  local notification channels and a WorkManager poll for YouTube.

### 1G Plan card
A soft card at the end. "Free mein continue karo" is the primary action; Pro is the secondary one.

## Settings
A top-level list like phone settings. Each row opens its own page. Connected accounts is **not** a top-level row:
it lives inside Account.
1. **Account**, with these sections in order:
   1. **Profile**: photo, name, email, plan.
   2. **Connected accounts**: the list of connected accounts (remove one with its delete button) and a
      **Connect your accounts** button that opens the connect panel (Google finds YouTube channels; "Login with …"
      for other platforms; paste a link for link-only platforms). Shows how many accounts the plan allows.
   3. **Sign out**.
   4. **Delete account**.
2. **Notifications**: the categories above.
3. **Creatora AI**: language, tone, personalization (the creator profile), history.
4. **Billing and usage**: plan, AI usage meter, upgrade (Play Billing), payment history.
5. **Appearance and language**: theme, app language.
6. **Privacy and security**.
7. **Help and about**.

## AI usage limit
- A meter per plan, tracked per month in Firestore (`users/{uid}/usage/{yyyyMM}`).
- At 90%: a dialog (once per month). 91% to 99%: a persistent banner at the top that updates each percent.
  At 100%: AI actions are disabled with an Upgrade call to action.
- Limits (placeholders for the manager to confirm): Free 50 AI actions a month, Pro 1000. Accounts: Free 1, Pro 5.

## Languages
App-wide rule: all UI text **and all AI replies** use the language the user chose; before they choose, the device
language (English, Hindi or Hinglish) is used.

`values` (English), `values-hi` (Hindi) and `values-b+hi+Latn` (Hinglish). The in-app choice overrides the
device language. Strings not yet translated fall back to English; the full Hindi pass is part of release quality.

## Known limits of Phase A (stated honestly)
- Plan and usage are enforced on the phone. Firestore security rules and purchase verification must be added
  server-side (Cloud Functions) before launch, or a modified app could raise its own plan.
- Play Billing needs the products created in Play Console. Until then the Upgrade screen says Pro is not
  available yet.
- Payment history is not exposed by Play Billing; the page links to Google Play's subscription screen.
- Nothing here can be tested against live Google, Firebase or YouTube services without the manager's
  project and keys; CI only builds and runs unit tests.
