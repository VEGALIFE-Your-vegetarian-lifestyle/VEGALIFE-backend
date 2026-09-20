# Workflows Index

A workflow is a multi-step process for a recurring kind of development
task. Each defines required inputs, ordered steps described in plain
language (workflows don't name specific skills — matching a step to a
skill happens as a separate part of the process, per `AGENTS.md`),
expected artifacts, verification gates, and conditions for stopping to
ask a human. See "How to work in this repo" in `AGENTS.md` for the rule
on picking one before starting a task.

| Workflow | Use when |
|---|---|
| [`feature.md`](feature.md) | Building a new feature or capability. |
| [`bugfix.md`](bugfix.md) | Fixing a reported or observed bug. |
| [`refactor.md`](refactor.md) | Restructuring code with no intended behavior change. |

If a task doesn't clearly match one of these (e.g. a one-off script, an
exploratory spike, a pure documentation change), say so rather than
forcing it into the nearest workflow — small or genuinely novel tasks
don't need a workflow at all, see `AGENTS.md`'s "keep it minimal" rule.

<!-- TODO: add project-specific workflows here as they're justified by
a real recurring need, e.g. release.md, migration.md. Don't add one
speculatively. -->
