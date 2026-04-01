# View-Logic Separation
**Category:** SwiftUI Architecture

**Scope:** SwiftUI View structs must not contain business logic. Networking, encoding/decoding, complex algorithms, and multi-step persistence belong in @Observable view models or service types.

## Positive Signals
- View delegates work via `.task { await viewModel.load() }` to an @Observable class
- Networking, JSON decoding, and domain logic live in separate types
- View contains only: @Query, simple CRUD (context.delete, single insert), computed filters, conditional rendering, navigation, sheet/alert presentation

## Negative Signals

**Networking in View (MAJOR):**
`URLSession`, `URLRequest`, `HTTPURLResponse`, `URLComponents` used inside a View struct or its private methods.

**JSON coding in View (MAJOR):**
`JSONDecoder().decode(...)` or `JSONEncoder().encode(...)` called inside a View.

**Domain algorithm in View (MAJOR):**
Private function >15 lines of non-UI logic with domain-specific branching (scoring, recommendations, multi-step pipelines).

**Heavy persistence orchestration (MINOR):**
`FetchDescriptor` + conditional insert/update + state reconciliation spanning >15 lines in a View.

**3+ private functions** that each manipulate domain data (not UI state).

**Complex for/while loops** implementing domain algorithms inside a View.

## Exclusions — Do NOT Flag
- `.onDelete { indexSet in for i in indexSet { context.delete(items[i]) } }` — idiomatic SwiftData
- Simple `addItem()` that creates one model, inserts, optionally saves
- `var filteredItems: [Item] { items.filter { $0.isActive } }` — simple computed filter
- `await viewModel.fetchData()` in `.task` — this IS the correct pattern
- @Query + conditional rendering
- Single `modelContext` delete/insert in a button action
- Non-View files (models, services, view models, app entry points, preview-only files)

## Verification — Confirm Logic Is in the View Struct
1. The code is inside a `struct` conforming to `View` (check for `: View` on a `+` line)
2. The code is on `+` lines — pre-existing code is not flaggable
3. For ">15 lines" claims — cite start and end `[L<n>]` lines and state the actual count
4. Confirm the code is not in a separate file — check file path in the diff hunk header

## Severity
- **CRITICAL**: Never
- **MAJOR**: Networking in View, JSON coding in View, >15-line domain algorithm in View
- **MINOR**: Heavy persistence orchestration (>15 lines with business-rule branching)
