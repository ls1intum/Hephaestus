# PR #1021 Expanded — GitLab Sync Hardening

See `.ai/gitlab-sync-gaps.md` for full audit and fix spec.

## Scope (must all land in this PR)

- [ ] Gap 1 — `commit_pull_request` linker: GraphQL fallback + persist `merge_commit_sha`
- [ ] Gap 2 — Review/inline-comment position metadata (path, line, commit_sha, in_reply_to, resolved_by)
- [ ] Gap 3 — Shadow columns: `issue_type_id`, `state_reason`, `parent_count`, `parent_shas`
- [ ] Gap 4 — Milestone sync (group + pagination + fields)
- [ ] Gap 5 — `CommitAuthorResolver` strategy 3 wired into sync + email backfill
- [ ] Gap 6 — All-branch commit walker (not just default)
- [ ] Gap 7 — `commit_file_change.previous_path` for renames
- [ ] Gap 8 — `issue_comment.updated_at` distinct from `created_at`
- [ ] Gap 9 — `user_achievement` dup-key race fix
- [ ] Keep original profile review-comment visibility scope intact
- [ ] Regen OpenAPI if DTOs touched
- [ ] Re-run full fresh sync; re-run audit queries; verify 12 success criteria
- [ ] Retitle + rewrite PR #1021 description
