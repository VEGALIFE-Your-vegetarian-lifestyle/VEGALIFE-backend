# Workflow: Bugfix

## Required inputs

- A description of the bug: expected vs. actual behavior.
- Steps to reproduce, if available.

## Steps

1. **Reproduce** — confirm the bug actually occurs as described before
   changing anything. If it can't be reproduced, say so rather than
   guessing at a fix. Record the report with severity, expected vs.
   actual behavior, steps to reproduce, and environment on a GitHub
   Issue (Type: Bug) — that is where bug reports live, not a file
   under `docs/`. If no driving issue exists yet, create that one Bug
   issue now; if one already exists, use it as the report and do not
   open a duplicate.
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
   itself. Link the driving Bug issue in the plan.
5. **Write a failing test** — pick unit-level or a higher-level test
   based on where the bug actually reproduces (per the steps to
   reproduce), add a test that fails because of the bug, before fixing
   it.
6. **Fix** — keep the change minimal and scoped to the root cause. If
   the fix changes HTTP/API surface, update or write the API reference
   under `docs/apis/` so it matches what will exist after the fix. If
   it establishes or changes a constraint that should outlive this
   fix, write a business rule under `docs/brs/`. After any permanent
   doc under `docs/`, add or update that subdirectory's `index.md` row
   in the same change. If the approved fix requires a recorded design
   decision (see stop conditions), write a decision record as well and
   index it.
7. **Verify** — re-run the new test (now passing) plus the full relevant
   test suite, using the actual commands from `AGENTS.md`. Confirm the
   fix didn't break anything else.
8. **Review** — check security/safety first, then correctness,
   convention adherence, whether the verification gate above was
   actually met, and scope creep.
9. **Write the PR description** — what changed, why, how it was
   verified, and any risk/rollback notes. Link the driving Bug issue
   so the PR closes or relates to it (use the PR template's "Related
   issue(s)" line).

## Expected artifacts

- A bug report on the GitHub Issue (Type: Bug), root cause filled in
  after diagnosis — created only if no driving issue existed yet.
- A regression test that fails before the fix and passes after.
- The fix itself, scoped to the root cause.
- An API reference and/or business rule under `docs/` only when the
  fix changes API surface or a standing constraint; index rows updated
  for every permanent doc written. A decision record only if the
  approved fix required a recorded design decision.
- A PR description including root cause and how it was verified,
  linking the Bug issue.

## Verification gates

- The regression test fails on the pre-fix code and passes on the
  post-fix code — both observed, not assumed.
- Full relevant test suite still passes after the fix.
- `./mvnw test` → all tests pass, N passed / 0 failed
- `./mvnw checkstyle:check` → no new violations
- `./mvnw spotless:check` → no formatting changes needed

## Conditions for stopping or requesting human input

- The bug can't be reproduced.
- The root cause implicates a design decision bigger than a local fix —
  stop for human input; if they approve proceeding with that design
  choice, record it as a decision record before implementing.
- The user hasn't yet confirmed the fix approach for anything beyond a
  trivial one-line change.
- Fixing it safely would require touching prod data, secrets, or a
  destructive operation.
