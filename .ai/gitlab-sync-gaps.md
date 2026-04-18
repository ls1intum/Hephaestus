# GitLab Sync Gaps — PR #1021 Expanded Scope

**Branch:** `fix/profile-review-comment-visibility`
**Audit date:** 2026-04-18 (full fresh sync of `ase/ipraktikum/IOS26/Introcourse`, provider_id=3, 57 projects)
**Status:** All gaps listed below MUST be closed in this PR.

---

## Audit Source Material

- `/tmp/gitlab-audit-fields.md` — 483-line field-completeness report (subagent 1)
- `/tmp/gitlab-audit-live.md` — 83.8% DB-vs-live-GitLab match (subagent 2)
- `/tmp/sync-run.log` — app-server log for the 15:56 → 16:12 sync (3298966)
- Direct psql verification via `docker exec application-server-postgres-1 psql -U root -d hephaestus`

**Row counts (provider_id=3):** users=80, repos=57, labels=861, milestones=1, issues=1141, MRs=904, issue_comments=2188, reviews=2478, review_comments=1731, review_threads=1489, commits=2281, commit_contributors=4563, commit_pr_links=1377, commit_file_changes=13268.

---

## Gap 1 🚨 `commit_pull_request` 77–82 % missing on merged/closed MRs

**Observed coverage:**
| State  | MRs | Linked | Coverage |
|--------|-----|--------|----------|
| MERGED | 768 | 135    | **17.6 %** |
| CLOSED | 108 | 47     | **43.5 %** |
| OPEN   | 28  | 26     | 92.9 %   |

Sample: `simon.christian.winter/go93hih` MRs 22, 23, 24 — each have `issue.commits > 0`, 0 `commit_pull_request` rows.

**Root cause:**
- `pull_request.merge_commit_sha` is NULL on 41 % of merged MRs.
- Linker relies on `merge_commit_sha` + local-clone walk.
- GraphQL `MergeRequest.commits { nodes { sha } }` is **never** consulted as a fallback.

**Fix required:**
- In `GitLabMergeRequestSyncService` (or equivalent linker service), after the local-clone walk, if `commit_pull_request` rows for a merged/closed MR are empty, fetch `MergeRequest.commits` from GraphQL and insert link rows.
- Also persist `merge_commit_sha` from GraphQL (`mergeCommitSha`), not just from local git.
- Target: ≥ 95 % coverage on merged MRs, ≥ 85 % on closed.

