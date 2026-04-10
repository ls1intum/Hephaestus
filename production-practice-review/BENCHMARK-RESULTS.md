# Practice Review Benchmark Results

**Run Date:** 2026-04-10  
**Model:** gpt-5.4-mini via Pi agent  
**MRs Reviewed:** 9/10 (go72hog failed consistently)

## Summary

| Metric | Value |
|--------|-------|
| Overall accuracy | **73.0%** (84/115 verdicts) |
| Macro-avg F1 | **0.76** |
| Average cost/review | **$0.34** |
| Average duration | **6.2 min** |
| Average LLM tool calls | **23 rounds** |
| Total cost (9 reviews) | **$3.07** |
| Total tokens | **883,242** (424K in + 459K out) |

## Per-Class Metrics

| Class | TP | FP | FN | Precision | Recall | F1 |
|-------|---:|---:|---:|----------:|-------:|---:|
| NEGATIVE | 28 | 11 | 18 | 71.8% | 60.9% | 0.66 |
| POSITIVE | 47 | 19 | 11 | 71.2% | 81.0% | 0.76 |
| NOT_APPLICABLE | 9 | 1 | 2 | 90.0% | 81.8% | 0.86 |

## Per-Practice Performance (sorted by accuracy)

| Practice | N | Acc | NEG F1 | POS F1 | Notes |
|----------|--:|----:|-------:|-------:|-------|
| error-state-handling* | 9 | 89% | 1.00 | 0.86 | Top performer (improved script) |
| fatal-error-crash | 9 | 89% | 0.80 | 1.00 | High-confidence safety check |
| hardcoded-secrets | 9 | 89% | 1.00 | 0.80 | Zero false negatives |
| silent-failure-patterns* | 9 | 89% | 0.89 | 0.80 | Improved script helped |
| state-ownership* | 9 | 89% | 0.67 | 0.93 | Improved script helped |
| mr-description-quality | 8 | 88% | 0.80 | 0.91 | Strong for metadata-only practice |
| view-logic-separation | 9 | 78% | 0.50 | 0.86 | Misses some subtle violations |
| accessibility-support | 9 | 67% | 0.77 | 0.40 | Misses Dynamic Type issues |
| preview-quality | 9 | 67% | 0.40 | 0.77 | Over-marks as positive |
| commit-discipline | 8 | 62% | 0.73 | 0.40 | Misses generic commit messages |
| code-hygiene | 9 | 56% | 0.67 | 0.33 | Misses commented-out code |
| view-decomposition* | 9 | 56% | 0.00 | 0.71 | Over-flags as NEGATIVE |
| meaningful-naming* | 9 | 33% | 0.00 | 0.50 | Worst performer — systematic confusion |

\* = practice with improved precompute script

## Error Patterns

### False Negatives (17 — system missed real issues)
- code-hygiene: 4x — fails to detect commented-out code and print statements
- accessibility-support: 3x — misses fixed font sizes blocking Dynamic Type
- commit-discipline: 3x — marks vague commits as acceptable
- meaningful-naming: 3x — marks poor names as acceptable
- view-logic-separation: 2x — misses networking in views

### False Positives (11 — system flagged non-issues)
- view-decomposition: 4x — over-flags view bodies as "too large"
- meaningful-naming: 3x — flags acceptable names as violations
- preview-quality: 2x — flags existing previews as insufficient
- silent-failure-patterns: 1x
- state-ownership: 1x

## Qualitative Assessment

### Strengths
- MR summary notes are professional, context-specific, and well-structured
- Diff notes target correct files/lines with actionable fix suggestions
- High-severity practices (secrets, crashes) have excellent accuracy
- Each review covers all 13 practices with nuanced, evidence-backed reasoning
- Code fix suggestions are concrete, not generic

### Weaknesses
- `meaningful-naming` needs a fundamentally different detection strategy
- `view-decomposition` threshold too aggressive — penalizes acceptable view sizes
- Several practices miss subtle issues (commented code, generic commits)
- 1/10 MRs consistently failed (go72hog) — likely context window limitation
- Feedback delivery suppressed for merged MRs (5/9 reviews visible on GitLab)

## Recommendations

1. **meaningful-naming**: Rework precompute to focus on clear violations (single-letter vars, mismatched types) rather than style preferences
2. **view-decomposition**: Raise the body-size threshold in the precompute script or add a "tolerable complexity" baseline
3. **code-hygiene**: Strengthen grep patterns for `print(`, commented blocks, and debug artifacts
4. **commit-discipline**: Add tighter checks for generic messages like "fix", "update", "changes"
5. **Reliability**: Investigate go72hog failure mode — may need to increase agent timeout or reduce context for large MRs
