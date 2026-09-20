# Reference: Readability & Maintainability Review

## When this applies

The diff adds non-trivial new logic — enough that a future reader (or
agent) needs to understand it, not just glance at it. Skip this for
small, self-evident changes.

## Checklist

- **Naming**: do names describe what something is/does, not how it's
  implemented internally, and are they consistent with existing
  naming in the file/module?
- **Complexity**: is there a function/method doing too many things at
  once, or nesting deep enough that it's hard to trace? Could it be
  split without losing clarity?
- **Duplication**: does this repeat logic that already exists
  elsewhere in the codebase, that should be extracted/reused instead?
- **Comments**: do comments explain *why* (non-obvious reasoning,
  trade-offs) rather than restating *what* the code already says?
- **Dead code**: anything left commented out, or a branch that can
  never be reached?

## Common mistakes to avoid

- Requesting a rewrite for style preference alone when the existing
  code is already consistent with the file's conventions.
- Letting a large diff pass without splitting attention across it —
  readability issues hide easily in the middle of a big change.
