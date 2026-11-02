# <Title — imperative mood, e.g. "Add rate limiting to the /login endpoint">

<!-- No type label in the title — GitHub's own Issue Type field already
carries that. Set Issue Type to Task when creating this issue. If this
is actually a Feature or a Bug, use feature-request.md or
bug-report.md instead — their fields don't fit this template. -->

## Description

<!-- What needs to be done and why, in a sentence or two. If this task
came from a feature spec or feature request, link it instead of
repeating it. -->

## Acceptance criteria / Definition of done

<!-- Specific, testable conditions — not vague. 3-7 items is typical; if
you need more than 10, this task is probably too large and should be
split. -->

- [ ]
- [ ]

## Parent

<!-- If this task belongs under a larger feature/epic, link it as a
GitHub sub-issue (via "Add sub-issue" on the parent, or "Convert to
sub-issue" here) rather than writing the link as plain text — sub-issue
progress rolls up automatically. Only fall back to a plain-text link if
sub-issues aren't set up yet. -->

## Dependencies

<!-- Other issues that must land first, or that this blocks. Use
GitHub's "Blocked by" / "Blocks" issue relationship if available;
otherwise link plainly here. -->

## Project fields

<!-- Priority, Size/Estimate, Status, Iteration, and Labels normally
live as GitHub Project fields (set on the Project board) or repo
Labels — not written here. Only note a value in this section if the
Project isn't set up yet and it needs to be tracked somewhere. -->

---

## Example

# Add rate limiting to the /login endpoint

<!-- Issue Type: Task -->

## Description
The `/login` endpoint currently has no rate limiting, allowing
unrestricted repeated attempts. Add per-IP rate limiting to slow down
brute-force attempts, using the existing rate-limit middleware already
used on `/password-reset`.

## Acceptance criteria / Definition of done
- [ ] `/login` rejects with 429 after 10 failed attempts from the same
      IP within 5 minutes.
- [ ] Successful logins do not count toward the failed-attempt limit.
- [ ] Rate limit resets after the 5-minute window elapses.
- [ ] Existing `/password-reset` rate limiting is unaffected.
- [ ] Unit tests cover the limit boundary (9th, 10th, 11th attempt).

## Parent
Sub-issue of #104 (Account security hardening).

## Dependencies
None — reuses existing middleware from #98.

## Project fields
Priority: P1, Size: 3 — set on the Project board.
