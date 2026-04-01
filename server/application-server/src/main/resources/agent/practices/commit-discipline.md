# Commit Discipline
**Category:** Process

**Scope:** MR scope, coherence, and title meaningfulness. MRs should not bundle unrelated concerns, and titles should communicate the change.

## Positive Signals
- MR touches a single feature area or concern
- Title clearly states WHAT was changed (e.g., "Add login screen", "Fix crash on empty cart")
- Large MR with clear title/description explaining the unified scope
- Refactoring/rename MRs touching many files but one concern

## Negative Signals
- **Kitchen-sink MR**: 20+ files across 4+ unrelated concerns AND no title/description that justifies the breadth
- **Meaningless title**: empty, single punctuation, gibberish (".", "asdf", "wip"), or titles that describe process rather than content (e.g., "forget issue and branch", "see message history", "push changes")
- **Branch-slug title**: title is just the branch name with slashes/hyphens (e.g., "#24-2-8: feature-branch-name") without describing the change
- **Generic-only title**: title uses only generic verbs without a subject ("fix", "update", "changes", "stuff")

## Exclusions — Do NOT Flag
- MRs touching only 1-3 files (scope is self-evident)
- README-only MRs
- MRs where the description body explains the bundled scope
- Titles with an issue reference AND a clear subject (e.g., "Resolve #12: Add weather fetching")
- Auto-generated "Resolve ..." titles from GitLab that include the issue title

## Boundary with mr-description-quality
If the MR title is vague but the MR scope is tight (few files, single concern), flag under mr-description-quality, not here. This practice fires on **structural** scope problems OR titles so meaningless they indicate zero thought about what was changed.

## Severity
- **CRITICAL**: Never
- **MAJOR**: Never
- **MINOR**: All findings — meaningless titles, kitchen-sink scope
