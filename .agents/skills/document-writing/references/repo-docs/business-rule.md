# Business Rule: <short name>

## Status

<!-- Active | Deprecated | Superseded by <rule name> -->
Active

## Statement

<!-- The rule itself, stated precisely enough to be unambiguous. If you
need "usually" or "in most cases" to state it, the exceptions below
aren't captured yet — make them explicit instead. -->

## Rationale

<!-- Why this rule exists — a business, legal, or product reason. Not
"because the code does this" — the rule should exist independently of
any particular implementation. -->

## Scope & exceptions

<!-- Who/what this applies to, and any explicit exceptions. A rule with
unstated exceptions is a rule someone will implement wrong. -->

## Enforcement

<!-- Where this is actually enforced in the system — link to the
code/config, don't duplicate the logic here. If it's not enforced in
code yet (process-only, or pending implementation), say so explicitly. -->

## Last reviewed

<!-- Date, and by whom. A business rule that's never reviewed again is
how stale rules end up silently violated. -->

---

## Example

# Business Rule: Order Cancellation Window

## Status
Active

## Statement
An order can be cancelled by the customer, with a full refund, only
within 24 hours of the order being placed. After 24 hours, cancellation
requires manual support approval and is not guaranteed.

## Rationale
Orders are handed off to the fulfillment partner after 24 hours, at
which point cancellation costs the company a restocking fee. The
24-hour window balances customer flexibility against that cost.

## Scope & exceptions
Applies to all standard orders. Does not apply to:
- Pre-order items (cancellable until 24 hours before the ship date
  instead).
- Orders flagged as fraud-review — these can be cancelled by support at
  any time regardless of window.

## Enforcement
Enforced in `OrderService.cancelOrder()` — see `docs/adr/0007-...` for
the related architecture decision on how the window is computed. Support
manual overrides are a process (support tooling), not code-enforced.

## Last reviewed
2026-01-15, by Duy (product) and support lead.
