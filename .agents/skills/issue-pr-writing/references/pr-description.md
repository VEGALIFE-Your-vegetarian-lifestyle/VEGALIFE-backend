# <PR title>

## What changed

<!-- One or two sentences, plain language. -->

## Why

<!-- Link the issue/ticket, or explain the motivation if there isn't one. -->

**Related issue(s):** <!-- e.g. Closes #123 — GitHub auto-closes the
linked issue when this PR merges. Use "Relates to #123" instead if it
shouldn't auto-close. -->

## Type of change

<!-- Feature | Bugfix | Refactor | Docs | Chore -->

## How it was verified

<!-- Required — must reflect commands actually run this session, not
assumed. Follow the verification gate of whichever workflow produced
this change. -->

- Build: `<command>` → <result>
- Tests: `<command>` → <result>
- Lint: `<command>` → <result>

## Checklist

- [ ] Tests added or updated for this change
- [ ] Docs updated if behavior or setup changed
- [ ] No secrets, credentials, or debug logging left in the diff
- [ ] Verification results above are from an actual run this session

## Risk / rollback

<!-- What could go wrong, and how to roll back if it does. Required for
anything touching prod config, migrations, or .agents/ / AGENTS.md. -->

## Notes for reviewer

<!-- Anything a reviewer should pay special attention to. -->

---

## Example

# Fix cart total resetting to $0.00 after applying discount code

## What changed
Included the active discount code in the cart summary's auto-refresh
payload, and added a defensive null check, so the discount total no
longer disappears a few seconds after being applied.

## Why
Checkout was silently dropping the discount total mid-session, causing
customers to see $0.00 and either abandon the cart or contact support.

**Related issue(s):** Closes #482

## Type of change
Bugfix

## How it was verified
- Build: `npm run build` → succeeded, no errors
- Tests: `npm test -- cart-summary` → 14 passed, 0 failed (new
  regression test included)
- Lint: `npm run lint` → no new violations

## Checklist
- [x] Tests added or updated for this change
- [ ] Docs updated if behavior or setup changed — N/A, no user-facing
      docs reference this flow
- [x] No secrets, credentials, or debug logging left in the diff
- [x] Verification results above are from an actual run this session

## Risk / rollback
Low risk — change is scoped to one component's refresh payload. Rollback
is a straight revert of this PR; no migration or data change involved.

## Notes for reviewer
Worth double-checking the null-check fallback doesn't mask a *different*
future bug silently — it currently just skips the discount line instead
of erroring, which was a deliberate trade-off for now.
