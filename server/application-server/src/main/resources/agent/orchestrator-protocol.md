# Practice Review Protocol

Canonical protocol for practice-aware code review. Both Claude Code and OpenCode follow this.

## Workspace Layout

```
/workspace/
  repo/                              # Git repository (read-only)
  .context/
    metadata.json                    # PR title, body, author, branches
    comments.json                    # Existing review comments
    diff.patch                       # Unified diff with [L<n>] line annotations
    diff_stat.txt                    # Changed files summary
    diff_summary.md                  # Per-file diff with index table (primary input)
    contributor_history.json         # (optional) Prior findings for this author
  .practices/
    index.json                       # [{slug, name, category}]
    {slug}.md                        # Evaluation criteria per practice
    all-criteria.md                  # All practice criteria bundled
  .analysis/
    practices/
      {slug}.json                    # Per-practice findings
  orchestrator-protocol.md           # THIS FILE
```

## Line Number Annotations

The diff is pre-annotated with `[L<n>]` prefixes = **source-file line number**:
```
[L9] +    private let apiToken = "ghp_abc123"
```
**All line numbers in findings MUST use `[L<n>]` values.** Never patch-file positions.

## Output Schema

```json
{
  "findings": [
    {
      "practiceSlug": "string — from index.json",
      "title": "string — max 120 chars, describe the defect or good practice",
      "verdict": "POSITIVE or NEGATIVE",
      "severity": "CRITICAL or MAJOR or MINOR or INFO",
      "confidence": 0.85,
      "evidence": {
        "locations": [
          {"path": "relative/path.swift", "startLine": 42, "endLine": 50}
        ],
        "snippets": ["exact code from diff — never paraphrased"]
      },
      "reasoning": "max 500 chars. WHAT the pattern is, WHY it matters here, WHAT happens if unfixed.",
      "guidance": "max 800 chars. Show the fix with a code block. Reference only symbols that exist in the diff.",
      "suggestedDiffNotes": [
        {"filePath": "path.swift", "startLine": 42, "endLine": 42, "body": "max 300 chars. The fix, not the diagnosis."}
      ]
    }
  ],
  "delivery": {
    "mrNote": "string — markdown summary, 100-300 words",
    "diffNotes": [
      {"filePath": "string", "startLine": 1, "endLine": 1, "body": "string"}
    ]
  }
}
```

## Field Definitions

**verdict**: POSITIVE (good practice demonstrated) or NEGATIVE (violation found in changed code).

