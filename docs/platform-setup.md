# Platform setup: Instagram, Facebook Pages and TikTok

Creatora signs in to these platforms with their own login screens and reads data (and posts) through their APIs.
Nothing works until the developer apps below exist and their IDs are given to the build. YouTube needs none of this (it
uses the Google sign-in from Firebase).

Everything here is written from each platform's documentation. None of it has been run against a live account yet,
so when something fails the app shows the platform's own error text. Send that text and it can be fixed quickly.

## How it works

1. The user taps "Login with Instagram" (or Facebook, TikTok). Creatora opens the platform's sign-in page in the browser.
2. The platform sends the user to **one shared HTTPS redirect page** (below). It cannot send them straight to the app,
   because Meta and TikTok only accept `https` redirect addresses.
3. The redirect page forwards everything to `creatora://oauth?...`, which opens Creatora again.
4. Creatora swaps the code for a token and keeps it **encrypted on the phone** (Android Keystore). Tokens are never
   sent to Creatora's servers or stored in Firestore.

## 1. The shared redirect page (Firebase Hosting)

The page is already in the repository: `hosting/public/oauth/index.html`. Deploy it once:

```bash
npm install -g firebase-tools
firebase login
firebase use <your-firebase-project-id>
firebase deploy --only hosting
```

The page is then at `https://<your-firebase-project-id>.web.app/oauth/`. That exact address, **with the trailing
slash**, is the value of `OAUTH_REDIRECT_URL` and is what you register as the redirect address in all three developer
apps. (The page is static and keeps nothing.)

## 2. Instagram (Instagram API with Instagram Login)

Works for **professional accounts only** (Business or Creator). A personal account cannot be connected: in Instagram go to
Settings, Account type and tools, Switch to professional account.

1. Go to developers.facebook.com, My Apps, **Create app**, type **Business**.
2. Add the product **Instagram**, then open **API setup with Instagram login**.
3. Copy the **Instagram app ID** and **Instagram app secret** shown there. These are not the Facebook app ID and secret.
   They become `INSTAGRAM_APP_ID` and `INSTAGRAM_APP_SECRET`.
4. Under **Set up Instagram business login**, add the redirect address from section 1 to **Valid OAuth redirect URIs**.
5. Permissions the app asks for: `instagram_business_basic`, `instagram_business_content_publish`,
   `instagram_business_manage_comments`, `instagram_business_manage_insights`.
6. While the app is in **development mode** it works for the people you add: App roles, Roles, **Add People**,
   **Instagram Testers**, then the tester accepts in the Instagram app (Settings, Apps and websites, Tester invites).
7. Posting needs the video at a public https link; Creatora uploads renders to Firebase Storage for that (Phase E).

## 3. Facebook Pages (Facebook Login for Business)

Pages only, never personal profiles. You can use the same Meta app as Instagram.

1. In the Meta app add the product **Facebook Login for Business**.
2. **Settings, Basic**: copy the **App ID** and **App secret**. They become `FACEBOOK_APP_ID` and `FACEBOOK_APP_SECRET`.
3. **Facebook Login for Business, Settings**: add the redirect address from section 1 to **Valid OAuth redirect URIs**.
4. **Facebook Login for Business, Configurations, Create configuration**: type **User access token**; permissions
   `pages_show_list`, `pages_read_engagement`, `pages_manage_posts`, `pages_read_user_content`,
   `pages_manage_engagement`, `read_insights`. Copy the **Configuration ID**: it becomes `FACEBOOK_CONFIG_ID`.
   (Without it Creatora asks for the same permissions directly, which also works.)
5. In development mode the app works for people with a role on the app (App roles, Roles) who manage the Page.

## 4. TikTok (Login Kit, Display API, Content Posting API)

1. Go to developers.tiktok.com, **Manage apps, Connect an app**.
2. Add the products **Login Kit**, **Display API** and **Content Posting API**.
3. Under Login Kit add the redirect address from section 1 as a **Web** redirect URI.
4. Scopes: `user.info.basic`, `user.info.profile`, `user.info.stats`, `video.list`, `video.publish`.
5. Copy the **Client key** and **Client secret**: `TIKTOK_CLIENT_KEY` and `TIKTOK_CLIENT_SECRET`.
6. Use the app's **Sandbox** and add your TikTok account under Target users so you can sign in before the audit.
7. Until TikTok audits the app, posts can only be **private** (visible to the user). Creatora says so after posting.

Meta and TikTok both ask for a **privacy policy URL** and terms of service URL in the app settings. Creatora needs a public
page for these; tell the team which address to use.

## 5. Giving the values to the build

Local builds: add these lines to `local.properties` (git-ignored):

```properties
OAUTH_REDIRECT_URL=https://<project-id>.web.app/oauth/
INSTAGRAM_APP_ID=...
INSTAGRAM_APP_SECRET=...
FACEBOOK_APP_ID=...
FACEBOOK_APP_SECRET=...
FACEBOOK_CONFIG_ID=...
TIKTOK_CLIENT_KEY=...
TIKTOK_CLIENT_SECRET=...
```

GitHub builds: repository **Settings, Secrets and variables, Actions, New repository secret**, one secret per name
above (same names). Leave out any platform you have not set up: its login button then says exactly which settings are
missing instead of failing.

**Known limit:** the app secrets end up inside the APK. That is acceptable for tester builds you share privately. Before
a public release, the code exchange moves to a Cloud Function so the secrets stay on the server; the APKs built by CI are
downloadable by everyone with access to the repository, so keep it private.

## 6. What works before the platforms' reviews

| | Development mode (testers only) | After the platform's review |
|---|---|---|
| Instagram | Sign-in, profile, posts, insights, comments, publishing for added testers | Any professional account (Meta app review for each permission, plus business verification) |
| Facebook Pages | Same for people with a role on the app | Any Page admin (app review for `pages_manage_posts`, `read_insights` and the rest) |
| TikTok | Sandbox target users: profile, videos, stats; posts are private only | Public posts after the Content Posting API audit |

## 7. What each platform does not share

Instagram has no trending, no public search and no other-account lookup in this API. Facebook has none of those either.
TikTok gives no account analytics over time and no comments. Creatora says so on screen instead of showing made-up
numbers: for example Instagram shows no "watch time", and TikTok's snapshot shows followers and per-video totals only.

## 8. Reading an error

| Message in the app | Meaning |
|---|---|
| "Login for Instagram is not set up in this build. These build settings are empty: ..." | A value from section 5 is missing in this build. |
| "Instagram login did not finish: Invalid redirect_uri" | The redirect address is not registered exactly as `OAUTH_REDIRECT_URL` (check the trailing slash). |
| "... Insufficient developer role" / "App not live" | The account is not added as a tester or role holder. |
| "A permission was not granted" | The user unticked a permission, or the app lacks the permission before review. |
