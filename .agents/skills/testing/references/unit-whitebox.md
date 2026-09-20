# Reference: Unit Testing (Whitebox)

## When this applies

The change is to internal logic — a function, a class, a module — and
the test needs to know the implementation to be meaningful: which
branches exist, what gets mocked, what internal state matters.

## Approach

- Test one unit of behavior at a time; don't reach across module
  boundaries — that's a job for a higher-level test instead.
- Mock or stub external dependencies (network calls, database, file
  system, other modules) so the test is fast, deterministic, and
  isolated to the unit under test.
- Cover: the happy path, each meaningful branch/condition, and edge
  cases (empty input, boundary values, error paths) — not just the
  obvious case.
- Because this is whitebox, it's fine (and expected) for the test to
  know about internal structure — e.g. asserting a private helper was
  called with specific arguments — that a blackbox test never could.
- Keep tests independent of each other and of execution order.

## What good coverage looks like

- Each public function/method has at least one test for its main
  behavior and one for its most important edge case or error path.
- A bugfix's regression test at this level reproduces the exact
  condition that caused the bug, not a generic nearby case.

## Common mistakes to avoid

- Testing implementation details that aren't actually part of the
  contract (e.g. asserting exact internal call order when only the
  outcome matters) — this makes tests brittle without adding value.
- Over-mocking to the point the test no longer exercises real logic.
- One giant test asserting many unrelated things instead of several
  focused tests.
