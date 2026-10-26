# ADR-017: Sign In With Google by Exchanging an Authorization Code and Auto-Linking on Verified Email

## Status

Accepted

## Date

2026-10-26

## Deciders

Backend team (Lê Đình Duy), reviewed in the design discussion for issue #31

## Context

Issue #31 asks for "Login with Google API + JWT auth": a user authorizes with
Google and receives the platform's existing JWT access/refresh pair. The JWT
half already exists — `JwtTokenService` mints a 15-minute HS256 access token and
an opaque SHA-256-hashed refresh token (ADR-002), and `AuthService.login`
assembles a `LoginResponse` from them.

What is missing is the Google identity itself. Constraints that shaped the
options:

- The API is **stateless** (`SessionCreationPolicy.STATELESS`) and authenticates
  with a custom bearer filter (`JwtAuthenticationFilter` →
  `JwtAuthenticationProvider`). Introducing HTTP sessions for an OAuth
  authorization-request would make part of the surface stateful and would need
  `SecurityConfig` to grow an `oauth2Login()` leg plus a success handler.
- The client is a SPA/mobile app; the user can obtain a Google authorization
  code themselves via Google Identity Services.
- `user.password_hash` is `NOT NULL` (V1), and a Google user has no password.
- `user` has no column for a provider subject, so nothing can record "this
  Google account is that Vegalife account".
- `docs/arch/dependencies.md` approves `spring-boot-starter-oauth2-resource-server`
  only in principle and lists no `oauth2-client` at all.
- Issue #31 explicitly leaves other providers for later sprints, so whatever
  records the link should not need reshaping when a second provider arrives.

## Decision

**Obtain the Google identity by exchanging a client-supplied authorization code
server-side, and store the link in a dedicated `user_identity` table keyed by
`(provider, provider_subject)`.**

Concretely:

1. `POST /api/auth/google` accepts `{ "code": "..." }`. The backend exchanges it
   at `https://oauth2.googleapis.com/token`, verifies the returned ID token
   (RS256 signature against Google's JWKS, `aud` = our client ID, `iss` ∈
   {`accounts.google.com`, `https://accounts.google.com`}, `exp`/`iat`), and
   derives the user from the claims. No redirect callback, no session, no
   new dependency — `RestClient` plus `jjwt` cover it.
2. `user_identity` holds `(id, user_id, provider, provider_subject,
   email_at_provider, created_at)` with `UNIQUE(provider, provider_subject)`
   and `UNIQUE(user_id, provider)`.
3. **Resolve order:** existing `user_identity(provider=GOOGLE, sub)` → matched
   local account's email → create new.
4. **Auto-link** a Google identity to an existing local account only when the
   ID token reports `email_verified=true`, and the matched account already has
   `email_verified=true` and `status=activated`. Every other matched state is
   rejected with the message `AuthService.login` would produce for that state.
5. A newly created account gets `email_verified=true` and `status=activated`
   (Google already proved the address), `password_hash = NULL`, and a generated
   username that satisfies BR-AUTH-006.
6. `user.password_hash` becomes nullable. `AuthService.login` treats a null
   hash as "no local password" and returns the same generic
   "Invalid email/username or password" it returns for a wrong password.
7. There is no in-session "connect Google" endpoint and no unlink endpoint.

## Considered options

- **Option A — `spring-boot-starter-oauth2-client` with `oauth2Login()` and a
  redirect callback** — standard, but needs a session to carry the
  authorization request, forces `SecurityConfig` to become partly stateful, and
  the token hand-off to a SPA still needs a redirect/code dance. Rejected for a
  token API.
- **Option B — accept a Google `id_token` directly from the client** — no code
  exchange, but accepting a token minted for any audience is a well-known
  confused-deputy problem unless `aud` is checked; the code exchange keeps the
  client secret server-side and is what Google recommends for web backends.
  Rejected in favour of the exchange.
- **Option C — hand-rolled authorize + callback endpoints with Spring Sessions
  or a signed `state`** — closer to the issue's wording ("callback endpoint"),
  but re-implements the same exchange behind a redirect the SPA does not need.
  Rejected as strictly more surface for the same result.
- **Option D — store the Google `sub` in new `user` columns
  (`google_sub`, `auth_provider`)** — smallest change, but every future provider
  adds columns and a single `auth_provider` enum cannot express "this account
  has both a password and Google". Rejected because the issue names future
  providers.
- **Option E — match on `user.email` alone, storing no link** — least code, but
  trusts a provider-asserted address as an account key with no record of the
  binding, and Google addresses can be reassigned. Rejected.
- **Option F — random unusable password hash (`bcrypt(UUID.randomUUID())`)** —
  keeps `password_hash` `NOT NULL` and leaves `login()` untouched, but writes a
  credential nobody knows and costs a BCrypt-12 hash per Google signup.
  Rejected in favour of a nullable column (Option G).
- **Option G — nullable `password_hash` + explicit null check in `login()`** —
  chosen; honest model, one guard, no per-signup CPU cost.

## Consequences

**Positive:**

- `SecurityConfig` needs no change: `/api/auth/**` is already `permitAll`
  (`SecurityConfig`), and the endpoint is stateless like the rest of the API.
- No new dependency; `RestClient` is already the repository's HTTP client
  (`GeminiEmbeddingModel`) and `jjwt` is already on the classpath.
- A second provider is a new enum value plus rows — no schema migration.
- Auto-linking and first-time creation share the existing token-issuing path,
  so AC#1–#3 of issue #31 all return the same `LoginResponse`.

**Negative / trade-offs:**

- **Auto-link trusts Google's `email_verified` for an address we already hold.**
  If a Gmail address is deleted and later re-registered, the new owner's verified
  Google sign-in will auto-link into the previous owner's Vegalife account. This
  is the accepted cost of the user's chosen rule ("email already verified and
  account active → auto link"). It is bounded by keying every subsequent login on
  `(provider, sub)`, never on the email again, and it is logged at `WARN` with
  the user id so an incident is traceable.
- The login endpoint now has to distinguish "account has no local password"
  from "wrong password". Both must produce the identical
  `"Invalid email/username or password"` response; the `login()` guard is what
  keeps that true, and it is covered by a unit test.
- `password_hash` being nullable weakens a database-level invariant; any future
  code that assumes it is non-null must be reviewed. The only such code today is
  `AuthService.login`.
- No link and no unlink endpoint by decision: a user can never attach Google to
  an account from inside a session, and can never detach it. A Google-only user
  who wants a password must use the existing forgot-password flow.
- Username generation from the email local part can collide; it is resolved by a
  save-and-retry loop on the unique constraint, which means a failed attempt
  must not have side effects outside the transaction.

## Verification

Revisit this decision if any of the following holds:

- A second identity provider is added: if `user_identity` needs no change, this
  ADR's provider-agnostic claim held.
- An account-takeover report cites a reassigned Gmail address. That would
  justify adding a recency guard (auto-link only if the local account was
  created more than, say, 24 hours ago) or requiring a password sign-in before
  linking.
- Google changes ID token verification requirements or the token endpoint.

## Related

- Feature spec: `docs/feats/google-login.md`
- API reference: `docs/apis/auth/post-google.md`
- Business rules: `docs/brs/auth.md` (BR-AUTH-022, BR-AUTH-023)
- Predecessor: `docs/adrs/002-jwt-authentication-architecture.md`
