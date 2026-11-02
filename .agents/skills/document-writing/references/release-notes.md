# Release Notes: v<version> — <date>

## Summary

<!-- One or two sentences: the headline of this release. -->

## New features

<!-- User-facing additions. Link the feature spec if one exists. -->

-

## Improvements

<!-- Enhancements to existing behavior, not brand-new capability. -->

-

## Bug fixes

<!-- Link the bug report if one exists. -->

-

## Breaking changes

<!-- Anything that requires action from users/integrators to keep
working. If none, say "None" explicitly rather than omitting the
section. -->

-

## Upgrade notes

<!-- Migration steps, config changes, or deprecations relevant to
upgrading from the previous version. -->

---

## Example

# Release Notes: v2.15.0 — 2026-05-10

## Summary
Adds guest checkout, fixes the discount-total display bug, and includes
a database migration for the new session storage backend.

## New features
- Guest checkout — customers can complete a purchase without creating an
  account. See `docs/feats/guest-checkout.md`.

## Improvements
- Checkout page load time reduced by ~300ms by lazy-loading the payment
  widget.

## Bug fixes
- Fixed order total showing $0.00 after applying a discount code
  (see postmortem-free bug report: `docs/bugs/2026-04-cart-discount.md`).

## Breaking changes
None.

## Upgrade notes
This release includes a database migration (`0042_add_guest_orders.sql`)
that must run before deploying the new app version. Sessions now read
from Redis instead of in-memory storage (see ADR-0007); no action
needed from operators beyond confirming the Redis cluster has capacity
headroom.
