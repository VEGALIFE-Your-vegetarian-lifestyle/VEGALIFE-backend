---
name: code-review
description: Use whenever reviewing a diff or PR — as a dedicated review task or as the review step inside a workflow. Always checks security/safety, correctness, convention adherence, verification gates, and scope. A general review request goes through every lens (security, architecture/design, readability/maintainability) except performance, which only applies when the diff is latency-sensitive or performance was specifically asked for. A request scoped to one aspect reads only that lens.
---

# Skill: Code Review

## When to use this skill

Use whenever reviewing a diff or PR — either as a dedicated review task or
as the review step a workflow calls for.

## Required context / input

- The diff or PR to review.
- `AGENTS.md` (conventions, security constraints, verification rule).
- The relevant workflow's verification gate, to check it was actually met.

## Step 1 — baseline (every review, no exceptions)

1. **Security & safety first**: check the diff against `AGENTS.md`'s
   security constraints (secrets, destructive commands, prod-touching
   changes) before anything else.
2. **Correctness**: does the change do what it claims to do? Are there
   obvious logic errors, unhandled edge cases, or missed error paths?
3. **Convention adherence**: does it follow `AGENTS.md`'s coding
   guidelines and existing patterns in the touched files?
4. **Verification check**: was the relevant workflow's verification gate
   actually satisfied — real test/build output shown, not just claimed?
5. **Scope check**: does the diff stay within the scope of the stated
   task, or does it bundle unrelated changes?

## Step 2 — decide which lens(es) apply

- **The request asks for review of one specific aspect** (e.g. "just
  check security," "review this for performance") → read only the
  matching reference file below. Skip the rest of this step.
- **The request is a general review** (no aspect specified) → go
  through every lens below **except performance**:
  - `references/security.md` — always, deeper than the baseline check.
  - `references/architecture-design.md` — always.
  - `references/readability-maintainability.md` — always.
  - `references/performance.md` — only if the diff clearly touches a
    hot path, a loop over data, a DB query, or something else
    latency-sensitive; otherwise skip it for a general review.

## Step 3 — report

Label findings by severity: **must-fix**, **should-fix**, **suggestion**.

## Relevant project conventions

- **Checkstyle + Spotless** are enforced in CI. Reviewers should flag code
  that would fail `./mvnw checkstyle:check` or `./mvnw spotless:check`.
- **Layer architecture**: Controllers should be thin (delegate to services).
  Services contain business logic. Repositories handle data access. Review
  for layer violations (e.g., business logic in controllers).
- **Conventional commits**: Commit messages must follow
  `<type>(<scope>): <description>` format. PR titles should match.
- **No secrets**: Never log, print, or commit credentials, API keys, or
  tokens. Flag any occurrence in the diff.
- **DTO boundaries**: Entities must not be exposed directly in API responses.
  Review for entity leakage in controller responses.

## Verification steps

- Confirm claimed test/build results correspond to real output, not a
  paraphrase or assumption.
- Confirm referenced files/paths in the diff actually exist as described.
- If the diff touches `AGENTS.md` or `.agents/`, confirm it's going through
  the required PR + CODEOWNERS process rather than a direct merge.
- Confirm the right lens(es) were actually applied per Step 2's rule —
  all three non-performance lenses for a general review, or just the
  one requested for a scoped review, and performance only when it
  genuinely applied.

## Expected artifacts / output

- A structured review comment: must-fix / should-fix / suggestion,
  each with a short reason.
- An explicit statement on whether verification gates were actually met.
