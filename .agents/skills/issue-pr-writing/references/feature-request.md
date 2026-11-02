# <Title — imperative mood, e.g. "Add one-click reorder from order history">

<!-- No type label in the title — GitHub's own Issue Type field already
carries that. Set Issue Type to Feature when creating this issue.

This template is for a feature small enough to stay a single GitHub
issue — no formal "As a ... I want ... so that ..." framing required;
state the outcome and reason directly. If this feature spans multiple
issues and needs a permanent, cross-issue reference (success metrics,
design overview, several sub-issues), write a feature-spec instead and
commit it to docs/ — this issue can then link to it. -->

## Why

<!-- 1-2 sentences: the user-facing outcome and why it matters. -->

## Acceptance criteria

<!-- Given/When/Then for behavior that depends on context or state; a
plain checklist is fine for simpler cases. 3-7 criteria is typical — if
you need more than 10, this is probably feature-spec sized, not a
single issue. -->

- [ ] Given <context>, when <action>, then <outcome>.
- [ ] Given <context>, when <action>, then <outcome>.

## Non-goals

<!-- What this explicitly does not cover, to prevent scope creep. -->

-

## Parent

<!-- If this belongs under a larger feature/epic, link it as a GitHub
sub-issue rather than plain text — see task.md's note on sub-issues. -->

## Notes

<!-- Links: related issues, design mockups, discussion threads. -->

---

## Example

# Add one-click reorder from order history

<!-- Issue Type: Feature -->

## Why
Customers currently have to manually re-add every item from a past
order. A one-click "Reorder" button on order history reduces friction
for repeat purchases of the same items.

## Acceptance criteria
- [ ] Given a past order, when the customer clicks "Reorder", then all
      still-available items from that order are added to the cart.
- [ ] Given an item in the order is no longer available, when
      reordering, then that item is skipped and the customer sees which
      items were skipped and why.
- [ ] Given the cart already has items, when reordering, then the
      reordered items are added alongside them, not replacing them.

## Non-goals
- Reordering with modified quantities in one click (out of scope —
  customer adjusts quantities in cart afterward as normal).
- Partial reorder (picking specific items from a past order) — this is
  "reorder everything available", not a picker.

## Parent
Sub-issue of #071 (Order history improvements).

## Notes
Related to #058 (inventory availability check, reused here).