**Files to edit (actual package layout — GitLab code lives *under each entity*, not in one `gitlab/` root):**
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/pullrequest/gitlab/**`
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/commit/gitlab/**`
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/commit/util/**` (shared linker logic)

---

## Gap 2 🚨 Review / inline-comment metadata 100 % NULL

**Observed NULL columns (all 100 %):**

| Table | Columns |
|-------|---------|
| `pull_request_review` | `body`, `submitted_at`, `commit_sha` (on synthetic COMMENTED rows) |
| `pull_request_review` (APPROVED) | `submitted_at`, `commit_sha` (141 rows) |
| `pull_request_review_comment` | `path`, `position`, `original_position`, `diff_hunk`, `commit_sha`, `original_commit_sha`, `in_reply_to_id`, `side`, `line`, `original_line`, `start_line`, `start_side` |
| `pull_request_review_thread` | `diff_side`, `start_diff_side`, `line`, `start_line`, `path`, `commit_sha`, `original_commit_sha`, `resolved_by_id` |

**Impact:**
- Inline-feedback scoring in `ExperiencePointCalculator` runs blind — no diff position means no path-weighted or line-weighted scoring is possible.
- Agent diff-note poster has no anchor for position-aware notes on GitLab MRs.
- Profile UI cannot filter review comments by file.

**Fix required:**
- In the discussion/note sync path, persist `Discussion.notes[].position` (REST `/merge_requests/:iid/discussions`) on every `pull_request_review_comment` row:
  - `path` ← `position.new_path` (or `old_path` if deletion)
  - `line` ← `position.new_line`
  - `original_line` ← `position.old_line`
  - `side` ← derived from `new_line` presence vs `old_line`
  - `diff_hunk` ← reconstructed from `position.line_range` if GitLab returns it
  - `commit_sha` ← `position.head_sha`
  - `original_commit_sha` ← `position.base_sha`
  - `in_reply_to_id` ← parent note ID within the same discussion (first note is the root)
- On `pull_request_review_thread`, persist `resolved_by_id` from `Discussion.resolved_by` and copy `path` / `line` from the root note position.
- On `pull_request_review` APPROVED rows, populate `submitted_at` from `MergeRequestApproval.approved_at` and `commit_sha` from the MR's head at approval time (GitLab GraphQL: `MergeRequest.approvedBy { user, approvedAt }`).
- Target: ≥ 95 % non-null on `pull_request_review_comment.{path, line, commit_sha}` for inline notes; ≥ 90 % on `pull_request_review_thread.resolved_by_id` where `resolved=true`.

**Files to edit:**
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/pullrequestreviewcomment/gitlab/**`
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/pullrequestreviewthread/gitlab/**`
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/pullrequestreview/gitlab/**`
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/discussion/**` and `discussioncomment/**` (cross-cutting)

---

## Gap 3 🚨 Denormalized shadow columns silently NULL

| Column | NULL % | Fix |
|--------|--------|-----|
| `issue.issue_type_id` | 100 % | Seed `issue_type` table (GitLab types: Issue, Incident, TestCase, Task) and set FK on sync |
| `issue.state_reason` | 100 % | Map GitLab GraphQL `Issue.closedAsDuplicateOf`, `Issue.moved_to_id`, or `state_event`; fall back to `COMPLETED` when `state='CLOSED'` and no other signal |
| `git_commit.parent_count` | 100 % | Populate from GraphQL `Commit.parents.count` or local `git log --format=%P` |
| `git_commit.parent_shas` | 100 % | Store parent SHA array; required for merge-commit detection |
| `pull_request.merge_commit_sha` | 41 % on merged | Persist GraphQL `MergeRequest.mergeCommitSha` (see Gap 1) |

**Files to edit:**
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/issuetype/gitlab/**` (exists but may be empty/stubbed — fill in)
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/issue/gitlab/**`
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/pullrequest/gitlab/**`
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/commit/gitlab/**`

---

## Gap 4 🚨 Milestones barely synced (1 row vs ≥ 20 on GitLab)

**Observed:** 1 milestone total across 57 projects.
**Expected:** Each IOS26 project group has group + project milestones (spot-check shows ≥ 20 just in the top-level group).

**Root cause hypothesis:** Milestone sync only queries project-level milestones, not group milestones; or pagination terminates after first page.

**Fix required:**
- Sync group milestones via `/groups/:id/milestones?include_parent_milestones=true`.
- Paginate until empty.
- Populate `description`, `due_on` (`due_date`), `closed_at` — currently all NULL.

**Files to edit:**
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/milestone/gitlab/**`
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/common/gitlab/**` (if shared GraphQL/REST client lives there)

---

## Gap 5 ⚠️ User email 45 % NULL, commit_contributor 18 % unresolved

**Observed:**
- 36 / 80 users have `email=NULL`.
- 812 / 4563 `commit_contributor.user_id` NULL.
- ~40 % of those commit authors (~325) are recoverable via display-name match (e.g. `erik.kiessig@tum.de` → user 5202, `go98tod@mytum.de` → user 4875).

**Root cause:** `CommitAuthorResolver` strategy 3 (display-name + TUM alias matching) is defined but not wired into the contributor-sync path. `UserRepository.backfillEmailIfNull` exists but is never called.

**Fix required:**
- After each commit-contributor sync batch, run resolver strategy 3 over unresolved rows and call `backfillEmailIfNull` for the match.
- Ensure sync is idempotent — only backfill when `email IS NULL`.

**Files to edit:**
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/commit/util/CommitAuthorResolver.java` (or equivalent shared resolver)
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/commit/gitlab/**` (sync path that inserts `commit_contributor`)
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/user/UserRepository.java` (`backfillEmailIfNull` already present — just call it)

---

## Gap 6 ⚠️ Commit coverage is default-branch-only

**Observed:** `ge27pah` DB=33 commits, all-branches=73. Feature-branch commits that never merged are dropped.

**Impact:** Commit-based XP, leaderboard, agent precompute all under-count activity on still-open feature branches.

**Fix required:**
- Walk all branches, not just `default_branch`, when populating `git_commit`.
- De-duplicate by SHA.
- Limit by `committed_at >= sync_since` to avoid unbounded history walks.

**Files to edit:**
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/gitlab/commit/**` (local-clone walker)

---

## Gap 7 ⚠️ `commit_file_change.previous_path` never populated for renames

**Fix required:**
- When GitLab `diffs[].renamed_file == true`, persist `old_path` into `previous_path`.
- Add `status='RENAMED'` when `renamed_file=true` or `diff` contains rename header.

**Files to edit:**
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/gitprovider/gitlab/commit/**` (file-change writer)

---

## Gap 8 ⚠️ `issue_comment.updated_at` never diverges from `created_at`

**Fix required:**
- Persist GitLab `Note.updated_at` distinct from `created_at` on every sync; do NOT copy `created_at` when updated timestamp is available.

---

## Gap 9 🐛 `user_achievement` unique-constraint races (1,156 ERROR lines)

**Observed:** `AchievementEventListener` async inserts duplicate `(user_id, achievement_id)` concurrently → unique-key violations spamming the log.

**Fix required:**
- Add `ON CONFLICT (user_id, achievement_id) DO NOTHING` to the upsert path, OR wrap the insert in `try { save } catch DataIntegrityViolationException { ignore }`, OR use a short-lived distributed lock.
- Log ERROR-spam count is *not* a gitprovider issue but blocks log signal — must be fixed in this PR to keep the sync log readable.

**Files to edit:**
- `server/application-server/src/main/java/de/tum/in/www1/hephaestus/achievement/**`

---

## Non-Blocking Observations (document but don't need code fix in this PR)

- GitHub workspaces 2, 3, 4 auto-activated from residual GitHub App installs. Orthogonal.
- GitLab subgroups not modeled as teams. Architectural TBD — track separately.
- Organization descriptions / repository homepage_url / language / archived — cosmetic, skip unless trivial.

---

## Success Criteria for PR #1021 (Expanded)

1. `commit_pull_request` coverage ≥ 95 % on MERGED, ≥ 85 % on CLOSED MRs (re-run audit after fix).
2. `pull_request_review_comment.{path, line, commit_sha}` ≥ 95 % non-null for inline notes.
3. `pull_request_review_thread.resolved_by_id` ≥ 90 % where `resolved=true`.
4. `issue.issue_type_id`, `issue.state_reason`, `git_commit.parent_count`, `git_commit.parent_shas`, `pull_request.merge_commit_sha` populated (≥ 95 %).
5. Milestone count ≥ 20 on fresh sync of same group.
6. `user.email` NULL rate < 20 %, `commit_contributor.user_id` NULL rate < 10 %.
7. `git_commit` count per sampled project matches all-branches count within 5 %.
8. `commit_file_change.previous_path` populated on all rename entries.
9. `issue_comment.updated_at` distinct from `created_at` where GitLab exposes an edit.
10. Zero `user_achievement` unique-constraint errors in sync log.
11. All new paths covered by unit tests (subagents: add `shouldXWhenY` tests per repo convention).
12. OpenAPI regenerated if any DTO touched.

---

## PR #1021 — Retitle + Description Refocus

**New title:** `fix(gitlab-sync): close metadata, commit-link, and review-position gaps (+profile review visibility)`

**New description skeleton:**
```
## Summary
- Expand PR #1021 from profile review-comment visibility into a full GitLab sync hardening pass.
- Close 9 audited gaps (see .ai/gitlab-sync-gaps.md) spanning commit→MR linking, inline review metadata, milestones, denormalized shadow columns, user/email resolution, all-branch commit coverage, rename detection, comment edit timestamps, and achievement dup-key races.
- Keep original profile review-comment visibility + scoring changes in scope.

## Validation
- Full fresh sync of ase/ipraktikum/IOS26/Introcourse (57 projects) passes all 12 success criteria in .ai/gitlab-sync-gaps.md
- Row-level audit reproducible via psql queries in the same file.
- Unit tests added for each sync-path change.
```

---

## Subagent Delegation Plan

This document is the authoritative brief for subagents. Each subagent gets one Gap #, reads this file, ships the fix + tests, and reports back with row-count proof.

- **Subagent A:** Gaps 1 + 3 (`commit_pull_request` + shadow columns on PR/commit)
- **Subagent B:** Gap 2 (review/inline-comment metadata)
- **Subagent C:** Gaps 4 + 8 (milestones + issue_comment.updated_at)
- **Subagent D:** Gaps 5 + 6 + 7 (user resolution + all-branch commits + rename paths)
- **Subagent E:** Gap 9 (achievement dup-key race)
- **Subagent F:** OpenAPI regen + integration sanity re-sync + final audit numbers

After all subagents finish: re-run sync, re-run audit queries, verify all 12 success criteria.
