# API Reference Documentation

Supplementary human-written API docs for the Vegalife backend (complements auto-generated OpenAPI/Swagger at `/swagger-ui.html`).

Each file documents one endpoint: `docs/apis/<feature>/<method>-<resource>.md`

| File | Endpoint | Description |
|------|----------|-------------|
| `auth/post-register.md` | `POST /api/auth/register` | Register new user (sends verification OTP) |
| `auth/post-verify-email.md` | `POST /api/auth/verify-email` | Verify email with 6-digit OTP |
| `auth/post-resend-email.md` | `POST /api/auth/resend-email` | Resend email-verification OTP (generic 200) |
| `auth/post-login.md` | `POST /api/auth/login` | User login |
| `auth/post-refresh.md` | `POST /api/auth/refresh` | Refresh access token |
| `auth/post-logout.md` | `POST /api/auth/logout` | User logout |
| `auth/post-forgot-password.md` | `POST /api/auth/forgot-password` | Request password-reset OTP by email |
| `auth/post-verify-password-reset.md` | `POST /api/auth/verify-password-reset` | Verify password-reset OTP (step 1 of 2) |
| `auth/post-reset-password.md` | `POST /api/auth/reset-password` | Set new password after verification (step 2 of 2) |
| `profile/put-profile.md` | `PUT /api/profile` | Update user profile |
| `profile/get-profile.md` | `GET /api/profile` | Get own profile (authenticated; auto-creates empty row) |
| `profile/get-profile-userid.md` | `GET /api/profile/{userId}` | Get a member's profile (public) |
| `post/post-posts.md` | `POST /api/posts` | Create a post for the authenticated user |
| `post/get-posts.md` | `GET /api/posts` | List the authenticated user's posts |
| `post/get-users-userid-posts.md` | `GET /api/users/{userId}/posts` | List a member's posts (public: published only; owner/Admin: all) |
| `post/get-posts-feed.md` | `GET /api/posts/feed` | Global feed of published posts, newest first (public) |
| `post/patch-posts-postid.md` | `PATCH /api/posts/{postId}` | Edit a post (owner or Admin) |
| `post/delete-posts-postid.md` | `DELETE /api/posts/{postId}` | Soft-delete a post (owner or Admin) |
| `post/patch-posts-postid-visibility.md` | `PATCH /api/posts/{postId}/visibility` | Hide or unhide a post (Admin) |
| `admin/get-users.md` | `GET /api/admin/users` | List user accounts (Admin) |
| `admin/get-posts.md` | `GET /api/admin/posts` | List all posts across users and statuses (Admin) |
| `admin/post-suspend-user.md` | `POST /api/admin/users/{userId}/suspend` | Suspend user account (Admin) |
| `admin/post-restore-user.md` | `POST /api/admin/users/{userId}/restore` | Restore suspended user account (Admin) |
| `admin/get-comments.md` | `GET /api/admin/comments` | List all comments (Admin) |
| `admin/get-recipes.md` | `GET /api/admin/recipes` | List all recipes across users with ingredients and instructions (Admin) |
| `admin/get-videos.md` | `GET /api/admin/videos` | List all uploaded videos across users and statuses (Admin) |
| `media/post-upload.md` | `POST /api/media/upload` | Issue a signed upload grant and media ID |
| `media/post-media-mediaid-confirm.md` | `POST /api/media/{mediaId}/confirm` | Verify a completed upload and finalize the media record |
| `media/get-media-mediaid.md` | `GET /api/media/{mediaId}` | Read a stored media record |
| `media/delete-media-mediaid.md` | `DELETE /api/media/{mediaId}` | Soft-delete a media record (owner or Admin); physical purge is async |
| `admin/post-categories.md` | `POST /api/admin/categories` | Create a content category (Admin) |
| `admin/patch-categories-categoryid.md` | `PATCH /api/admin/categories/{categoryId}` | Edit a content category (Admin) |
| `admin/delete-categories-categoryid.md` | `DELETE /api/admin/categories/{categoryId}` | Retire a content category (Admin) |
| `post/get-categories.md` | `GET /api/categories` | List active content categories (public) |
| `recipes/post-recipes.md` | `POST /api/recipes` | Create a recipe (dish + ingredients) for the authenticated user |
| `ingredients/get-ingredients.md` | `GET /api/ingredients` | List ingredients paginated, with case-insensitive name filter (authenticated) |
| `recipes/get-dishes.md` | `GET /api/dishes` | List active dishes for recipe-create autocomplete (authenticated) |
| `subscriptions/get-me.md` | `GET /api/subscriptions/me` | Get own AI subscription: tier, usage, plan, latest payment (authenticated) |
| `subscriptions/get-subscriptions.md` | `GET /api/subscriptions` | List active AI plans with limits and price (public) |
| `subscriptions/get-history.md` | `GET /api/subscriptions/me/history` | Paginated history of own AI subscription rows, newest first (authenticated) |
| `subscriptions/post-cancel.md` | `POST /api/subscriptions/me/cancel` | Cancel own AI subscription immediately, cascading scheduled successors (authenticated) |
| `subscriptions/post-purchase-eligibility.md` | `POST /api/subscriptions/me/purchase/eligibility` | Check whether a plan purchase/extension is allowed; read-only (authenticated) |
| `payments/post-checkout.md` | `POST /api/payments/checkout` | Start (or resume) a VNPay checkout for an AI plan (authenticated) |
| `payments/get-vnpay-ipn.md` | `GET /api/payments/vnpay/ipn` | VNPay payment notification webhook; fulfils a verified payment (public, signed) |
| `payments/get-payments-paymentid.md` | `GET /api/payments/{paymentId}` | Read one of your payments' status from the ledger (authenticated; owner only) |
| `payments/get-payments.md` | `GET /api/payments` | List own payment history, newest first, with subscription and plan context (authenticated) |
| `admin/get-payments.md` | `GET /api/admin/payments` | List payments across all users with user/status/date filters (Admin) |
| `ai/post-messages.md` | `POST /api/ai/messages` | Send a chat message, get the AI reply (authenticated) |
| `ai/post-messages-stream.md` | `POST /api/ai/messages/stream` | Send a chat message, stream the AI reply as SSE (authenticated) |
