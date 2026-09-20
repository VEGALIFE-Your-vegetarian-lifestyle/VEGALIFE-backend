# ⚠️ DO NOT COMMIT THIS FILE

This is a working artifact for implementing one branch's worth of work —
not part of the project's permanent history. Before writing it, confirm
`.agents/plans/` is listed in `.gitignore`; add the entry if it's
missing. Never write a plan file anywhere that would get committed.

## Prerequisites — confirm before writing this plan

Don't write a plan until all of these are true. Writing a plan is not
how you figure out the direction — it's how you sequence work on a
direction that's already settled.

- [ ] The requirement/bug has been clarified (ambiguity resolved, goals
      vs. non-goals separated, requirements made testable).
- [ ] **The user has explicitly confirmed the implementation
      direction** — not just that a request exists, but the actual
      approach: what will be built/changed and the key trade-offs. If
      this hasn't happened yet, stop and get confirmation first.
- [ ] The driving document/issue (feature spec, feature request, task,
      or bug report) is already written and reflects the confirmed
      direction. A plan describes *how* to build something already
      decided *what* to build — it doesn't substitute for that
      decision.

# Plan: <short title>

## Branch

<!-- One branch per plan — don't reuse a branch across unrelated plans,
and don't start implementing without one. Name it <type>/<issue-number>-
<short-description>, e.g. feat/104-one-click-reorder — using the GitHub
issue number, not an invented ID. Type is one of: feat, fix, refactor,
chore, docs, test — matching the change's actual nature, same as the
commit type below. -->

## Driving issue

<!-- Link to the GitHub issue (task / bug report / feature request)
this implements. -->

## Phases

Each phase = exactly one commit. Keep each phase small enough to stay
buildable and independently revertible on its own — if describing a
phase's scope needs "and", it's probably two phases.

### Phase 1: <name>

- **Scope**: <the single concern this phase covers — one function, one
  module, one behavior>
- **Files touched**: <paths>
- **Commit message**: `<type>(<scope>): <description>` — type is one of
  feat / fix / refactor / test / chore / docs / style / perf / ci;
  scope is optional (e.g. `auth`, `api`); description is lowercase, no
  trailing period, under 72 characters
- **Est. size**: <LOC estimate — keep under ~200, ideally 50–150>
- **Must hold before moving to the next phase**: the codebase builds
  and existing tests still pass at the end of this phase, even if the
  overall plan isn't done yet

### Phase 2: <name>

- **Scope**:
- **Files touched**:
- **Commit message**: `<type>(<scope>): <description>`
- **Est. size**:
- **Must hold before moving to the next phase**:

## Verification plan

<!-- How each phase (or the plan as a whole) actually gets verified —
which commands, per AGENTS.md's verification rule. Don't mark a phase
done on an assumed result. -->

## Rollback notes

<!-- Which phases are safe to revert independently, and which depend on
an earlier phase landing first. -->

## Git hygiene checklist for this branch

- [ ] Branch created from an up-to-date main/develop.
- [ ] Branch stays scoped to this one plan — no unrelated changes
      riding along.
- [ ] Rebase with main periodically; don't let the branch live past
      ~3 days without merging or rebasing.
- [ ] Commits land in the order planned above, one phase at a time —
      not one giant commit at the end.
- [ ] Total change stays within a reviewable PR size (~400 LOC / ~10
      files / ~3–8 commits); split into multiple plans/branches if the
      phase list grows past that.
- [ ] A draft PR is opened early (from the first commit), not only
      once every phase is finished.

---

## Example

## Prerequisites — confirm before writing this plan
- [x] Requirement clarified — this is a straightforward, well-scoped
      task, nothing ambiguous to resolve.
- [x] User confirmed the direction: apply the existing rate-limit
      middleware (already used on `/password-reset`) to `/login` rather
      than building a new mechanism.
- [x] Driving issue #88 already written and reflects this direction.

# Plan: Add rate limiting to the /login endpoint

## Branch
feat/88-login-rate-limit

## Driving issue
#88 — Add rate limiting to the /login endpoint

## Phases

### Phase 1: Add rate-limit config
- **Scope**: introduce configurable rate-limit constants (max attempts,
  window duration) — no behavior change yet.
- **Files touched**: `config/rate-limit.ts`
- **Commit message**: `chore(auth): add rate-limit config constants`
- **Est. size**: ~30 LOC
- **Must hold before moving to the next phase**: builds clean, no
  existing tests broken (nothing uses the config yet).

### Phase 2: Add rate-limit middleware
- **Scope**: implement the per-IP rate-limit middleware itself, unit
  tested in isolation, not yet wired to any route.
- **Files touched**: `middleware/rateLimit.ts`, `middleware/rateLimit.test.ts`
- **Commit message**: `feat(auth): add per-IP rate-limit middleware`
- **Est. size**: ~90 LOC
- **Must hold before moving to the next phase**: unit tests for the
  middleware pass; build succeeds.

### Phase 3: Wire middleware onto /login
- **Scope**: apply the middleware to the `/login` route only.
- **Files touched**: `routes/auth.ts`
- **Commit message**: `feat(auth): apply rate limiting to /login`
- **Est. size**: ~15 LOC
- **Must hold before moving to the next phase**: existing `/login`
  tests still pass; manual check that unrelated routes are unaffected.

### Phase 4: Add integration tests
- **Scope**: end-to-end test hitting `/login` past the limit, confirming
  429 behavior and window reset.
- **Files touched**: `tests/integration/login-rate-limit.test.ts`
- **Commit message**: `test(auth): add integration tests for login rate limit`
- **Est. size**: ~60 LOC
- **Must hold before moving to the next phase**: N/A — final phase.

## Verification plan
Run the project's test command after each phase; run the full suite
plus a manual `curl` burst test against `/login` after phase 4.

## Rollback notes
Phases 1–2 are inert on their own (nothing calls the new middleware
yet) and safe to revert independently. Phase 3 depends on phase 2 and
reverting it removes rate limiting from `/login` cleanly. Phase 4 is
test-only and always safe to revert.

## Git hygiene checklist for this branch
- [x] Branch created from up-to-date main.
- [x] Scoped to this one plan only.
- [ ] Rebase before opening PR.
- [ ] Commits land in order above.
- [x] Total estimated ~195 LOC across 4 commits — within PR size guide.
- [ ] Open draft PR after phase 1 lands.
