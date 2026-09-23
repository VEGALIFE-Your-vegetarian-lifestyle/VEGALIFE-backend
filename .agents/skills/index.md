# Skills Index

Skills are reusable procedures. Each is a folder under `.agents/skills/`
with a `SKILL.md` (YAML frontmatter + instructions), and optionally a
`references/` folder with supplementary material (including blank
document templates, flat under each skill’s `references/` — no category
subfolders) loaded only when needed. A compatible agent may auto-discover
these via the `name` and `description` in each `SKILL.md`'s frontmatter;
regardless, `AGENTS.md` requires matching a skill to each workflow step
from this index.

| Skill | Use when |
|---|---|
| [`requirement-analysis/`](requirement-analysis/SKILL.md) | A request is ambiguous, underspecified, or a one-liner. Clarifies the real problem, goals/non-goals, testable requirements, and sizing — before any document gets written. |
| [`document-writing/`](document-writing/SKILL.md) | Permanent project docs under `docs/` (ADR, feature spec, API reference, business rule, release notes) **or** an implementation plan under `.agents/plans/` (never committed). Dispatches to the matching template under `document-writing/references/` and places the artifact correctly (docs/ + index row for permanent docs; plan is gitignored). |
| [`issue-pr-writing/`](issue-pr-writing/SKILL.md) | GitHub Issue body (Task / Bug / Feature) or pull request description. Dispatches to the matching template under `issue-pr-writing/references/` and fills it with concrete content — pasted into GitHub, never free-form markdown, never committed to the repo. |
| [`testing/`](testing/SKILL.md) | Writing, updating, or running tests. Dispatches to `references/unit-whitebox.md` or `references/integration-e2e-blackbox.md` depending on what the change touches. |
| [`code-review/`](code-review/SKILL.md) | Reviewing a diff or PR. Baseline always runs (security, correctness, conventions, verification gates, scope). General review → all of `references/{security,architecture-design,readability-maintainability}.md`, skipping `performance.md` unless the diff is latency-sensitive. Review scoped to one aspect → read only that reference. |
| [`database/`](database/SKILL.md) | Designing entities, writing migrations (Flyway), optimizing queries, or changing the database schema. Checks `docs/arch/dependencies.md` for approved DB-related dependencies. |
| [`deployment/`](deployment/SKILL.md) | Docker Compose setup, cloud deployment, CI/CD pipelines, environment configuration. References `docs/` for provider-specific infrastructure details. |
