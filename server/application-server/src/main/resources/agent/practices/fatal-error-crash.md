# Fatal Error / Crash Risk
**Category:** Reliability

**Scope:** Code patterns that unconditionally terminate the app at runtime. Every crash-risk pattern has a safe Swift alternative.

## Positive Signals
- `guard let` / `if let` for optional unwrapping
- `try`/`catch` instead of `try!`
- `as?` instead of `as!`
- `??` default values for optionals
- Bounds checking before array subscript access

## Negative Signals

**Explicit termination:**
- `fatalError(` — unconditional process kill
- `preconditionFailure(` — unconditional process kill

**Force unwrapping (most common crash vectors):**
- `try!` — crashes if expression throws
- `someVar!`, `dict[key]!`, `array.first!` — crashes on nil
- `as! SomeType` — crashes on type mismatch

**Unguarded collection access:**
- `array[0]`, `result.choices[0]`, `items[index]` without bounds checking — crashes on empty collection

**Identifying `!` in diffs:** Look for `!` as postfix operator on values. Do NOT confuse with: `!=` (not-equal), `Type!` in type annotations (IUO declarations), `?` (optional chaining), `if let`/`guard let` (safe unwrapping), `!flag` (boolean negation).

## Exclusions — Do NOT Flag
- Code on lines without `+` prefix (pre-existing, not changed in this MR) — **#1 false positive source**
- `assert()` and `assertionFailure()` — stripped from release builds
- Code inside `#Preview` blocks or `#if DEBUG`
- Files importing `XCTest` or `Testing`
- `required init?(coder:)` with `fatalError` — compiler-required, unreachable in SwiftUI
- `@unknown default` with `fatalError` — Swift-recommended exhaustive switch pattern
- Sample/preview data classes with `isStoredInMemoryOnly: true`
- Force unwrap after explicit nil check: `if x != nil { use(x!) }` — safe but inelegant (INFO at most)
- `@IBOutlet` implicitly unwrapped optionals — UIKit convention
- Third-party packages (check copyright headers)

## Edge Cases
- `precondition()` with user-input-dependent condition: MINOR
- `array[0]` only flag when array could plausibly be empty from surrounding context

## Severity
- **CRITICAL**: `fatalError`/`try!`/force unwrap in ViewModel, Service, or Manager methods on user-interaction paths
- **MAJOR**: `fatalError` in `@main` App struct `ModelContainer` init; `try!` on I/O (file, network, JSON decoding); force unwrap on network response data; unguarded collection access on dynamic data
- **MINOR**: `fatalError` in config/plist loaders; force unwrap on near-certain values (e.g., `URL(string: "https://example.com")!`); `fatalError` in fully-controlled switch default
