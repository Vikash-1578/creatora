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

### 1E Connected accounts
- Platform picker plus "paste ID or profile link". The platform is detected from the link. A Connect button
  opens the platform's own login.
- Platform adapter architecture: one interface, one implementation per platform.

| Platform | State in Phase A |
|----------|------------------|
| YouTube | Full: profile, about, videos, titles, views, likes, comments, analytics (CTR, retention, traffic) and estimated revenue when monetized |
| Instagram | Adapter with a real "Coming soon" state. Instagram API with Instagram Login, professional accounts only; personal accounts get a how-to-convert help text |
| Facebook Pages | Adapter, "Coming soon" |
| TikTok | Adapter, "Coming soon" |
| Moj, Josh, ShareChat, Snapchat | Link only: the link is saved, nothing is fetched |

- After connecting: a preview card (photo, name, subscribers, video count) with "Is this you?". Several channels:
  the user picks from a list. Free plan: 1 account. Pro: more. "Skip for now" is always available.
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
A top-level list like phone settings. Each row opens its own page:
1. **Account**: profile, sign out, delete account.
2. **Connected accounts**: list, add, remove.
3. **Notifications**: the categories above.
4. **Creatora AI**: language, tone, personalization (the creator profile), history.
5. **Billing and usage**: plan, AI usage meter, upgrade (Play Billing), payment history.
6. **Appearance and language**: theme, app language.
7. **Privacy and security**.
8. **Help and about**.

## AI usage limit
- A meter per plan, tracked per month in Firestore (`users/{uid}/usage/{yyyyMM}`).
- At 90%: a dialog (once per month). 91% to 99%: a persistent banner at the top that updates each percent.
  At 100%: AI actions are disabled with an Upgrade call to action.
- Limits (placeholders for the manager to confirm): Free 50 AI actions a month, Pro 1000. Accounts: Free 1, Pro 5.

## Languages
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
