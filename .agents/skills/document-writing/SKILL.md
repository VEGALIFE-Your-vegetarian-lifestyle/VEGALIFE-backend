---
name: document-writing
description: >-
  Use whenever writing a permanent project document under docs/ (ADR,
  feature spec, API reference, business rule, release notes) or an
  implementation plan under .agents/plans/. Dispatches to the matching
  template under references/, fills every section with concrete content,
  and places the artifact correctly — docs/ + index row for permanent
  docs; plan is never committed. Not for GitHub Issue bodies or PR
  descriptions (use the issue-pr-writing skill for those).
---

# Document writing

## When to use

- **Permanent docs under `docs/`** — ADRs, feature specs, API
  references, business rules, release notes.
- **Implementation plan under `.agents/plans/`** — a commit-sized
  phase breakdown for one branch; never part of permanent history.

Do **not** use this skill for GitHub Issue bodies or pull request
descriptions — those go through `issue-pr-writing`.

## Templates

Templates live directly under `references/` (no category subfolders):

| Template | What it is for | Where it lands |
|---|---|---|
| `references/adr.md` | Architectural Decision Record | `docs/adrs/NNNN-short-title.md` + row in `docs/index.md` |
| `references/feature-spec.md` | Feature specification | `docs/feats/<feature-name>.md` + row in `docs/index.md` |
| `references/api-reference.md` | One file per API endpoint | `docs/apis/<feature>/<method>-<path>.md` + row in `docs/apis/index.md` |
| `references/business-rule.md` | Business rules for a feature/domain | `docs/brs/<feature-name>.md` + row in `docs/brs/index.md` |
| `references/release-notes.md` | Release notes for a version | `docs/releases/v<version>.md` + row in `docs/index.md` (if applicable) |
| `references/plan.md` | Implementation plan (ephemeral) | `.agents/plans/` — **never committed** |

## Workflow

1. **Pick the template** from the table above by document type.

2. **Fill every section.** Replace all `<placeholder>` text and comments
   with concrete content drawn from the actual codebase / decision /
   requirement — no leftover placeholders, no “TBD”, no empty sections.
   If a section genuinely does not apply, say so explicitly in one line
   rather than deleting the heading.

3. **Facts over aspiration.** State what is, not what should be. Link to
   existing docs rather than duplicating them.

4. **Place the artifact.**
   - **Permanent docs** → correct `docs/` subdirectory **and** add a row
     to that directory’s `index.md` (or `docs/index.md` for top-level
     entries).
   - **Plan** → `.agents/plans/`, one file per active branch. Confirm
     `.agents/plans/` is in `.gitignore` before writing; never write a
     plan anywhere that would get committed. A plan is written only
     after the requirement is clarified and the implementation direction
     is explicitly confirmed — it sequences decided work, it is not how
     direction is figured out.

5. **Strip the example.** Templates include an `## Example` section for
   reference only — never include it in the document you write.

## Placement rules (summary)

| Kind | Committed? | Location | Index row? |
|---|---|---|---|
| ADR / feature spec / API ref / business rule / release notes | Yes | under `docs/` | Yes |
| Implementation plan | **No** | `.agents/plans/` (gitignored) | No |

## Related

- GitHub Issue bodies + PR descriptions: `.agents/skills/issue-pr-writing/`
- Skill index: `.agents/skills/index.md`
- Repo knowledge structure: `AGENTS.md` (“Repository knowledge structure”)
