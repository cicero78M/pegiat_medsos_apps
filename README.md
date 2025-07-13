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

## Macro Automation

This version replaces the old Instagram and Twitter auto repost implementation
with a generic macro system. A single accessibility service reads a saved macro
and performs the recorded gestures such as click, swipe and set text. Macros are
managed through a simple ViewPager UI where actions can be added or removed and
persisted with `SharedPreferences`.




