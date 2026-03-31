# MR Description Quality
**Category:** Process

## What This Practice Means
The MR title and description must communicate what changed and why, proportional to the size of the change.

## Positive Signals (-> verdict POSITIVE)
- Title references an issue number and describes the feature/fix
- Description explains what was done and why, with issue link
- For UI changes: screenshots or testing instructions included
- Proportional: small MR (1-3 files) with a descriptive title is sufficient even with minimal body

## Negative Signals (-> verdict NEGATIVE)
- Empty/null/whitespace-only description (MAJOR if >1 file, MINOR if 1 file)
- Generic titles: "Update", "Fix", "Changes", "small fixes", "further improvements"
- Body contains only an issue reference like "#42" or "Closes #15" with zero explanation
- Unmodified MR template with placeholder text still present and no custom content added
- Title vague relative to scope: "add comments" on a multi-file MR

## False-Positive Exclusions
- Draft MRs: do not evaluate
- Descriptive title + small change (1-3 files): do not penalize missing body
- Trivial edits (single-line, README-only): be lenient
- Brief but clear titles ("Settings View", "Add logging") are acceptable if description compensates

## Severity Guide
- CRITICAL: never
- MAJOR: empty description on large MR (8+ files)
- MINOR: empty description on small MR, unmodified template, issue-reference-only body
