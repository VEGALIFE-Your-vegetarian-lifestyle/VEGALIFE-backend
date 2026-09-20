---
name: requirement-analysis
description: Use whenever an incoming request is ambiguous, underspecified, or stated as a one-liner from a user/stakeholder — before writing a feature spec, feature request, or task. Covers finding the real problem behind the literal ask, separating goals from non-goals, checking each requirement is testable, and deciding whether it's feature-spec sized (needs a permanent, cross-issue reference), feature-request sized (a single-issue user-facing capability), or task sized. Produces a clarified requirement, not the final document itself.
---

# Skill: Requirement Analysis

## When to use this skill

Use whenever the incoming request is a rough idea, a one-line ask, a
verbal request relayed secondhand, or anything where scope/acceptance
criteria aren't already explicit — before any document about it gets
written. Skip it when the request already arrives as a clear, testable
spec or ticket — there's nothing to analyze.

## Required context / input

- The raw request, in whatever form it arrived.
- Existing related documents, if any, in `docs/`.
- Relevant conventions/constraints from `AGENTS.md`, to catch a request
  that conflicts with them early.

## Procedure

1. **Find the real problem, not just the literal ask.** A request like
   "add a filter to the orders page" usually stands in for a specific
   pain (support can't find refund-eligible orders fast). State that
   underlying problem explicitly — it prevents solving the wrong thing
   well.

2. **Separate goals from non-goals.** Write down what this is explicitly
   *not* trying to do — most scope creep starts from never having said
   this out loud.

3. **Make every requirement testable.** Rewrite vague statements
   ("should be fast," "better UX") as a number, a condition, or a
   Given/When/Then scenario. A requirement that can't be turned into a
   test isn't ready to hand off yet.

4. **Size it**, with a reason stated, not just asserted:
   - Spans multiple issues, needs a permanent cross-issue reference
     (success metrics, design overview) → feature-spec sized.
   - One user-facing capability, fits a single GitHub issue →
     feature-request sized.
   - A specific implementation-facing unit, no user angle needed → task
     sized.

5. **Surface conflicts and gaps instead of silently resolving them.** If
   the request contradicts an existing convention, an existing feature,
   or itself, or if a genuine decision is missing (not just detail),
   list it as an open question for a human — don't guess and move on.

6. **Present the clarified requirement and get explicit user
   confirmation before handing off.** Don't treat producing the
   clarified requirement as the finish line — lay out the intended
   direction (problem, sizing, key open questions) and wait for the
   user to confirm it. Only a confirmed direction gets handed off to
   write the actual document or a plan; an unconfirmed one stops here.

## Relevant project conventions

- **Requesters**: Team members submit requests via GitHub Issues (Task /
  Bug / Feature types). External stakeholder requests come through the
  team's communication channel and get converted to issues.
- **Intake process**: If the request arrives as a GitHub Issue, link to
  it. If it arrives as chat/verbal, create a draft issue or note the
  source in the clarified requirement.
- **Conflict escalation**: If the request contradicts an existing feature
  or convention, flag it as an open question in the clarified requirement
  and wait for user confirmation before proceeding. Do not silently
  override existing behavior.
- **Sizing guidance**: For this project, most user-facing capabilities
  are feature-request sized (single GitHub issue). Cross-cutting
  concerns (auth, notifications, AI integration) tend to be feature-spec
  sized. Pure implementation tasks (add an index, refactor a method) are
  task sized.

## Verification steps

- Every requirement produced is independently testable — no "faster,"
  "better," "robust" left unresolved into a number or condition.
- Goals and non-goals are both stated explicitly, not left implicit.
- Every genuine conflict or missing decision found is listed as an open
  question, not quietly decided on the agent's own judgment.
- The sizing decision (feature-spec vs feature-request vs task) is
  stated with a reason.
- The user has explicitly confirmed the direction — not merely been
  informed of it — before any document gets written.

## Expected artifacts / output

- A clarified requirement — problem statement, goals, non-goals,
  testable requirements, sizing decision, and open questions — plus the
  user's explicit confirmation of it. This skill does not produce the
  final document itself.
