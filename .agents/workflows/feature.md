# Workflow: Feature

## Required inputs

- A description of the feature — can be a rough idea or a one-liner;
  clarifying it is step 1 below, not a precondition to starting.

## Steps

1. **Understand** — read the request, relevant existing code, and any
   linked design/architecture docs. If the request is ambiguous,
   underspecified, or just a one-liner, clarify it first: find the real
   problem behind the literal ask, separate goals from non-goals, and
   turn vague requirements into testable ones before moving on.
2. **Confirm direction with the user** — before writing anything, lay
   out the intended approach (what will be built, key trade-offs, any
   open questions from step 1) and get the user's explicit confirmation.
   Don't proceed to writing a spec or a plan on an assumed direction —
   an unconfirmed guess compounds into wasted spec + plan + code.
3. **Spec** — for anything non-trivial, write a feature spec before
   coding, so scope and acceptance criteria are explicit. Skip this for
   small, obvious changes. If the feature involves a significant
   architectural decision, record that decision separately.
4. **Plan** — only after the spec (or the confirmed direction, for
   small changes) exists — before touching code, create a dedicated
   branch for this feature and break the implementation into
   commit-sized phases, each one a single concern that keeps the
   codebase buildable on its own. Write this down as a working plan —
   it guides implementation but is never itself committed.
5. **Implement** — follow the plan one phase at a time, one commit per
   phase; stay scoped to the feature and its acceptance criteria, don't
   bundle unrelated refactors.
6. **Test** — most features need both unit-level tests for new internal
   logic and a higher-level test that exercises the acceptance criteria
   the way a real user/caller would — don't default to only one kind.
7. **Verify** — run the project's actual build/lint/test commands from
   `AGENTS.md`. This step is not complete until real output is observed
   this session.
8. **Review** — check security/safety first, then correctness,
   convention adherence, whether the verification gate above was
   actually met, and scope creep.
9. **Write the PR description** — what changed, why, how it was
   verified, and any risk/rollback notes.

## Expected artifacts

- A feature spec (for non-trivial features), and a separate decision
  record if a significant design decision was made.
- A working plan broken into commit-sized phases (not committed).
- Implementation code.
- Tests covering the new feature.
- A PR description.
- Verification output (build/test/lint results actually run).

## Verification gates

- Build succeeds — actual command run, exit code observed.
- Test suite passes — actual command run, pass/fail output observed.
- Lint passes (or pre-existing violations are unchanged) — actual command
  run.
- `./mvnw clean compile` → exit code 0
- `./mvnw test` → all tests pass, N passed / 0 failed
- `./mvnw checkstyle:check` → no new violations
- `./mvnw spotless:check` → no formatting changes needed

## Conditions for stopping or requesting human input

- Clarifying the request surfaces a genuine conflict or missing
  decision that isn't just a detail.
- The user hasn't yet confirmed the intended direction — don't proceed
  to spec, plan, or code on an assumed confirmation.
- Acceptance criteria are ambiguous or contradictory.
- The feature requires a change to `AGENTS.md`/`.agents/` conventions.
- A verification gate fails and the fix isn't obvious/safe.
- The feature would require touching prod config, secrets, or a
  destructive operation — confirm with a human before proceeding.
