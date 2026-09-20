# Reference: Integration / End-to-End Testing (Blackbox)

## When this applies

The change affects behavior visible through a public interface — an API
endpoint, a UI flow, a CLI command, or interaction between services —
and the test should verify outcomes without depending on how the
implementation works internally.

## Approach

- Test through the same interface a real caller would use: HTTP
  request, UI interaction, CLI invocation — not by calling internal
  functions directly.
- Don't mock the thing you're actually testing the integration of — if
  the point is "service A correctly calls service B," a test that mocks
  service B entirely isn't testing the integration.
- It's fine to mock things genuinely external to the system under test
  (a third-party payment provider, an external API) to keep tests
  deterministic — the boundary to mock is the edge of what this project
  owns, not the edge of the current module.
- Cover the primary user-facing flow first, then the most likely failure
  modes a real user/caller would hit (invalid input, unauthorized
  access, a downstream dependency being unavailable).
- Because this is blackbox, the test must not assume or assert internal
  implementation details — if the internals change but the observable
  behavior doesn't, the test should still pass.

## What good coverage looks like

- The primary flow works end-to-end exactly as a real user/caller would
  exercise it.
- Each acceptance criterion for the user-facing capability being tested
  maps directly to one or more blackbox tests — if a criterion can't be
  turned into a test, that's worth flagging.
- A bugfix's regression test at this level reproduces the bug exactly
  as a real user/caller would trigger it, not through a shortcut only
  reachable in test code.

## Common mistakes to avoid

- Reaching into internal state to set up test conditions instead of
  driving them through the real interface — this silently turns the
  test into a whitebox test and can hide bugs in the real entry path.
- Flaky tests from real timing/network dependencies — mock genuinely
  external services, use deterministic waits/polling instead of fixed
  sleeps.
- Testing too many scenarios end-to-end when a unit test would cover
  the same logic faster and more reliably — reserve this level for what
  only shows up across the interface or across components.
