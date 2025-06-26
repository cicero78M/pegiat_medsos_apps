# Project Architecture

This Android application is organized around a small set of Activities and Fragments.
The main purpose of the app is to authenticate the user, display Instagram posts
and allow reporting of content links.

## Activity Flow

1. **SplashActivity** – Entry point that shows the logo and checks for a saved
   JWT token. When valid it opens `DashboardActivity`, otherwise it forwards the
   user to `MainActivity`.
2. **MainActivity** – A landing page with a brief explanation and a **Login**
   button.
3. **LoginActivity** – Presents a simple login form (NRP and phone number).
   On success it stores the token and user ID in `SharedPreferences` and opens
   the dashboard.
4. **DashboardActivity** – Hosts a `ViewPager2` with a bottom navigation bar.
   It shows two fragments:
   - `UserProfileFragment` – displays user information retrieved from the
     backend API.
   - `DashboardFragment` – lists Instagram posts fetched for the logged in user.
5. **ReportActivity** – A standalone screen to paste links from various social
   media platforms for reporting purposes.

Fragments are kept lightweight so most of the logic resides in the Activities.
Networking is done with OkHttp and Kotlin coroutines.
