# MR Description Quality
**Category:** Process

**Scope:** The MR title and description must communicate what changed and why, proportional to change size. This practice evaluates communication quality — how well the MR explains itself.

## Positive Signals
- Title references an issue number and describes the feature/fix
- Description explains what was done and why
- For UI changes: screenshots or testing instructions included
- Proportional: small MR (1-3 files) with descriptive title is sufficient even with minimal body

## Negative Signals
- **Empty/whitespace-only description** on a multi-file MR
- **Generic titles**: "Update", "Fix", "Changes", "small fixes", "further improvements" — no specificity about WHAT changed
- **Body contains only issue reference** like "#42" or "Closes #15" with zero explanation
- **Unmodified MR template** with placeholder text and no custom content
- **Title vague relative to scope**: "add comments" on a multi-file MR

## Exclusions — Do NOT Flag
- Draft MRs: do not evaluate
- Descriptive title + small change (1-3 files): do not penalize missing body
- Trivial edits (single-line, README-only): be lenient
- Brief but clear titles ("Settings View", "Add logging") are acceptable
- Any title with an issue reference that gives context

## Severity
- **CRITICAL**: Never
- **MAJOR**: Empty description on large MR (8+ files)
- **MINOR**: Empty description on small MR, unmodified template, issue-reference-only body, generic title
