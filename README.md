# Cicero Reposter

This project contains a minimal Android application skeleton. The app includes
several activities:

- `SplashActivity` shows the app logo and validates any saved JWT token before
  continuing to the next screen.
- `MainActivity` acts as a landing page with a short introduction and a **Login**
  button.
- `LoginActivity` for user authentication
- `UserProfileActivity` to show user details
- `DashboardActivity` to display Instagram posts fetched from official accounts
  and host a bottom navigation bar
- `ReportActivity` for viewing repost links

The project uses Gradle Kotlin DSL. To build the project you would typically run:

```bash
./gradlew assembleDebug
```

## Update from GitHub Releases

`MainActivity` includes a **Perbarui Aplikasi** button that queries the latest
release on GitHub. If a newer APK is available, the download URL is opened in
the browser.

A workflow in `.github/workflows/update-release.yml` builds `app-release.apk`
from source and attaches it to a GitHub release whenever a version tag is
pushed.

Additional implementation is required to integrate Instagram APIs and handle authentication.

The app retrieves user profile information from the [Cicero_V2](https://github.com/cicero78M/Cicero_V2) backend API.
After a successful login, the token and user ID returned by `/api/auth/user-login`
are used to request `/api/users/{userId}` to display the profile screen.
The profile screen displays @username followed by rank and name, the user's NRP and Instagram statistics (post, follower and following counts). These stats are loaded from the backend and will be fetched on demand if missing. The screen also lists the fields Client ID, Satfung, Jabatan, and Status.
After logging in the user is redirected to `DashboardActivity` where a bottom navigation bar lets them open the profile, Instagram content, the Instagram login tools, a YouTube page and the Twitter page. The Twitter page uses the `twitter4j` library for OAuth authentication. After logging in through the browser you will be redirected back to `repostapp://twitter-callback` which should be configured as a callback URL in the Twitter developer portal. This replaces the manual PIN entry.
A logout button is provided at the bottom of the profile page.

## Environment Variables

Create a `.env` file in the project root before building the app. It must define your Twitter API credentials:

```ini
TWITTER_CONSUMER_KEY=YOUR_CONSUMER_KEY
TWITTER_CONSUMER_SECRET=YOUR_CONSUMER_SECRET
YOUTUBE_CLIENT_ID=YOUR_YOUTUBE_CLIENT_ID
YOUTUBE_API_KEY=YOUR_YOUTUBE_API_KEY
YOUTUBE_CLIENT_SECRET=YOUR_YOUTUBE_CLIENT_SECRET
FACEBOOK_APP_ID=YOUR_FACEBOOK_APP_ID
```

You can copy `.env.example` as a starting point. These values are exposed as `BuildConfig.TWITTER_CONSUMER_KEY` and `BuildConfig.TWITTER_CONSUMER_SECRET` at runtime and are used by `TwitterFragment`.
The YouTube variables become `BuildConfig.YOUTUBE_CLIENT_ID`, `BuildConfig.YOUTUBE_API_KEY` and `BuildConfig.YOUTUBE_CLIENT_SECRET`, which are read by `YoutubeFragment`.
`FACEBOOK_APP_ID` becomes the `facebook_app_id` string resource used by the Facebook SDK.

## Documentation

More detailed explanations of the activity flow and build instructions can be
found in the [docs](docs/) directory:

- [`ARCHITECTURE.md`](docs/ARCHITECTURE.md) – overview of activities and
  fragments.
- [`USAGE.md`](docs/USAGE.md) – how to build and interact with the app.
- [`landing_page.html`](docs/landing_page.html) – a lightweight landing page
  describing key features and providing a download link.
- [`IGTOOLS_FACEBOOK.md`](docs/IGTOOLS_FACEBOOK.md) – skenario pemuatan profil
  Facebook di halaman IG Tools.

## TikTok Automation

The `scripts/tiktok_post.py` helper demonstrates how to upload a video to TikTok
using the [UIAutomator2](https://github.com/openatx/uiautomator2) library. Install
the requirements first:

```bash
pip install -r requirements.txt
```

Then connect your Android device (USB or Wi‑Fi) and run:

```bash
python scripts/tiktok_post.py <device-serial> /sdcard/video.mp4 --caption "Hello"
```

This will open TikTok, select the first video in the gallery, optionally set the
caption and publish the post.