**severity** (follow practice criteria strictly — don't guess):
- **CRITICAL**: security vulnerability, data loss, production crash
- **MAJOR**: functional bug, missing safety mechanism
- **MINOR**: style, naming, minor readability
- **INFO**: observation, no direct quality impact

**confidence**: 0.0-1.0. Below 0.5 = lean POSITIVE instead.

**evidence**: Exact code and [L<n>] line numbers. Never fabricated.

**reasoning**: Three parts in ≤500 chars: (1) what the pattern is, (2) why it's bad in THIS context, (3) what breaks.

**guidance**: The fix. For NEGATIVE findings, MUST include a code block using only symbols that exist in the diff. Never reference variables, types, or properties the student hasn't written.

**suggestedDiffNotes**: Inline comments for NEGATIVE findings only. The body should be the fix action, not a restatement of the problem. Max 300 chars. Field names: `filePath`, `startLine`, `endLine`, `body`.

## Rules

1. **Only evaluate CHANGED code** — lines with `+` prefix in diff.patch. Pre-existing code (lines without `+`, code in `/workspace/repo/` not on a `+` line) is context for understanding, NEVER for flagging. If a finding's evidence points to code that is not on a `+` line, the finding is invalid. **WARNING: A file appearing in the diff does NOT mean all its code is changed.** A file with 200 lines may only have 5 `+` lines — only those 5 are in scope. grep for each snippet and verify the `+` prefix.
2. **Never fabricate evidence** — only cite code that literally appears in the diff on `+` lines.
3. **Use [L<n>] line numbers** — never patch-file positions or Read tool line numbers.
4. **Report ALL NEGATIVE findings** — do not cap or suppress valid findings. Prioritize by severity: security > crashes > correctness > design > style. Every real issue deserves a finding.
5. **Code examples must compile** — only reference symbols that exist in the diff or are standard library. Never invent `viewModel`, `errorMessage`, etc. unless the student wrote them.
6. **Check false-positive exclusions** in practice criteria before flagging NEGATIVE.
7. **No finding for irrelevant practices** — if a practice doesn't apply, produce no finding.
8. **One finding per practiceSlug** — deduplicate by slug.
9. **If ALL findings are POSITIVE**: set `delivery.mrNote` to `""` and `delivery.diffNotes` to `[]`. Silence = approval.
10. **One finding per practice, but note ALL violations** — if a practice has multiple violations (e.g., both `fatalError` AND `URL(string:)!` for fatal-error-crash), include ALL of them in the same finding's evidence/guidance. Don't silently drop the second violation. The title should reference the most impactful one, but guidance must cover all instances.
11. **Fix must be non-empty** — guidance code blocks must show the actual corrected code. Never show an empty function body or a no-op as a "fix".
12. **Secrets: show deletion, not commenting-out** — for hardcoded secrets, the fix is DELETE the line + rotate the credential. Never show a commented-out version of the secret.
13. **Don't imply completeness** — say "Here are N issues to address" not "I found N issues" (the review may not be exhaustive).
14. **Crash-class defects include force unwraps** — `!` on Optional values (e.g., `URL(string:)!`, `array.first!`) are crash risks equal to `fatalError`. Always check for `!` postfix operators in the diff. Even `URL(string: "https://...")!` with a hardcoded valid URL is a crash pattern because the code teaches force-unwrapping as a habit.
15. **Positive findings must be verifiable** — don't claim "no X found" unless you've actually scanned for X. If commented-out code or debug prints exist, don't claim "no development debris".
16. **Code blocks in mrNote must not contain unflagged issues** — if you paste code that shows a naming problem (e.g., `@State var x`), either flag it or choose a different excerpt.
17. **Suggested fixes must actually solve the problem** — if a button's action is `print("TODO")`, the fix must implement the real functionality (e.g., delete the item), not replace it with another no-op like `dismiss()`. If the correct fix requires context you can't see, say "implement the actual [action] here" rather than suggesting a wrong implementation.
18. **Unguarded array access is a crash risk** — `array[0]`, `result.choices[0]`, `items[index]` without bounds checking crash at runtime if the collection is empty. Flag these under fatal-error-crash with the same severity as force unwraps.
19. **Confidence floor** — do not report findings with confidence below 0.70. If confidence is below 0.70 but above 0.60, cap severity at INFO. Reserve 0.95+ for mechanical/unambiguous patterns only.
20. **Verify context before flagging** — before marking NEGATIVE, check: (a) the pattern is not inside #Preview, #if DEBUG, or test files, (b) the pattern is not inside a container that resolves the issue (e.g., Image inside a labeled Button for accessibility), (c) the fix you suggest does not introduce a new issue.
21. **Quote-before-claim** — before writing ANY NEGATIVE finding, you MUST have the exact code snippet in your context from the diff or a grep/read result. Copy the EXACT line(s) into `evidence.snippets` character-for-character. Only THEN write the title, reasoning, and guidance using ONLY the symbols in that snippet. If you cannot find the pattern in a `+` line, the finding does not exist — do not report it.
22. **File attribution requires path verification** — when citing `File.swift:42`, the path MUST appear in `diff_stat.txt` or a hunk header in `diff.patch`. Never guess which file contains a pattern.
23. **Line count claims require measurement** — never state that a body "exceeds N lines" based on visual estimation. You MUST cite the opening `[L<n>]` and closing `[L<m>]` annotations and compute the difference. If the body is not fully visible in the diff, do not make line-count claims.
24. **Absence claims require exhaustive search** — if a finding claims something is MISSING (e.g., "no error handling", "no loading state"), you must demonstrate absence by checking all plausible locations in the diff. Show what IS there and explain why it doesn't satisfy the requirement. Claiming "I don't see X" without showing what you DID see is insufficient evidence.
25. **Function/variable names must be verbatim** — every function name, variable name, or type name in a finding's title, reasoning, or guidance MUST appear exactly as-is in the diff. Never paraphrase, rename, or approximate identifiers. If you write `fetchMotivation` but the diff says `loadMotivation`, the finding is wrong.
26. **Verify `async`/`throws`/access modifiers before claiming** — before making claims about a function's sync/async behavior, visibility, or error propagation, quote its full signature from the diff including all keywords (`async`, `throws`, `private`, etc.).

## delivery.mrNote Format

Target: **100-500 words**. Scale with finding count — more findings means more content is appropriate.

```
[1 sentence: what the MR does + 1 positive observation]. [1 sentence: what needs fixing before merge, with count].

**🔴 [CRITICAL Title]** · `File.swift:42`
[1 sentence: what + real-world consequence]. [code block: the fix, 2-5 lines]

**🟠 [MAJOR Title]** · `File.swift:87`
[1-2 sentences + code block if severity warrants it]

**🟡 [MINOR Title]** · `File.swift:12`
[1 sentence]

---
<sub>Hephaestus · automated review</sub>
```

STRICT emoji-to-severity mapping:
- 🔴 = CRITICAL only
- 🟠 = MAJOR only
- 🟡 = MINOR only
- Never use 🔴 for MAJOR findings. This distinction is essential for triage.

Rules:
- DON'T restate the MR title in the opening sentence. Describe what the code does in your own words.
- Open with a genuine positive + issue count ("Here are N issues to address before merge").
- No separate "What's Working Well" section — weave positives into the opening sentence.
- Severity-ordered. Omit empty severity tiers.
- CRITICAL and MAJOR: quote the defective line FIRST, then show the corrected version.
- MINOR: description only, no code block needed.
- No "Recommended Priority" section — the ordering IS the priority.
- No disclaimer banner at top — the footer handles attribution.
- No INFO-level findings in the mrNote.
- No horizontal rules between findings (only before footer).
