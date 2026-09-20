# Skills Index

Skills are reusable procedures. Each is a folder under `.agents/skills/`
with a `SKILL.md` (YAML frontmatter + instructions), and optionally a
`references/` folder with supplementary material (including, for
`document-writing`, the blank document templates) loaded only when
needed. A compatible agent may auto-discover these via the `name` and
`description` in each `SKILL.md`'s frontmatter; regardless, `AGENTS.md`
requires matching a skill to each workflow step from this index.

| Skill | Use when |
|---|---|
| [`requirement-analysis/`](requirement-analysis/SKILL.md) | A request is ambiguous, underspecified, or a one-liner. Clarifies the real problem, goals/non-goals, testable requirements, and sizing — before any document gets written. |
| [`document-writing/`](document-writing/SKILL.md) | Producing any written artifact (ADR, feature spec, feature request, bug report, PR description, task, release notes, API reference, business rule, implementation plan). Dispatches to the matching reference template under `document-writing/references/` and fills it in. Project uses GitHub Issues + Projects for tracking. |
| [`testing/`](testing/SKILL.md) | Writing, updating, or running tests. Dispatches to `references/unit-whitebox.md` or `references/integration-e2e-blackbox.md` depending on what the change touches. |
| [`code-review/`](code-review/SKILL.md) | Reviewing a diff or PR. Baseline always runs (security, correctness, conventions, verification gates, scope). General review → all of `references/{security,architecture-design,readability-maintainability}.md`, skipping `performance.md` unless the diff is latency-sensitive. Review scoped to one aspect → read only that reference. |
| [`database/`](database/SKILL.md) | Designing entities, writing migrations (Flyway), optimizing queries, or changing the database schema. Checks `docs/arch/dependencies.md` for approved DB-related dependencies. |
| [`deployment/`](deployment/SKILL.md) | Docker Compose setup, cloud deployment, CI/CD pipelines, environment configuration. References `docs/` for provider-specific infrastructure details. |
