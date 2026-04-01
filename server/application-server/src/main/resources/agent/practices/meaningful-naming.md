# Meaningful Naming
**Category:** Readability

**Scope:** Identifiers must clearly communicate their purpose. Every NEGATIVE finding MUST include a specific alternative name — if you cannot suggest a clearly better name, do not flag. Limit: 3-4 naming issues per file.

## Positive Signals
- Descriptive names revealing intent
- Swift conventions: camelCase properties, UpperCamelCase types
- Boolean names prefixed with `is`/`has`/`should`
- `if let company = partial.company` (shadowing with meaningful name)

## Negative Signals
- **Single-letter names outside trivial scope**: `let c = ColorModel(...)` used across multiple lines -> suggest `colorModel`
- **Single-letter optional binding**: `if let c = partial.company` -> `if let company = partial.company`
- **Generic names in non-generic context**: `let data: String` holding a temperature -> `formattedTemperature`
- **Swift convention violations**: `snake_case` properties/enum cases (e.g., `case celebrate_winner` -> `celebrateWinner`)
- **Misleading names**: `var dirty: Bool` -> `isDirty`; verb-less function returning a value: `func recommendation()` -> `generateRecommendation()`

## Exclusions — Do NOT Flag
- Loop indices: `i`, `j`, `k`, `n`, `idx`
- Closure shorthand: `$0`, `$1`
- Single-line `ForEach`/`map` closures: `ForEach(items) { m in Text(m.rawValue) }`
- Standard abbreviations: `id`, `url`, `db`, `api`, `lhs`, `rhs`, `vc` (UIKit)
- `vm` in short scopes (2-3 lines)
- `item` in ForEach with typed collection
- `result` in TaskGroup/async context
- `data`/`response` from `URLSession.shared.data(from:)` — standard Swift pattern
- Decoder containers: `let c = try decoder.container(...)` — idiomatic Codable
- Short names in single-line string formatting

## Verification — MUST Complete Before Any Finding
1. **Quote the exact declaration** from the diff (`func`/`var`/`let`, full signature). If not on a `+` line, do not flag.
2. **Check the full signature** before claims about sync/async or parameter semantics.
3. **Use the identifier exactly as written** — never paraphrase. If the diff says `loadMotivation`, do not write `fetchMotivation`.
4. If you cannot paste the exact `+` line, the finding is fabricated — discard it.

## Severity
- **CRITICAL**: Never
- **MAJOR**: Never
- **MINOR**: All naming issues
