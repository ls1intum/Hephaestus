# State Ownership Misuse
**Category:** SwiftUI

## What This Practice Means
Mutable data has ONE owner. `@State` means "I create and own this." `@Binding` means "parent owns it, I mutate it." `@Bindable` means "I received an `@Observable` and need `$`-bindings." Misuse causes frozen data or broken reactivity.

## Positive Signals (-> verdict POSITIVE)
- `@State private var vm = MyViewModel()` -- view creates and owns an `@Observable`
- `@State private var showSheet = false` -- local UI toggle
- `@Binding var text: String` in a child that mutates parent's `@State String`
- `@Bindable var vm: SomeObservable` -- received `@Observable`, needs property bindings
- `var manager: Manager` (no wrapper) -- read-only observation of `@Observable`
- `@Environment(ThemeManager.self)` for injected `@Observable`

## Negative Signals (-> verdict NEGATIVE)
- **Pattern A -- `@State` where `@Binding` needed (MAJOR):** Child view stores parent-passed value as `@State var x: T` (no default, not private). Creates a frozen copy after first render. Fix: `let x` (read-only) or `@Binding var x` (read-write).
- **Pattern B -- `@Binding` where `@Bindable` needed (MINOR):** `@Binding var manager: SomeObservable` -- wrong wrapper for `@Observable` class. Fix: `@Bindable var manager`.
- **Pattern C -- `@State` for non-owned `@Observable` (MAJOR):** `@State var viewModel: VM` where the instance is passed from a parent, not created in this view. Fix: plain `var` or `@Bindable`.
- **Pattern D -- Excessive binding drilling (MINOR):** 5+ individual `@Binding` params that all originate from a single `@Observable` object. Fix: pass the `@Observable` directly with `@Bindable`.
- **Pattern E -- `@State` not private (INFO):** `@State var isVisible = false` without `private`. Suggests potential misuse as an external input.

## False-Positive Exclusions
- `@State private var viewModel = VM()` -- view creates instance: CORRECT, never flag
- `@State private var viewModel: VM` with `init` using `State(initialValue: VM(...))` -- view creates instance: CORRECT
- `@Previewable @State var x` in `#Preview` blocks -- preview-only code, never flag
- If only the child is in the diff (parent not visible), reduce confidence. Do not report above 0.90 unless parent-child call site is visible.
- When uncertain, prefer not flagging over a low-confidence NEGATIVE. False positives here confuse contributors about an already-complex ownership model.

## Severity Guide
- CRITICAL: never
- MAJOR: Pattern A (`@State` where `@Binding` needed), Pattern C (`@State` for non-owned `@Observable`)
- MINOR: Pattern B (`@Binding` where `@Bindable`), Pattern D (excessive binding drilling)
