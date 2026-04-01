# Code Hygiene
**Category:** Craftsmanship

**Scope:** Committed code must be intentional — free of debug prints, commented-out code blocks, and IDE artifacts. Git history preserves old code; comments should not.

## Positive Signals
- Clean diff with no debug traces or commented-out code blocks
- Proper logging via `Logger`/`os.Logger` instead of `print()`
- Comments that explain WHY, not dead code

## Negative Signals
- **Commented-out code blocks**: 2+ consecutive lines of commented code (not explanatory comments)
- **Debug `print()` in normal code flow**: trace prints in functions/methods. NOT in catch blocks — that belongs to silent-failure-patterns.
- **Print-only buttons/handlers**: `Button("X") { print("tapped") }` where `print()` is the ONLY action — strongest signal of unfinished code
- **Xcode placeholder tokens**: `/*@START_MENU_TOKEN@*/.../*@END_MENU_TOKEN@*/`

### Print-in-button distinction
- `Button { print("X") }` with NO other logic -> MAJOR (non-functional UI element)
- `Button { doThing(); print("X") }` with real logic + print -> MINOR (leftover debug trace)

## Exclusions — Do NOT Flag
- Explanatory comments clarifying intent
- `// MARK:` section dividers, `///` doc comments, `// TODO:`, `// FIXME:`
- Code inside `#if DEBUG` blocks
- `Logger`/`os.Logger` usage (proper structured logging)
- `print()` inside catch blocks — belongs to silent-failure-patterns
- Single inline comments explaining non-obvious lines
- Standard Xcode file headers where filename matches

## Severity
- **CRITICAL**: Never
- **MAJOR**: Print-only buttons (non-functional UI), Xcode placeholder tokens
- **MINOR**: Debug print alongside real logic, commented-out code blocks (2+ lines)
