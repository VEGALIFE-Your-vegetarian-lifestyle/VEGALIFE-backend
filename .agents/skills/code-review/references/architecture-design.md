# Reference: Architecture & Design Review

## When this applies

The diff introduces a new component, service, module boundary, or
otherwise makes a structural decision — not for a same-shaped change
that fits an existing, established pattern.

## Checklist

- **Fits existing patterns**: does this follow the project's existing
  architecture (per `AGENTS.md` / `docs/arch/`), or does it
  quietly introduce a parallel way of doing the same kind of thing?
- **Boundaries**: are responsibilities cleanly separated, or does this
  reach across a layer it shouldn't (e.g. business logic in a
  controller, direct DB access bypassing a data layer)?
- **Coupling**: does this create a new dependency between components
  that will be hard to change independently later?
- **Significant enough for an ADR?**: if this is a decision that will be
  hard to reverse or affects multiple areas, was it recorded as one, or
  should it have been?

## What good coverage looks like

- A reviewer unfamiliar with this specific change can tell, from the
  diff alone, where a similar future change should go.

## Common mistakes to avoid

- Approving a structural change on the strength of "it works" without
  asking whether it fits how the rest of the codebase is organized.
- Treating a significant design decision as a routine review comment
  instead of flagging that it deserved an ADR before implementation.
