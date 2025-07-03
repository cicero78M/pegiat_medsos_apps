# Building and Running

This project relies on the standard Android toolchain. You can open the
repository with **Android Studio Flamingo (or newer)** and press the *Run* button
on an attached emulator or device.

If you have the Gradle wrapper installed you may also build from the command
line:

```bash
./gradlew assembleDebug
```

Before building, copy `.env.example` to `.env` and provide your Twitter consumer
key and secret. Optionally set `IG_GRAPH_TOKEN` and `IG_USER_ID` to enable
Instagram Graph API integration on the dashboard. These values are read at build
time to configure `TwitterFragment` and the dashboard screen.

The app requires an active internet connection to reach the backend API hosted on
`papiqo.com`. Login uses the `/api/auth/user-login` endpoint which returns a JWT
and the user identifier. These values are saved in `SharedPreferences` under the
`auth` key space.

After a successful login the dashboard fetches Instagram posts using the
official Instagram Graph API when credentials are provided. Otherwise it falls
back to `/api/insta/posts?client_id=<id>`. The dashboard shows today's posts and
Tapping a post
will download it to a public directory named **CiceroReposterApp** and open a share dialog.
If the directory does not yet exist you will be prompted to create it before the
download proceeds.
The dialog lets you share the file, **Kirim Link** to open the reporting form,
and when a post already shows a check mark you will also see **Laporan WhatsApp**
which now sends the full report message retrieved from the backend via WhatsApp.
