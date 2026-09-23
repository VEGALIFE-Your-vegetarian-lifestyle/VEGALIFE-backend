# API Reference Documentation

Supplementary human-written API docs for the Vegalife backend (complements auto-generated OpenAPI/Swagger at `/swagger-ui.html`).

Each file documents one endpoint: `docs/apis/<feature>/<method>-<resource>.md`

| File | Endpoint | Description |
|------|----------|-------------|
| `auth/post-register.md` | `POST /api/auth/register` | Register new user |
| `auth/get-verify-email.md` | `GET /api/auth/verify-email` | Verify email with token |
| `auth/post-login.md` | `POST /api/auth/login` | User login |
| `auth/post-refresh.md` | `POST /api/auth/refresh` | Refresh access token |
| `auth/post-logout.md` | `POST /api/auth/logout` | User logout |
| `auth/post-forgot-password.md` | `POST /api/auth/forgot-password` | Request password-reset OTP by email |
| `auth/post-reset-password.md` | `POST /api/auth/reset-password` | Reset password with OTP |
| `profile/put-profile.md` | `PUT /api/profile` | Update user profile |
| `admin/get-users.md` | `GET /api/admin/users` | List user accounts (Admin) |
| `admin/post-suspend-user.md` | `POST /api/admin/users/{userId}/suspend` | Suspend user account (Admin) |
| `admin/post-restore-user.md` | `POST /api/admin/users/{userId}/restore` | Restore suspended user account (Admin) |