# Feature Spec: <name>

## Status

<!-- One of: Draft | In review | Approved | In progress | Shipped -->
Draft

## Author / owner

<!-- Who wrote this and who owns the decision if it's contested. -->

## Summary

<!-- One or two sentences: what this feature does and for whom. -->

## Problem / motivation

<!-- What's broken or missing today without this feature? Include
evidence if available — a metric, a support ticket volume, user
feedback — not just an assertion. -->

## Goals

<!-- What this feature is meant to achieve, stated as outcomes. -->

-

## Non-goals

<!-- Explicitly out of scope, to prevent scope creep during
implementation. -->

-

## Requirements

### Functional Requirements
<!-- Concrete, testable statements. Avoid vague language — state a number or condition where possible. Use FR-### format. -->

- [ ] FR-001: ...
- [ ] FR-002: ...

### Non-Functional Requirements
<!-- Include scalability, security, maintainability for all features.
     Add performance only when critical for the specific function.
     Use NFR-<CATEGORY>-### format: NFR-SCALE, NFR-SEC, NFR-MAINT, NFR-PERF. -->

- [ ] NFR-SCALE-001: ...
- [ ] NFR-SEC-001: ...
- [ ] NFR-MAINT-001: ...
- [ ] NFR-PERF-001: ... (only if critical)

## Design overview

<!-- High level: components touched, new components, data flow. Write a
separate ADR first if this involves a significant architectural
decision, rather than repeating it here. -->

## Success metrics

<!-- How you'll know this worked after shipping — a number, a rate, a
threshold, and by when. -->

## Acceptance criteria

<!-- Write this as a user story: who it's for, what they can do, and
how you'll know it works. -->

**As a** <type of user>, **I want to** <goal/action>, **so that**
<benefit/outcome>.

<!-- Given/When/Then for behavior that depends on context or state; a
plain checklist is fine for simpler cases. 3-7 criteria is typical — if
you need more than 10, this feature is probably too large for one spec
and should be split. -->

- [ ] Given <context>, when <action>, then <outcome>.
- [ ] Given <context>, when <action>, then <outcome>.

## Risks / open questions

<!-- Anything unresolved that needs a human decision before or during
implementation, or a risk worth flagging before starting. -->

-

---

## Example

# Feature Spec: Guest Checkout

## Status
Approved

## Author / owner
Duy (product), reviewed by backend + frontend leads

## Summary
Allow shoppers to complete a purchase without creating an account,
capturing only the information needed to fulfill and confirm the order.

## Problem / motivation
Checkout funnel analytics show 34% of cart abandonments happen at the
"create an account" step. Competitor sites in this market commonly offer
guest checkout, and support tickets have mentioned this specifically 12
times in the last month.

## Goals
- Reduce cart abandonment at the account-creation step.
- Let a guest order be later linked to an account if they sign up.

## Non-goals
- Guest order history / re-ordering (guests won't see past orders unless
  they create an account).
- Guest checkout for subscription products (out of scope for this phase).

## Requirements

### Functional Requirements
- [ ] FR-001: Checkout flow offers "Continue as guest" alongside "Sign in".
- [ ] FR-002: Guest checkout collects: email, shipping address, payment method — no password.
- [ ] FR-003: Order confirmation is emailed to the guest's provided address.
- [ ] FR-004: A guest's email is checked against existing accounts at checkout; if it matches, prompt to sign in instead (avoid duplicate profiles).

### Non-Functional Requirements
- [ ] NFR-SCALE-001: Support 10k concurrent guest checkouts without degradation.
- [ ] NFR-SEC-001: Guest session tokens expire after 30 minutes of inactivity.
- [ ] NFR-MAINT-001: Checkout service changes require zero-downtime deployment.
- [ ] NFR-PERF-001: Checkout API p95 latency < 500ms under peak load.

## Design overview
Reuses the existing checkout service; the "create account" step becomes
optional. New `guest_orders` linkage table maps a guest order to an
account if the guest later registers with the same email. See
`docs/adrs/0012-guest-order-account-linking.md` for the linkage decision.

## Success metrics
Cart abandonment at the account step drops below 15% within 6 weeks of
launch, measured via the existing funnel dashboard.

## Acceptance criteria
**As a** shopper without an account, **I want to** complete a purchase
without registering, **so that** I'm not blocked from buying by an
extra signup step.

- [ ] Given no account, when checking out, then a shopper can complete
      a full purchase with no account required.
- [ ] Given a completed guest purchase, when it's confirmed, then the
      guest receives an order confirmation email within 1 minute.
- [ ] Given an existing account's email is used as guest, when
      checkout is attempted, then a sign-in prompt is triggered instead
      of creating a duplicate profile.

## Risks / open questions
- Fraud/chargeback risk may be higher for guest orders — needs input
  from the payments team before launch (open).
- Should guest orders expire from search/support tools after N days? Not
  yet decided.
