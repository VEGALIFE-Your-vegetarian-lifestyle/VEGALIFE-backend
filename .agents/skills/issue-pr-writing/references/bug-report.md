# <Symptom, stated declaratively — e.g. "Order total shows $0.00 after applying a discount code">

<!-- No type label in the title — GitHub's own Issue Type field already
carries that. Set Issue Type to Bug when creating this issue. State the
symptom itself, not an instruction to fix it — "X does not work",
not "Fix X". -->

## Severity / priority

<!-- E.g. Critical (prod down) / High / Medium / Low. If the Project
board has a Priority field, set it there — note it here too only if
the Project isn't set up yet. -->

## Expected behavior

<!-- What should happen. -->

## Actual behavior

<!-- What actually happens. Be specific — include error messages, stack
traces, or screenshots/logs if relevant. -->

## Steps to reproduce

1.
2.
3.

## Environment

<!-- Version, branch/commit, OS, environment (local/staging/prod), and
how often it reproduces (always / intermittent — include a rough rate). -->

## Root cause

<!-- Filled in during diagnosis, not at report time — leave blank until
that's actually done. -->

## Fix summary

<!-- Filled in after the fix — what changed and why it addresses the root
cause, not just the symptom. -->

## Regression test

<!-- Link or reference to the test added that fails before the fix and
passes after. -->

---

## Example

# Order total shows $0.00 after applying a discount code

<!-- Issue Type: Bug -->

## Severity / priority
High — affects checkout, workaround exists (remove and re-apply code),
first reported 2026-04-02

## Expected behavior
After applying a valid discount code, the order total updates to reflect
the discount, showing the correct reduced amount.

## Actual behavior
The order total briefly flashes the correct discounted amount, then
resets to $0.00. Console shows:
`TypeError: Cannot read properties of undefined (reading 'amount')` in
`CartSummary.tsx:88`. Removing and re-applying the code temporarily fixes
it until the cart re-renders.

## Steps to reproduce
1. Add any item to cart.
2. Go to checkout, enter a valid discount code (e.g. `SAVE10`), apply it.
3. Wait ~2 seconds for the cart summary to auto-refresh.

## Environment
Production, v2.14.0, reproduces consistently (100%) on Chrome and
Firefox; not yet tested on Safari.

## Root cause
The cart summary refresh call re-fetches pricing but doesn't include the
applied discount in the refetch payload, so the discount object is
`undefined` on re-render while the UI still tries to read `.amount` from
it.

## Fix summary
Included the active discount code in the refresh payload so the
discount object is always present on re-render; added a null check as a
defensive fallback.

## Regression test
`cart-summary.test.tsx` — "retains discount total after auto-refresh"
(added, fails on pre-fix code, passes after).
