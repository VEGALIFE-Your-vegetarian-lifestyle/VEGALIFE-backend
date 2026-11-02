# ADR-019: Replace the Unique `username` Handle With a Non-Unique `display_name`

## Status

Accepted

## Date

2026-11-02

## Deciders

Backend team, on the request to "drop `username` entirely and use a
`display_name` instead".

## Context

`user.username` was introduced by the registration design in ADR-001 as a
self-chosen, globally unique handle: `VARCHAR(50)`, a unique index
(`user_username_key`), and a `3–50` character rule (BR-AUTH-006). It served two
purposes at once — an identifier to sign in with, and the name shown next to a
post or a comment.

Both purposes broke down:

- **As an identifier** it was redundant. `email` is already unique and is what
  verification, password reset and Google auto-linking key on (ADR-017). Every
  flow that needed an identifier already had one.
- **As a label** uniqueness was actively harmful. Two people called "Minh" could
  not both register, and Google-created accounts needed a derived, collision-free
  handle (`candidateUsername`, `_1`, `_2` suffixes) purely to satisfy an index
  that carries no meaning for a reader.

The visible name also has to survive Google sign-in, which never enters a
username: the account is created from a token, not a form. Deriving a unique
slug from that token is exactly the kind of work that produced `john_doe_3` in
the UI.

## Decision

**`user.username` is removed. A single non-unique `user.display_name` column
takes its place.**

- Column: `display_name VARCHAR(100) NOT NULL`, no unique constraint.
- Existing rows keep their value verbatim: the migration renames the column in
  place (`ALTER TABLE user RENAME COLUMN username TO display_name`), so
  `john.doe` stays `john.doe` — no suffixing, no concatenation, no backfill
  statement.
- Registration requires a `displayName` (`@NotBlank`, `2–100` characters) and
  rejects only a duplicate *email*. Duplicate display names are allowed.
- Login takes an email only. The login error message is `Invalid email or
  password`; it no longer mentions a username, because no such identifier
  exists. Submitting a nickname simply fails like any unknown email.
- Google-created accounts are seeded with the name Google asserts, resolved in
  this order: the `name` claim → `given_name` + `family_name` → the email local
  part → `Vegalife user`. The value is trimmed and capped at 100 characters
  without splitting a surrogate pair. It is written **only** when the account is
  created; later Google logins never touch it, so a member's chosen name is not
  overwritten by a Google profile edit.
- The name is a label, so it is returned wherever a member's identity is shown:
  auth responses, profile responses, comment threads, admin listings, and post
  responses (which now carry `userId`, `displayName` and `avatarUrl`).
- The JWT claim that carried `"username"` is renamed `"displayName"`. Nothing
  read it back — `sub` is the subject — so the rename is cosmetic for clients
  that ignore it.

### Migration safety for stored JSON

`outbound_message.payload` rows written before this change contain a
`"username"` key (email OTP and receipt payloads). `OutboundEmailPayload`
renames its field to `displayName` and declares
`@JsonAlias("username")`, so rows already on the queue still deserialize and
drain normally across the deploy.

### Legacy admin sort parameter

`GET /api/admin/users?sort=username,asc` was a documented parameter. Renaming
the column would turn it into a Spring Data `PropertyReferenceException` → 500.
`AdminService` translates a leading `username` sort key to `displayName` for
one release, so existing clients keep working; new clients use `displayName`.

## Consequences

Positive:

- One column, one meaning: the name people see. No uniqueness machinery, no
  slug derivation, no `_3` suffixes.
- Google sign-in seeds a human-readable name directly, with an explicit,
  testable fallback chain.
- Registration loses a 409 path (`Username already taken`) and a lookup
  (`existsByUsername`); login loses a second query path.

Negative / trade-offs:

- Display names are ambiguous by design — two members can share one. Anything
  that needs to address a specific member must use `userId`, never the name.
- Existing clients that send `username` on registration break. That is the
  intent (the field no longer exists), and it is a compile-time/validation-time
  failure, not a silent rename.
- Legacy `?sort=username` survives only as a translated alias; it should be
  dropped once clients migrate.
- Anyone who used to sign in with their username must now use their email.

## Alternatives considered

- **Keep `username` as a unique handle and add `display_name` beside it.** Two
  name fields, and the handle still has to be derived for Google accounts —
  the problem is retained, not solved.
- **`first_name` / `last_name` columns.** Splits a name along a convention that
  does not hold in many cultures, and makes the response shape depend on two
  fields where one display string is wanted.
- **Keep the unique index on `display_name`.** Reintroduces the collision
  problem and forces suffixing for Google accounts, which is the outcome the
  change exists to remove.

## References

- ADR-001 — User Registration Architecture Decisions
- ADR-002 — JWT Authentication Architecture
- ADR-005 — Persistent Outbound Message Queue
- ADR-017 — Sign In With Google
- `docs/brs/auth.md` — BR-AUTH-001 (identifier), BR-AUTH-006 (retired)
- `docs/arch/data-dictionary.md` — `user.display_name`
