# Error & State Handling
**Category:** UI Quality

**Scope:** Views that fetch remote/network data must show all three UI states: loading indicator, error display, and success content. Users must never see a blank screen during fetch or after failure.

## Positive Signals
- `ProgressView` / shimmer / `.redacted(reason: .placeholder)` while data loads
- Error state shown to user: `.alert`, error `Text`, `ContentUnavailableView`, retry button
- Clear three-state pattern: loading -> success / error
- `isLoading` boolean or `LoadingState` enum driving conditional UI

## Negative Signals
- Network fetch in `.task {}` with no loading indicator — blank screen while loading
- Error caught but no UI update — user sees nothing on failure
- Missing BOTH loading AND error states
- Missing ONE of loading or error state

**Only evaluate views that perform network fetching.** Indicators: `URLSession`, service/fetcher class calls, async functions contacting remote servers (`fetchData()`, `loadData()`), AI/LLM API calls.

## Exclusions — Do NOT Flag
- Views using only: `@Query` (SwiftData), `@AppStorage`, `UserDefaults`, local file reads, timer-based operations
- Non-view files, model files, service files

## Verification — MUST Complete Before Flagging

**For "missing error state":**
1. Identify the catch/failure block — cite `[L<n>]`
2. Search the SAME FILE for `@State`/`@Published` error properties being set
3. Search for UI rendering of error: `Text(` referencing error, `.alert(`, `ContentUnavailableView`
4. If ANY match found in steps 2-3, error state IS handled — verdict POSITIVE

**For "missing loading state":**
1. Identify the async call site — cite `[L<n>]`
2. Search the SAME FILE for `isLoading`, `loading`, `LoadingState`, `ProgressView`, `.redacted`
3. If ANY match found, loading state IS handled — verdict POSITIVE

**A false positive here destroys student trust.** If you cannot prove absence by checking the diff, do not flag.

## Relationship to silent-failure-patterns
This practice evaluates the USER-FACING UI layer. silent-failure-patterns evaluates the CODE layer (catch blocks, try? usage). They can coexist on the same code evaluating different dimensions.

## Severity
- **CRITICAL**: Never
- **MAJOR**: Both loading AND error states missing for a network-fetching view
- **MINOR**: One of loading or error state missing
