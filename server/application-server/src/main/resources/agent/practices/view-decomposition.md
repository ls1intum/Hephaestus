# View Decomposition
**Category:** SwiftUI

## What This Practice Means
A SwiftUI View's `body` should read like a short table of contents. When it grows too large or mixes too many concerns, extract named subviews.

## Positive Signals (-> verdict POSITIVE)
- Parent `body` < 30 lines, composed of clearly named child views (`RecipeHeaderView`, `IngredientsListView`)
- Subview structs defined in the same file or dedicated files
- Each subview handles a single concern

## Negative Signals (-> verdict NEGATIVE)

### Body length — you MUST anchor to diff line numbers
To claim a body exceeds a threshold, you MUST:
1. Quote the opening line: `[L<n>] var body: some View {`
2. Quote the closing line: `[L<m>] }` (the matching brace)
3. Compute: body lines = m - n - 1. State the actual count in your reasoning.
4. If the body is not fully visible in the diff (endpoints not in `+` lines), do NOT flag body length.
**Do NOT confuse file length with body length.** A 200-line file with a 30-line body and extracted subviews is GOOD decomposition.

### Thresholds (only after verifiable counting)
- `var body` exceeds **100 lines** (non-blank, non-comment) with **3+ distinct UI concerns** (MAJOR)
- `var body` is **60-100 lines** with mixed concerns or **>3 nesting levels** (MINOR)
- View struct has >8 `@State` properties, indicating it manages too many responsibilities (MINOR)
- >12 `@State` properties (MAJOR)
- Deeply nested closures: `VStack { HStack { ForEach { if { ... } } } }` beyond 3 structural levels

### Structural duplication
- Two or more View structs in the same MR that render the same model entity with similar visual structure (e.g., `HabitCard` and `HabitCardView` both rendering a `Habit`). Only flag if BOTH views appear in `+` lines of the diff. (MINOR)

## False-Positive Exclusions
- Long file but short `body` -- logic properly extracted into child structs in the same file
- `Form`/`List` with 20+ simple rows (TextField, Toggle, Picker) -- low complexity despite line count
- Non-View files, model files, app entry points: skip
- Small views where the body is naturally under 60 lines
- **Body not fully visible in the diff** — if you cannot see both the opening `var body` and its closing `}` with `[L<n>]` annotations, do not flag body length

## Severity Guide
- CRITICAL: never
- MAJOR: body >100 lines with 3+ distinct concerns; >12 `@State` properties
- MINOR: body 60-100 lines or >8 `@State` properties; >3 nesting levels
