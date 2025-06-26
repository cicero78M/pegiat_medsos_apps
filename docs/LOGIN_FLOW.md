# Login Flow with Landing Page

This document describes how the application handles user authentication when a landing page is present. The flow follows best practices for security and user experience.

## 1. Splash Screen

- **SplashActivity** appears on launch. It shows the logo and checks if a saved
  JWT token is still valid.
- When valid, the dashboard is opened automatically.
- When not, the user is forwarded to the landing page.

## 2. Landing Page

- **MainActivity** displays a short introduction and a **Login** button.

## 3. Login Screen

- **LoginActivity** contains a form for NRP and phone number (as password).
- Input is validated locally (non-empty fields) and a request is sent over HTTPS to `/api/auth/user-login`.
- On success, the JWT token and user ID are stored securely and the dashboard is opened.
- Errors are shown as toast messages, such as "NRP dan password wajib diisi" or "Gagal terhubung ke server".

## 4. Session Management

- Every network request includes the `Authorization: Bearer <token>` header.
- When a request returns `401 Unauthorized`, the app requires the user to log in again.
- Tokens are kept in `SharedPreferences` and cleared on logout.

## 5. Logout

- The profile screen provides a logout button that removes the saved token.
- After logout, the user is returned to the landing page.

By introducing the landing page, first-time users can learn about the app before logging in, while returning users with a valid token are seamlessly redirected to the dashboard.
