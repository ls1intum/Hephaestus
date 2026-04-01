# State Ownership Misuse
**Category:** SwiftUI

**Scope:** Correct use of SwiftUI property wrappers. `@State` = "I create and own this." `@Binding` = "parent owns it, I mutate it." `@Bindable` = "I received an @Observable and need $-bindings." Misuse causes frozen data or broken reactivity.

## Positive Signals
- `@State private var vm = MyViewModel()` — view creates and owns an @Observable
- `@State private var showSheet = false` — local UI toggle
- `@Binding var text: String` in child that mutates parent's @State
- `@Bindable var vm: SomeObservable` — received @Observable, needs property bindings
- `var manager: Manager` (no wrapper) — read-only observation of @Observable
- `@Environment(ThemeManager.self)` for injected @Observable

## Negative Signals

**Pattern A — `@State` where `@Binding` needed (MAJOR):**
Child view stores parent-passed value as `@State var x: T` (no default, not private). Creates a frozen copy after first render. Fix: `let x` (read-only) or `@Binding var x` (read-write).

**Pattern B — `@Binding` where `@Bindable` needed (MINOR):**
`@Binding var manager: SomeObservable` — wrong wrapper for @Observable class. Fix: `@Bindable var manager`.

**Pattern C — `@State` for non-owned `@Observable` (MAJOR):**
`@State var viewModel: VM` where instance is passed from parent, not created in this view. Fix: plain `var` or `@Bindable`.

**Pattern D — Excessive binding drilling (MINOR):**
5+ individual `@Binding` params all from one @Observable object. Fix: pass the @Observable directly with `@Bindable`.

**Pattern E — `@State` not private (INFO):**
`@State var isVisible = false` without `private`. Signals potential misuse as external input.

## Exclusions — Do NOT Flag
- `@State private var viewModel = VM()` — view creates instance: CORRECT
- `@State private var viewModel: VM` with `init` using `State(initialValue:)` — view creates instance: CORRECT
- `@Previewable @State var x` in `#Preview` blocks
- If only the child view is in the diff (parent not visible), cap confidence at 0.85
- When uncertain, prefer POSITIVE over low-confidence NEGATIVE. False positives confuse contributors about an already-complex ownership model.

## Severity
- **CRITICAL**: Never
- **MAJOR**: Pattern A, Pattern C
- **MINOR**: Pattern B, Pattern D
