# Cicero Reposter

This project contains a minimal Android application skeleton. The app includes several activities:

- `MainActivity` as a landing page. It checks for a saved JWT token and
  immediately opens the dashboard when the token is valid.
- `LoginActivity` for user authentication
- `UserProfileActivity` to show user details
- `DashboardActivity` to display Instagram posts fetched from official accounts
  and host a bottom navigation bar
- `ReportActivity` for viewing repost links

The project uses Gradle Kotlin DSL. To build the project you would typically run:

```bash
./gradlew assembleDebug
```

Additional implementation is required to integrate Instagram APIs and handle authentication.

The app retrieves user profile information from the [Cicero_V2](https://github.com/cicero78M/Cicero_V2) backend API.
After a successful login, the token and user ID returned by `/api/auth/user-login`
are used to request `/api/users/{userId}` to display the profile screen.
The profile screen displays @username followed by rank and name, the user's NRP and Instagram statistics (post, follower and following counts). These stats are loaded from the backend and will be fetched on demand if missing. The screen also lists the fields Client ID, Satfung, Jabatan, Username TikTok and Status.
After logging in the user is redirected to `DashboardActivity` where a bottom navigation bar lets them open the profile, Instagram content and link report pages.
A logout button is provided at the bottom of the profile page.

## Documentation

More detailed explanations of the activity flow and build instructions can be
found in the [docs](docs/) directory:

- [`ARCHITECTURE.md`](docs/ARCHITECTURE.md) – overview of activities and
  fragments.
- [`USAGE.md`](docs/USAGE.md) – how to build and interact with the app.
- [`landing_page.html`](docs/landing_page.html) – a lightweight landing page
  describing key features and providing a download link.
