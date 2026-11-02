---
name: issue-pr-writing
description: >-
  Use whenever writing a GitHub Issue body (Task / Bug / Feature) or a
  pull request description. Dispatches to the matching template under
  references/, fills every section with concrete content, and pastes into
  GitHub — never free-form generic PR/issue markdown. Not for permanent
  docs under docs/ (use the document-writing skill for those).
---

# Issue / PR writing

## When to use

- **GitHub Issue body** — Task, Bug report, or Feature request before
  creating or updating an issue on GitHub.
- **Pull request description** — every PR, before `gh pr create` or when
  rewriting an existing PR body.

Do **not** use this skill for permanent documents under `docs/` (ADR,
feature spec, API reference, business rules, release notes) or for
implementation plans under `.agents/plans/` — those go through
`document-writing`.

## Workflow

1. **Pick the template** by artifact type:

   | Artifact | Template under `references/` |
   |---|---|
   | GitHub Issue, type Task | `task.md` |
   | GitHub Issue, type Bug | `bug-report.md` |
   | GitHub Issue, type Feature | `feature-request.md` |
   | Pull request description | `pr-description.md` |

2. **Fill every section.** Replace all `<placeholder>` text and comments
   with concrete content drawn from the actual work — no leftover
   placeholders, no “TBD”, no empty sections. If a section genuinely does
   not apply, say so explicitly in one line (e.g. “N/A — no user-facing
   docs change”) rather than deleting the heading.

3. **Facts over aspiration.** In PR “How it was verified”, list only
   commands actually run this session and their observed results. Never
   assume or restate a prior session’s claim as your own run.

4. **Paste into GitHub.**
   - Issue: set the **Issue Type** selector (Task / Bug / Feature) to
     match the template used; body is the filled template (minus the
     `## Example` block).
   - PR: body is the filled `pr-description.md` (minus the example);
     title follows the same imperative style as the template’s title
     line.

5. **Strip the example.** Templates include an `## Example` section for
   reference only — never include it in the body you paste into GitHub.

## Placement

- These artifacts live **only on GitHub**, not in this repository.
- Never commit Issue bodies or PR descriptions as files under `docs/` or
  elsewhere in the repo.

## Related

- Permanent project docs + implementation plans: `.agents/skills/document-writing/`
- Skill index: `.agents/skills/index.md`
