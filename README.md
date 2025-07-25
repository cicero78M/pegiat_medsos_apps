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

Before running the build you must point Gradle to a valid Android SDK
installation.  Create a file named `local.properties` in the project root with
the following content (adjust the path to match your environment):

```properties
sdk.dir=/path/to/Android/Sdk
```

Without this file the Android plugin cannot generate sources such as
`BuildConfig`, which leads to errors like `Unresolved reference: BuildConfig` at
compile time.

The Twitter API keys used for login are loaded from a `.env` file in the project
root. Copy `.env.example` to `.env` and fill in your credentials:

```bash
cp .env.example .env
# edit .env and set TWITTER_CONSUMER_KEY, TWITTER_CONSUMER_SECRET
# and API_BASE_URL if you need a different backend
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
A bottom navigation bar lets the user access the profile and Instagram content after logging in.
A logout button is provided at the bottom of the profile page.

## Documentation

More detailed explanations of the activity flow and build instructions can be
found in the [docs](docs/) directory:

- [`ARCHITECTURE.md`](docs/ARCHITECTURE.md) – overview of activities and
  fragments.
- [`USAGE.md`](docs/USAGE.md) – how to build and interact with the app.
- [`landing_page.html`](docs/landing_page.html) – a lightweight landing page
  describing key features and providing a download link.

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

## Autopost

The macro prototype has been removed and replaced with a lightweight Autopost
page. This new page relies on the unofficial Instagram Private API to sign in
and display the authenticated user's profile information. From there the
application can be extended to perform automatic posting.

To run the Autopost page locally, first install the Node.js dependencies and
start the bundled Express server:

```bash
npm install
npm start
```

The server listens on port `3000` by default. Visit
`http://localhost:3000/autopost` to access a simple Autopost interface.
It now uses a lightweight **view pager** so you can switch between
Instagram, Twitter and TikTok workflows. Each page keeps track of posts that
have been submitted to avoid duplicates across sessions.





## Fetching Facebook Pages

The repository includes a script that fetches pages from Facebook while
emulating a real mobile browser. The script sends common headers such as
`Accept`, `Accept-Language`, and `Connection` so Facebook does not display the
"Facebook tidak tersedia di browser ini" warning. It also handles login tokens
(`lsd`, `jazoest`) and cookies automatically.

Run the script with Node.js and provide the URL you want to fetch. If you set
`FB_EMAIL` and `FB_PASS` environment variables, the script will attempt to log
in before requesting the page.

```bash
FB_EMAIL=myuser@example.com \
FB_PASS=secretpass \
node scripts/fetch_facebook.js https://m.facebook.com
```

The HTML contents will be printed to standard output.
