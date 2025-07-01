# Facebook Login via WebView

This fragment shows how the application logs a user into Facebook using a built-in WebView.

1. When the **Login dengan Facebook** button is pressed the WebView is displayed.
2. The login page from `m.facebook.com` is loaded and a progress bar is shown.
3. After credentials are entered and the page redirects to `/me`, cookies are stored securely using `FacebookSessionManager`.
4. The fragment then fetches the profile title from `/me` to display the logged in name.
5. Pressing **Logout** removes the cookies and shows the login button again.
