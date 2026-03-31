# Fatal Error / Crash Risk
**Category:** Reliability

## What This Practice Means
Code must not contain patterns that unconditionally terminate the app at runtime. Every crash-risk pattern has a safe Swift alternative.

## Positive Signals (-> verdict POSITIVE)
- `guard let` / `if let` used to safely unwrap optionals
- `try`/`catch` instead of `try!`
- `as?` instead of `as!`
- `??` default values for optional unwrapping
- `ModelContainer` initialization wrapped in do/catch with fallback UI

## Negative Signals (-> verdict NEGATIVE)

### Category A: Explicit termination calls
- `fatalError(` — unconditionally kills the process
- `preconditionFailure(` — unconditionally kills the process

### Category B: Force unwrapping (most common crash vectors)
- `try!` — crashes if expression throws
- Force unwrap: `someVar!`, `dict[key]!`, `array.first!` — crashes on nil
- Force downcast: `as! SomeType` — crashes on type mismatch

### Identifying force unwraps in diffs
Look for `!` as postfix operator: `variable!`, `result.property!`, `dictionary[key]!`, `array.first!`

Do NOT confuse with: `!=` (not-equal), `Type!` in type annotations (IUO declarations), `?` (optional chaining), `if let`/`guard let` (safe unwrapping).

### Diff Scope — CHANGED CODE ONLY
**Only flag patterns on `+` lines (added/modified code in the diff).** Lines without a `+` prefix are context — they show pre-existing code that the student did NOT write or modify in this MR. Even if you see a dangerous `fatalError` or force unwrap in a context line, do NOT flag it. The student cannot be held responsible for code they did not change.

Before every NEGATIVE finding, confirm: "The line I am flagging has a `+` prefix in the diff and I can cite its `[L<n>]` annotation."

### Critical False-Positive Exclusions
Do NOT flag — check these BEFORE flagging:
0. **Unchanged code** — any pattern on a line WITHOUT `+` prefix in the diff. This is the #1 source of false positives.
1. `assert()` and `assertionFailure()` — stripped from release builds
2. Code inside `#Preview` blocks
3. Code inside `#if DEBUG` blocks
4. Files importing `XCTest`
5. `required init?(coder:)` with `fatalError` — compiler-imposed, unreachable in SwiftUI
6. `@unknown default` with `fatalError` — Swift-recommended pattern
7. `SampleData` classes used ONLY in previews with `isStoredInMemoryOnly: true`
8. Force unwrap immediately after nil check: `if dict[key] != nil { use(dict[key]!) }` — safe (INFO at most)
9. `@IBOutlet` implicitly unwrapped optionals — UIKit convention
10. Third-party packages or Apple sample code (check copyright headers)

### Edge cases
- `precondition()` (not `preconditionFailure`) with user-input-dependent condition: MINOR
- Array subscript `array[0]` without bounds check: only flag if array could be empty from context

## Severity Guide
- CRITICAL: `fatalError`/`try!`/force unwrap in ViewModel, Service, or Manager methods during normal user interaction
- MAJOR: `fatalError` in `@main` App struct ModelContainer init (Xcode template pattern); `try!` on I/O (file, network, JSON decoding); force unwrap on network response data
- MINOR: `fatalError` in Secrets/plist loader; `fatalError`/`try!` in fully-controlled switch default; force unwrap on very-likely-non-nil values (e.g., `URL(string: "https://example.com")!`)
