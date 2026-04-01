# View Decomposition
**Category:** SwiftUI

**Scope:** A SwiftUI View's `body` should read like a short table of contents. Oversized or multi-concern bodies need extraction into named subviews.

## Positive Signals
- Parent `body` < 30 lines, composed of named child views
- Subview structs defined in same file or dedicated files
- Each subview handles a single concern

## Negative Signals

### Body Length — MUST Anchor to Diff Line Numbers
To claim a body exceeds a threshold:
1. Quote the opening line: `[L<n>] var body: some View {`
2. Quote the closing line: `[L<m>] }` (matching brace)
3. Compute: body lines = m - n - 1. State the count explicitly.
4. If body boundaries are not both visible in the diff, do NOT flag body length.

**Do NOT confuse file length with body length.** A 200-line file with a 25-line body and extracted subviews is GOOD.

### Thresholds (only after verifiable counting)
- `body` exceeds **100 lines** with **3+ distinct UI concerns** (MAJOR)
- `body` is **60-100 lines** with mixed concerns or **>3 nesting levels** (MINOR)
- >12 `@State` properties in one View (MAJOR)
- >8 `@State` properties (MINOR)
- Deeply nested closures: `VStack { HStack { ForEach { if { ... } } } }` beyond 3 structural levels

### Structural Duplication
Two+ View structs in the same MR rendering the same model entity with similar visual structure (e.g., `HabitCard` and `HabitCardView` both rendering `Habit`). Only flag if BOTH appear on `+` lines. (MINOR)

## Exclusions — Do NOT Flag
- Long file but short body — logic properly extracted into child structs
- `Form`/`List` with many simple rows (TextField, Toggle, Picker) — low complexity despite line count
- Non-View files, model files, app entry points
- Small views where body is naturally under 60 lines
- Body not fully visible in the diff — cannot verify length

## Severity
- **CRITICAL**: Never
- **MAJOR**: Body >100 lines with 3+ concerns; >12 @State properties
- **MINOR**: Body 60-100 lines or >8 @State properties; >3 nesting levels; structural duplication
