# Workflow: Bugfix

## Required inputs

- A description of the bug: expected vs. actual behavior.
- Steps to reproduce, if available.

## Steps

1. **Reproduce** — confirm the bug actually occurs as described before
   changing anything. If it can't be reproduced, say so rather than
   guessing at a fix. Record the report: severity, expected vs. actual
   behavior, steps to reproduce, environment.
2. **Diagnose** — locate the root cause, not just the symptom. Record it
   in the bug report.
3. **Confirm approach with the user** — for anything beyond an obvious
   one-line fix, lay out the intended fix approach and get explicit
   confirmation before branching/planning. Don't assume a root cause
   this significant is fine to fix a specific way without checking.
4. **Branch & plan** — create a dedicated branch for this fix. For
   anything beyond a trivial one-line change, write a short working
   plan breaking the fix into commit-sized phases (e.g. failing test as
   one commit, the fix itself as another) — never commit the plan
   itself.
5. **Write a failing test** — pick unit-level or a higher-level test
   based on where the bug actually reproduces (per the steps to
   reproduce), add a test that fails because of the bug, before fixing
   it.
6. **Fix** — keep the change minimal and scoped to the root cause.
7. **Verify** — re-run the new test (now passing) plus the full relevant
   test suite, using the actual commands from `AGENTS.md`. Confirm the
   fix didn't break anything else.
8. **Review** — check security/safety first, then correctness,
   convention adherence, whether the verification gate above was
   actually met, and scope creep.
9. **Write the PR description** — what changed, why, how it was
   verified, and any risk/rollback notes.

## Expected artifacts

- A bug report, root cause filled in after diagnosis.
- A regression test that fails before the fix and passes after.
- The fix itself, scoped to the root cause.
- A PR description including root cause and how it was verified.

## Verification gates

- The regression test fails on the pre-fix code and passes on the
  post-fix code — both observed, not assumed.
- Full relevant test suite still passes after the fix.
- `./mvnw test` → all tests pass, N passed / 0 failed
- `./mvnw checkstyle:check` → no new violations
- `./mvnw spotless:check` → no formatting changes needed

## Conditions for stopping or requesting human input

- The bug can't be reproduced.
- The root cause implicates a design decision bigger than a local fix.
- The user hasn't yet confirmed the fix approach for anything beyond a
  trivial one-line change.
- Fixing it safely would require touching prod data, secrets, or a
  destructive operation.
