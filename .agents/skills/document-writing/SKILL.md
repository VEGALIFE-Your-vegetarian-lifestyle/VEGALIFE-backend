---
name: document-writing
description: Use whenever a task produces a written artifact — an ADR, a feature spec, a feature request, a bug report, a PR description, a task, release notes, an API reference, a business rule, or an implementation plan. Dispatches to the matching reference template, fills every section with concrete content instead of placeholders, and places the finished document correctly per AGENTS.md's repository knowledge structure. This project uses GitHub Issues + Projects for tracking.
---

# Skill: Document Writing

## When to use this skill

Use whenever a task produces a written artifact — an ADR, a feature spec,
a feature request, a bug report, a PR description, a task, release
notes, an API reference, a business rule, or an implementation plan —
not for ad-hoc chat replies or code comments.

This skill governs **how to pick and fill a template**; it does not
decide *where the finished document lives* — see "Repository knowledge
structure" in `AGENTS.md` for that.

## Required context / input

- The document type needed.
- Any existing docs of the same type in `docs/` (or existing GitHub
  issues, for tasks/bugs/features) to match tone and conventions — e.g.
  read 2–3 recent ADRs before writing a new one.

## Step 1 — pick the category, then the template

Templates live in three categories under `references/`, each with a
different lifecycle and placement rule:

### repo-docs — permanent project knowledge

Committed to the repo under `docs/`. Source of truth that evolves as the
project grows. Read existing docs of the same type before writing a new
one to match tone and conventions.

| Document type | Template | Placement |
|---|---|---|
| Architecture decision, already made | `repo-docs/adr.md` | `docs/adrs/NNNN-title.md` |
| Product-level feature spanning multiple issues (success metrics, design overview) | `repo-docs/feature-spec.md` | `docs/feats/` |
| API endpoints/contract as it actually exists | `repo-docs/api-reference.md` | `docs/apis/` |
| Business rule — a constraint independent of any one implementation | `repo-docs/business-rule.md` | `docs/brs/` |
| Summarizing a shipped version | `repo-docs/release-notes.md` | `docs/releases/` |

### task-docs — GitHub Issue / PR body templates

Not committed as files in the repo. Pasted into a GitHub Issue or PR
description when creating it. Every GitHub issue's title carries no type
label — set the actual **Issue Type** (Task / Bug / Feature) via
GitHub's own selector when creating it.

| Document type | Template | Where it goes |
|---|---|---|
| Concrete, implementation-facing work | `task-docs/task.md` | GitHub Issue (Type: Task) |
| Recording and diagnosing a bug | `task-docs/bug-report.md` | GitHub Issue (Type: Bug) |
| User-facing feature small enough for one issue | `task-docs/feature-request.md` | GitHub Issue (Type: Feature) |
| Describing a PR for review | `task-docs/pr-description.md` | PR description body |

Pick the template by what's actually being written. The task template's
own guidance covers the routing between it, feature-request, and
bug-report for the common case of "which do I write for this issue."

### temp-docs — ephemeral agent artifacts

Never committed. Created in `.agents/plans/` during development, wiped
after the work lands or is abandoned. `.agents/plans/` is listed in
`.gitignore`.

| Document type | Template | Where it goes |
|---|---|---|
| Breaking work into commit-sized phases before touching code | `temp-docs/plan.md` | `.agents/plans/` (gitignored) |

The plan template has its own prerequisite gate: don't write one until
the requirement is clarified, the user has explicitly confirmed the
implementation direction, and the driving document (spec/task/bug report)
already reflects that confirmed direction.

### No matching template?

If no reference template fits and the document type is likely to recur,
propose adding one (short, following the shape of the existing
references, including a filled example) rather than writing an
untemplated one-off. If it's a genuine one-off, write it directly,
following the same principles as Step 2 below.

## Step 2 — fill it in

1. **Fill every section — don't skip or leave placeholder text in the
   final document.** A section with nothing to say gets "None" or "N/A"
   with a one-word reason, not silence and not the template's own
   instructional comment left in place.
2. **State facts before conclusions.** Context/problem sections describe
   what's true; decision/fix sections state what was chosen. Don't blend
   the two — a reader should be able to tell "this is what happened" from
   "this is what we decided" at a glance.
3. **Be concrete, not vague.** Prefer a number, a command, a file path, a
   named condition, or a Given/When/Then scenario over words like
   "faster," "better," or "robust." If a claim can't be made concrete
   yet, mark it as an open question instead of writing it as settled.
4. **Link instead of duplicating.** If something is already documented
   elsewhere (`docs/arch/`, another ADR, a linked GitHub issue),
   link to it by path or `#<number>` rather than re-explaining it.
5. **Place the file per its category's rule:**
   - **repo-docs** → `docs/` subdirectory per `AGENTS.md`'s "Repository
     knowledge structure"
   - **task-docs** → pasted into a GitHub Issue or PR body, not saved as
     a repo file
   - **temp-docs** → `.agents/plans/`, never committed

## Relevant project conventions

- **GitHub Issue Types**: Use GitHub's own selector (Task / Bug / Feature)
  when creating issues -- the title carries no type label.
- **Doc placement**: Finished repo-docs go in `docs/` subdirectories.
  ADRs in `docs/adrs/NNNN-title.md`, architecture docs in
  `docs/arch/`, API docs in `docs/apis/`.
- **Branch/commit conventions**: Branches use `type/description` format.
  Commits use Conventional Commits: `<type>(<scope>): <description>`.
  PR descriptions should follow the `task-docs/pr-description.md` template.
- **Heading style**: Use ATX-style markdown headings (`#`, `##`).
  ADRs start with `# ADR-NNNN: Title` and status in the first section.
- **Cross-references**: Link to other docs by path (e.g.,
  `docs/arch/dependencies.md`) rather than duplicating content.

## Verification steps

- Every section of the template is filled with real content, not
  leftover instructional comments (`<!-- ... -->`) or placeholder text.
- Any command, path, number, or acceptance criterion stated in the
  document has actually been checked/run/agreed this session, not
  assumed — this applies especially to the "How it was verified" section
  of a PR description and the acceptance criteria of a feature
  spec/feature request.
- Cross-references (links to other docs, ADRs, GitHub issues) resolve to
  something that actually exists.
- The document landed in the right place per its category:
  - repo-docs → in `docs/`, not defaulted into `.agents/`
  - task-docs → in a GitHub Issue/PR, not saved as a file
  - temp-docs → in `.agents/plans/`, not committed

## Expected artifacts / output

- A completed document, in the correct location, following the matching
  reference template with no unfilled placeholders.
