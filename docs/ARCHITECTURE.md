# Project Architecture

This Android application is organized around a small set of Activities and Fragments.
The main purpose of the app is to authenticate the user, display Instagram posts
and allow reporting of content links.

## Activity Flow

1. **SplashActivity** – Entry point that shows the logo and checks for a saved
   JWT token. When valid it opens `DashboardActivity`, otherwise it forwards the
   user to `MainActivity`.
2. **MainActivity** – A landing page with a brief explanation and a **Login**
   button. It also validates a saved token so the dashboard opens immediately
   when the session is still active.
3. **LoginActivity** – Presents a simple login form (NRP and phone number).
   On success it stores the token and user ID in `SharedPreferences` and opens
   the dashboard. The activity likewise validates an existing token on startup
   to skip the form for returning users.
4. **DashboardActivity** – Hosts a `ViewPager2` with a bottom navigation bar.
   It shows multiple fragments:
   - `UserProfileFragment` – displays user information retrieved from the
     backend API.
   - `InstaLoginFragment` – handles Instagram login and automation features.
   - `DashboardFragment` – lists Instagram posts fetched for the logged in user.
  - `TwitterFragment` – provides login using the `twitter4j` library to access
    Twitter via an OAuth flow that redirects back to `repostapp://twitter-callback`. The API keys are loaded from
    `BuildConfig` fields defined via a `.env` file.
5. **ReportActivity** – A standalone screen to paste links from various social
   media platforms for reporting purposes.

Fragments are kept lightweight so most of the logic resides in the Activities.
Networking is done with OkHttp and Kotlin coroutines.
