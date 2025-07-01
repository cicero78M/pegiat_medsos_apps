# Facebook Login via WebView

This fragment shows how the application logs a user into Facebook using a built-in WebView.

1. When the **Login dengan Facebook** button is pressed the WebView is displayed.
2. The login page from `m.facebook.com` is loaded and a progress bar is shown.
3. After credentials are entered and the page redirects to `/me`, cookies are stored securely using `FacebookSessionManager`.
4. The fragment fetches the `/me` page again to read the title and profile image. The user's name and avatar are displayed when the session is active.
5. Pressing **Logout** removes the cookies and hides the profile picture while showing the login button again.
