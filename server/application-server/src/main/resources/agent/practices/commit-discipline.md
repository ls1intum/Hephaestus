# Commit Discipline
**Category:** Process

## What This Practice Means
MR titles should communicate what changed. MRs should not bundle unrelated concerns without explanation. This is an MR-level evaluation, not inline on code.

## Positive Signals (-> verdict POSITIVE)
- MR title with 3+ words describing WHAT changed (e.g., "Implement the EventListView")
- Title references an issue number (e.g., "#19: Feedback on user selection")
- Clear title on a large MR makes scope understandable

## Negative Signals (-> verdict NEGATIVE)
- **Empty or meaningless title**: empty, single punctuation, gibberish (".", "asdf"), single generic word alone ("fix", "update", "changes", "stuff", "wip", "test")
- **Truly generic multi-word title**: vague adjective + vague noun with zero specificity ("further improvements", "small fixes", "some changes")
- **Kitchen-sink MR**: 20+ files across 4+ unrelated concerns AND a vague title (a 20-file MR with a clear title is fine; refactoring/renames are fine)

### Critical False-Positive Exclusions
Do NOT flag:
- MRs touching only 1-3 files (scope is self-evident)
- README-only MRs
- Auto-generated kebab-case branch name titles from GitLab
- MRs where the description body clearly explains the changes
- Any title with an issue reference
- Short but specific titles: "Add logging", "Settings View", "Code Refactor"
- Any title >= 3 words that identifies WHAT was changed

### Boundary with mr-description-quality
Both practices may evaluate the same MR title. If overlapping, let mr-description-quality handle title quality — this practice should focus on scope/structure concerns (kitchen-sink).

## Severity Guide
- CRITICAL: Never used for this practice
- MAJOR: Never used for this practice
- MINOR: All negative findings — meaningless titles, generic titles, kitchen-sink scope
