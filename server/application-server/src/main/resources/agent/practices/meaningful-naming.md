# Meaningful Naming
**Category:** Readability

## What This Practice Means
Identifiers must clearly communicate their purpose. Every negative finding MUST include a specific alternative name — if you cannot suggest a clearly better name, do not flag. Limit: 3-4 naming issues per file max.

## Positive Signals (-> verdict POSITIVE)
- Descriptive variable/function names that reveal intent
- Swift naming conventions followed: camelCase properties, UpperCamelCase types
- Boolean names prefixed with `is`/`has`/`should`
- `if let company = partial.company` (shadowing with meaningful name)

## Negative Signals (-> verdict NEGATIVE)
- **Single-letter names outside trivially small scope**: `let c = ColorModel(...)` used across multiple lines -> suggest `colorModel`; `@State private var tm = TimerManager()` -> suggest `timerManager`
- **Single-letter optional binding when property name is available**: `if let c = partial.company` -> `if let company = partial.company`
- **Generic names in non-generic context**: `let data: String` displaying a temperature -> suggest `formattedValue` or `displayValue`
- **Swift convention violations**: `snake_case` enum cases or properties (e.g., `case celebrate_winner` -> `celebrateWinner`; `let created_at` -> use CodingKeys + `createdAt`)
- **Misleading/ambiguous names**: `var dirty: Bool` -> `isDirty`; verb-less function returning a value: `func recommendation()` -> `generateRecommendation()`

### Critical False-Positive Exclusions
Do NOT flag:
- Loop indices: `i`, `j`, `k`, `n`, `idx`
- Closure shorthand: `$0`, `$1`
- Single-line `ForEach`/`map` closures: `ForEach(items) { m in Text(m.rawValue).tag(m) }`
- Standard abbreviations: `id`, `url`, `db`, `api`, `lhs`, `rhs`, `r`/`g`/`b` in color code, `vc` in UIKit
- `vm` in short if-let unwraps (2-3 lines)
- `item` in ForEach with well-named typed collection
- `result` in TaskGroup/async context
- `data` and `response` from `URLSession.shared.data(from:)` — standard Swift pattern
- Decoder containers: `let c = try decoder.container(...)` — idiomatic Codable
- Short names in string formatting one-liners

### Only analyze CHANGED lines in the diff. Pre-existing code is context only.

### Verification Mandate — MUST complete before any NEGATIVE finding
1. **Quote the exact declaration** from the diff (including `func`/`var`/`let`, parameter labels, return type, and `async`/`throws` keywords). If you cannot find the exact declaration on a `+` line, do not flag it.
2. **Check the full signature** before making claims about sync/async, parameter semantics, or return type. A function declared `async func loadMotivation()` is NOT "a synchronous function with an async-sounding name."
3. **Use the identifier exactly as written** in the diff — never paraphrase or approximate a name. If the diff says `loadMotivation`, do not write `fetchMotivation` in your finding.
4. If you cannot paste the exact `+` line from the diff that contains the identifier you want to flag, the finding is fabricated — discard it.

## Severity Guide
- CRITICAL: Never used for this practice
- MAJOR: Never used for this practice
- MINOR: All naming issues — single-letter names, generic names, convention violations, misleading names
