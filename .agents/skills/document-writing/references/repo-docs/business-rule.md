# Business Rules Template

## When to use
Use this template for documenting business rules. **Group all rules for one feature/domain into a single physical file** named `docs/brs/<feature-name>.md`.

## Rule ID Format
`BR-<FEATURE>-<NNN>` where:
- FEATURE: 3-4 letter uppercase code (AUTH, USER, RECP, CONT, etc.)
- NNN: 3-digit zero-padded sequence (001, 002, 003...)

Examples:
- `BR-AUTH-001` — Auth: Unique email/username
- `BR-AUTH-002` — Auth: Password confirmation match
- `BR-USER-001` — User: Profile visibility
- `BR-RECP-001` — Recipe: Public/private visibility

## Index format
In `docs/brs/index.md`, list each rule individually:

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-AUTH-001 | Unique User Identity | Active | 2026-09-22 |
| BR-AUTH-002 | Password Confirmation Match | Active | 2026-09-22 |
| BR-AUTH-003 | Email Verification Required | Active | 2026-09-22 |

---

# Business Rule: <Short Descriptive Name>

## Rule ID
`BR-<FEATURE>-<NNN>`

## Status
Active | Deprecated | Superseded by BR-XXX-XXX

## Statement
<!-- The rule itself, stated precisely enough to be unambiguous. Avoid "usually" or "in most cases" — make exceptions explicit. -->

## Rationale
<!-- Why this rule exists — business, legal, or product reason. Not "because the code does this." -->

## Scope & Exceptions
<!-- Who/what this applies to, and any explicit exceptions. -->

## Enforcement
<!-- Where this is enforced in the system — link to code/config. If not yet enforced in code (process-only), say so explicitly. -->

## Last Reviewed
YYYY-MM-DD, by <name/role>

---

## Example

# Business Rule: Unique User Identity

## Rule ID
`BR-AUTH-001`

## Status
Active

## Statement
A user's email and username must each be unique across the system.

## Rationale
Prevents account confusion, ensures reliable login/identification, and supports password recovery via email.

## Scope & Exceptions
Applies to all user registrations. No exceptions.

## Enforcement
- `AuthService.register()` checks `userRepository.existsByEmail()` and `existsByUsername()` before creation
- Database: Unique constraints on `user.email` and `user.username`
- API: Returns 409 Conflict with message "Email already registered" or "Username already taken"

## Last Reviewed
2026-09-22, by <name/role>