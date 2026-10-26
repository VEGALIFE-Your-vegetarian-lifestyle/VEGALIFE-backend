# Feature Spec: Sign In with Google

## Status

In progress

## Author / owner

Lê Đình Duy (backend), from issue #31

## Summary

Let a member sign in to Vegalife with their Google account and receive the same
JWT access/refresh pair the email/password login issues. An existing account is
reused and linked when Google's verified email matches it; a brand-new Google
account gets a new Vegalife account and is signed straight in.

## Problem / motivation

Today the only way into a Vegalife account is to register with a username, an
email address, a password, and then verify a 6-digit OTP. That friction is the
one thing between a visitor and their first post. Issue #31 records the product
ask: "Users want quick sign-in without creating new credentials. Increases
conversion and reduces password fatigue."

This is applicable because the JWT half already ships — `JwtTokenService` and
`AuthService.login` — so the missing work is only the Google identity and the
"which Vegalife account does this belong to" question. See ADR-017.

## Goals

- A member can sign in with Google and receive a working access + refresh pair.
- An existing account whose email matches a verified Google account is reused,
  not duplicated: after linking, both sign-in methods resolve to one user id.
- A first-time Google user gets a usable account with no email-verification
  loop, because Google already proved the address.
- The API stays stateless and needs no new dependency.

## Non-goals

- Any other OAuth provider (issue #31: "future sprints"). The storage shape is
  provider-agnostic, but only Google is wired.
- Account-linking UI (issue #31 non-goal) **and** an in-session "Connect Google"
  endpoint — decided out of scope for this change.
- Unlinking Google from an account.
- Setting a password on a Google-created account from a new endpoint; the
  existing forgot-password flow already covers that.
- Google One Tap client-side integration; the client may use any GIS mechanism
  that yields an authorization code.

## Requirements

### Functional Requirements

- [ ] FR-001: `POST /api/auth/google` accepts a Google authorization code and
  returns the standard `LoginResponse` (access token, refresh token,
  `tokenType=Bearer`, access-token `expiresIn`).
- [ ] FR-002: When the ID token's `sub` is already present in `user_identity`
  for provider `GOOGLE`, that linked user is signed in; no lookup by email is
  performed.
- [ ] FR-003: When no identity row exists but the ID token's email matches an
  existing account that is `email_verified=true` and `status=activated`, the
  backend inserts the identity row (auto-link) and signs that user in.
- [ ] FR-004: When no identity row exists and no account matches the email, a
  new account is created with `email_verified=true`, `status=activated`,
  `role=USER`, `password_hash=NULL`, and a generated unique username; the
  identity row is inserted; the user is signed in.
- [ ] FR-005: An ID token whose `email_verified` claim is not `true` is
  rejected with HTTP 400 and no account is created or linked.
- [ ] FR-006: An identity that resolves to an account in a state that password
  login would refuse (`email_verified=false`, `deactivated`, `suspended`,
  soft-deleted) is rejected with the same message password login gives for that
  state, and no identity row is inserted.
- [ ] FR-007: A generated username is 3-50 characters, unique, and derived from
  the email local part; collisions are resolved by retrying against the unique
  constraint.
- [ ] FR-008: `POST /api/auth/login` with an email/username whose
  `password_hash` is NULL returns HTTP 400 "Invalid email/username or password"
  — never a 500, and never a message that reveals the account is Google-only.
- [ ] FR-009: `POST /api/auth/google` is reachable without authentication;
  every other endpoint's authorization is unchanged.

### Non-Functional Requirements

- [ ] NFR-SCALE-001: The endpoint adds no per-request database work beyond the
  identity/user lookups it needs, and no new long-lived in-memory state.
- [ ] NFR-SEC-001: The Google client secret, access token, and ID token are never
  logged, echoed in an error response, or persisted (BR-MCP-004's posture).
- [ ] NFR-SEC-002: ID token signatures are verified against Google's published
  JWKS and cached; a token that fails signature, issuer, audience, or expiry
  validation is rejected before any database access.
- [ ] NFR-MAINT-001: The provider is modelled as an enum value, so a second
  provider adds code but not a migration.
- [ ] NFR-PERF-001: One code exchange plus one JWKS fetch (cached afterwards)
  per sign-in; the endpoint's latency is dominated by the Google round-trip.

## Design overview

The flow lives in `service/auth/GoogleAuthService`, called by
`AuthController` via `POST /api/auth/google`. It has three collaborators:

- `infrastructure/google/GoogleTokenExchanger` — posts the code to Google's
  token endpoint and returns the raw `id_token`.
- `infrastructure/google/GoogleIdTokenVerifier` — verifies the ID token
  (RS256 against JWKS, `aud`, `iss`, `exp`, clock skew) and returns a
  `GoogleUserInfo` (`sub`, `email`, `emailVerified`, `name`).
- `JwtTokenService` — unchanged, reused to mint the access and refresh tokens.

Account resolution is a single decision tree over
`UserIdentityRepository` and `UserRepository`, with the token issuance
extracted so `AuthService.login` and `GoogleAuthService` share it. The
`user_identity` table is new (`V44`), and `user.password_hash` becomes nullable
in the same migration.

Decisions and trade-offs are recorded in `docs/adrs/017-google-login.md`.

## Success metrics

No analytics exist in this backend yet. The comparable signal after launch is
the share of `POST /api/auth/google` calls that return 200 against the total
login volume, and the count of `WARN` auto-link lines
(`Google identity auto-linked to existing user`) — the latter should stay
proportional to the number of first-time Google sign-ins, not grow with
repeat sign-ins.

## Acceptance criteria

**As a** visitor who does not want to create a password, **I want to** sign in
with my Google account, **so that** I can start using Vegalife immediately.

- [ ] Given no Vegalife account, when I authorize with Google, then an account
  is created with my Google email and I receive a JWT pair (issue #31 AC#1, AC#3).
- [ ] Given an account that already has Google linked, when I sign in with
  Google again, then that account is used, no second account exists, and the
  returned `userId` is unchanged (issue #31 AC#2).
- [ ] Given a password account with a verified email, when I sign in with Google
  using the same verified email, then the identity is linked to that account and
  the returned `userId` is the existing one (issue #31 AC#2 via auto-link).
- [ ] Given an account with an unverified email, when its email signs in with
  Google, then I get the "verify your email" rejection and no link is created.
- [ ] Given a suspended account, when its email signs in with Google, then I get
  the suspension rejection and no link is created.
- [ ] Given a Google-only account, when I try to sign in with email and password,
  then I get the standard invalid-credentials rejection, not a server error.
- [ ] The code exchange, signature verification, audience and issuer checks happen
  server-side before any account lookup (issue #31 AC#4).

## Risks / open questions

- **Reassigned Gmail addresses** — auto-linking trusts Google's
  `email_verified`. A deleted-then-re-registered address could link into the
  previous owner's account. Accepted in ADR-017; bounded by keying repeat logins
  on `sub` only.
- **No unlink path** — by decision there is no way to detach Google. If a user
  links into the wrong account there is no self-service remedy.
- **Client contract unresolved** — the frontend must send the Google
  authorization *code* (with `redirect_uri` matching what was registered), not
  an ID token. This spec assumes that contract; it needs confirming with the
  frontend team before ship.
- **JWKS availability** — Google's keys are fetched over the network. A JWKS
  outage makes Google sign-in fail (password login unaffected). Acceptable per
  NFR-MAINT-001's scope.
