---
name: testing
description: Use whenever writing, updating, or running tests for a change — new features, bugfixes, or refactors — and whenever a workflow's verification gate requires proof that tests pass. Covers deciding between unit/whitebox testing and integration-or-e2e/blackbox testing, and how to actually run and report results.
---

# Skill: Testing

## When to use this skill

Use whenever writing, updating, or running tests for a change — new
features, bugfixes, or refactors — and whenever a workflow's
verification gate requires proof that tests pass.

## Required context / input

- The change being tested.
- The project's test framework and conventions:
  JUnit 5 + Mockito + MockMvc + H2 in-memory database.
- The exact test command from `AGENTS.md`: `./mvnw test`

## Step 1 — decide which kind of test this needs

Most changes need one or both. Don't default to one without thinking —
pick based on what the change actually touches:

- **Internal logic, a function, a module's own behavior** → unit test,
  whitebox. Read `references/unit-whitebox.md`.
- **Behavior visible through a public interface — an API endpoint, a UI
  flow, a CLI command, cross-service interaction** → integration/e2e
  test, blackbox. Read `references/integration-e2e-blackbox.md`.
- **A bugfix** → almost always needs a regression test at whichever
  level actually exercises the bug; don't reflexively default to
  unit-level if the bug only reproduces through the public interface.

Only open the reference file for the kind(s) you actually need — don't
read both if the change clearly only calls for one.

## Step 2 — write and run

1. Locate existing tests for the touched code, if any — extend them
   rather than duplicating coverage.
2. Write tests following the project's existing style and structure,
   per the chosen reference file.
3. Run the project's test command exactly as specified in `AGENTS.md`.
4. Capture the real output — pass/fail counts, failing test names, error
   messages. Do not paraphrase a "looks fine" summary in place of actual
   output.
5. If tests fail, fix the code or the test (whichever is actually wrong)
   and re-run — don't mark the step done on a failing run.

## Relevant project conventions

- **Test file location**: Tests mirror the main source layout under
  `src/test/java/com/vegalife/...`. Same package structure as `src/main`.
- **Naming**: Test classes end with `Test` suffix (e.g., `UserServiceTest`,
  `UserControllerTest`). Test methods use descriptive names:
  `shouldReturnUserWhenFound`, `shouldThrowExceptionWhenNotFound`.
- **Annotations**:
  - `@SpringBootTest` -- full context integration tests.
  - `@WebMvcTest(UserController.class)` -- controller layer tests with
    MockMvc, loads only web layer.
  - `@DataJpaTest` -- repository layer tests with auto-configured test
    database and transactions.
  - `@ExtendWith(MockitoExtension.class)` -- unit tests with Mockito mocks.
- **Test data**: Use builder patterns or `@BeforeEach` setup methods.
  Avoid shared mutable static state between tests.
- **Assertions**: Prefer AssertJ's `assertThat()` (included via
  spring-boot-starter-test) for readable assertions.

## Verification steps

- The test command was actually executed this session.
- Exit code / pass-fail output is available and consistent with the
  claim made in the summary.
- No existing test was deleted or weakened to force a pass without cause.
- The kind of test written (unit vs integration/e2e) actually matches
  what the change touches — not just whichever was faster to write.

## Expected artifacts / output

- New or updated test file(s).
- A verification note with the actual command run and its real result.
