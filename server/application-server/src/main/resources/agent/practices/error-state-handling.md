# Error & State Handling
**Category:** UI Quality

## What This Practice Means
Views that fetch remote/network data must show all three UI states: loading indicator, error display, and success content. Users should never see a blank screen during a fetch or after a failure.

## Positive Signals (-> verdict POSITIVE)
- `ProgressView` / shimmer / `.redacted(reason: .placeholder)` shown while data loads
- Error state displayed to user: `.alert`, error `Text`, `ContentUnavailableView`, retry button
- Clear three-state pattern: loading -> success content / error display
- `isLoading` boolean or `LoadingState` enum driving conditional UI

## Negative Signals (-> verdict NEGATIVE)
- Network fetch in `.task {}` with no `ProgressView` or loading indicator — user sees empty/blank screen
- Error caught and logged/printed but NO UI update — user sees nothing on failure
- Missing both loading AND error states (MAJOR)
- Missing one of loading or error state (MINOR)

### Scope — ONLY evaluate views with network fetching
Indicators of network fetching:
- `URLSession.shared.data` calls
- Calls to service/fetcher classes making HTTP requests
- Async functions like `fetchData()`, `loadData()`, `sendMessage()` contacting remote servers
- AI/LLM API calls

### Critical False-Positive Exclusions
Do NOT flag views that only use:
- SwiftData `@Query` (synchronous from view's perspective)
- `UserDefaults` / `@AppStorage`
- Local file reads
- Timer-based operations without network

### Relationship to silent-failure-patterns
This practice evaluates the USER-FACING UI layer. silent-failure-patterns evaluates the CODE/DEVELOPER layer. They can overlap on the same code but evaluate different dimensions.

### Verification Checklist — MUST complete before flagging

**For "missing error state":**
1. Identify the catch/failure block — quote the `catch` or error-handling line with its `[L<n>]`
2. Search the SAME FILE in the diff for `@State`/`@Published` error properties being set (e.g., `errorMessage = `, `showError = true`)
3. Search for UI rendering of the error: `Text(` referencing error, `.alert(`, `ContentUnavailableView`
4. If ANY of steps 2-3 finds a match, the error state IS handled — verdict POSITIVE

**For "missing loading state":**
1. Identify the async call site — quote the `.task { }` or `.onAppear { }` line
2. Search the SAME FILE for `isLoading`, `loading`, `LoadingState`, `ProgressView`, `.redacted`
3. If ANY match found, the loading state IS handled — verdict POSITIVE

**A false positive here destroys student trust.** If you cannot prove absence by checking the diff, do not flag.

## Severity Guide
- CRITICAL: Never used for this practice
- MAJOR: Both loading AND error states missing for a network-fetching view
- MINOR: One of loading or error state missing
