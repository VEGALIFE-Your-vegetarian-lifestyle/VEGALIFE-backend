# Workflow: Refactor

## Required inputs

- What's being refactored and why (readability, performance, removing
  duplication, enabling a future change, etc.).
- Confirmation that behavior should NOT change (a refactor that changes
  behavior is a feature or bugfix, not a refactor).

## Steps

1. **Baseline** — run the existing test suite first and confirm it
   passes before touching anything, using the actual command from
   `AGENTS.md`. This is the behavior contract the refactor must preserve.
2. **Determine target structure** — for non-trivial refactors, work out
   the target structure and the reasoning behind it before writing
   anything down or touching code.
3. **Confirm direction with the user** — lay out the target structure
   and trade-offs, and get explicit confirmation before recording a
   decision or planning phases. Don't record or plan against an
   assumed direction.
4. **Record the decision** — if the refactor is significant or hard to
   reverse, write it down as a decision record now that it's confirmed.
5. **Plan** — create a dedicated branch for this refactor and break the
   work into commit-sized phases, each one independently buildable and
   revertible — write this as a working plan, never committed itself.
6. **Refactor** — follow the plan one phase at a time, one commit per
   phase, preferring small reviewable steps over one large rewrite.
7. **Verify no behavior change** — re-run the same test suite from step 1
   and confirm it still passes, unchanged in what it asserts (don't edit
   tests to make a broken refactor pass, unless the tests themselves were
   the target of the refactor).
8. **Review** — check security/safety first, then correctness,
   convention adherence, whether the change is genuinely
   behavior-preserving, and scope creep.
9. **Write the PR description** — what structure changed, confirmation
   that behavior is unchanged, and verification evidence.

## Expected artifacts

- A decision record (for non-trivial refactors) of the target structure.
- A working plan broken into commit-sized phases (not committed).
- The refactored code.
- A PR description stating what structure changed and confirming
  behavior is unchanged, with verification evidence.

## Verification gates

- Full test suite passes before and after, using the actual command from
  `AGENTS.md` — both runs observed this session.
- No test assertions were weakened or removed to force a pass, unless
  that was explicitly the point of the task.
- `./mvnw test` → all tests pass, N passed / 0 failed
- `./mvnw checkstyle:check` → no new violations
- `./mvnw spotless:check` → no formatting changes needed

## Conditions for stopping or requesting human input

- The "refactor" turns out to require a behavior change to work.
- The user hasn't yet confirmed the target structure for a non-trivial
  refactor — don't record a decision or plan phases on an assumed
  direction.
- No test coverage exists for the code being refactored — flag this
  before proceeding rather than refactoring blind.
- The refactor's scope grows beyond what was originally agreed.
