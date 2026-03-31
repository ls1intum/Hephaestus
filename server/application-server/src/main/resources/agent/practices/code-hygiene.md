# Code Hygiene
**Category:** Craftsmanship

## What This Practice Means
Committed code should be intentional — free of development debris like debug prints, commented-out code, and IDE artifacts. Git history preserves old code; comments should not.

## Positive Signals (-> verdict POSITIVE)
- Clean diff with no debug traces, no commented-out code blocks
- Proper logging via `Logger`/`os.Logger` instead of `print()`
- Intentional comments that explain WHY, not dead code

## Negative Signals (-> verdict NEGATIVE)
- **Commented-out code blocks** (2+ consecutive lines of commented code, not explanatory comments)
- **Debug `print()` in normal code flow** — trace prints in functions/methods (NOT in catch blocks — that belongs to silent-failure-patterns)
- **Print-only buttons/handlers** — `Button("X") { print("tapped") }` where `print()` is the ONLY action (strongest signal)
- **Xcode placeholder tokens** — `/*@START_MENU_TOKEN@*/.../*@END_MENU_TOKEN@*/`
- **Stale file headers** — header comment says a different filename than the actual file
- **Garbled/corrupted comments** — e.g., `// MARK: - API Recipe Mod// MARK: - API Recipe Model`

### Critical False-Positive Exclusions
Do NOT flag:
- Explanatory comments clarifying intent
- `// MARK:` section dividers, `///` doc comments
- `// TODO:` and `// FIXME:` markers
- Code inside `#if DEBUG` blocks
- `Logger`/`os.Logger` usage (proper structured logging)
- Standard Xcode file headers where filename matches
- `print()` inside catch blocks — belongs to silent-failure-patterns practice
- Single inline comments explaining non-obvious lines

### Print-in-button distinction
- `Button { print("X") }` with NO other logic -> MAJOR (non-functional UI)
- `Button { doThing(); print("X") }` with real logic + print -> MINOR (debug trace)

## Severity Guide
- CRITICAL: Never used for this practice
- MAJOR: Print-only buttons (non-functional UI), Xcode placeholder tokens
- MINOR: Debug print alongside real logic, commented-out code blocks (2+ lines)
