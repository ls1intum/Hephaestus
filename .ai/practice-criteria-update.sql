-- Practice criteria rewrite v10
-- Changes: Remove guidanceMethod, NEEDS_REVIEW, NOT_APPLICABLE, Tone sections
-- Add "when in doubt suppress", tighten FP exclusions, binary verdicts only

-- 1. mr-description-quality
UPDATE practice SET criteria = 'Assess whether the contributor communicated their changes effectively through the MR title and description.

## What You Are Evaluating

The MR title and body (description). Also consider diff scope (number of files, lines added/removed).

## Title Quality

GOOD titles:
- "#19: Feedback on user selection and game logic" — issue reference + feature description
- "#28: Create Dashboard View" — concise, specific
- "[Day 4]: Subsystem decomposition" — contextual, clear scope

BAD titles:
- "Update" / "Fix" / "Changes" / "small fixes" — no information content
- "Draft: Add changes" — completely generic
- "further improvements" — says nothing about what improved

ACCEPTABLE (do not penalize):
- "Add logging" — brief but clear intent
- "Settings View" — identifies the feature area
- Auto-generated titles from GitLab (kebab-case branch names)

## Description Quality

**EMPTY (verdict: NEGATIVE, confidence: 0.95+)**
Body is null, empty, or whitespace only.

**ISSUE-REFERENCE-ONLY (verdict: NEGATIVE, confidence: 0.85-0.95)**
Body contains only an issue number like "#42" with zero explanation.

**UNMODIFIED TEMPLATE (verdict: NEGATIVE, confidence: 0.90)**
Body contains the MR template but placeholder text is still present with no custom content.

**MINIMAL BUT PRESENT on a large MR (verdict: NEGATIVE, confidence: 0.65)**
Essentially restates the title on a 10+ file MR. For 1-3 file changes, this is acceptable — lean POSITIVE.

**ADEQUATE (verdict: POSITIVE, confidence: 0.80)**
Custom content explaining what was done. Issue is linked.

**EXCELLENT (verdict: POSITIVE, confidence: 0.90+)**
Clear description of what and why, issue link, testing instructions.

## Proportionality Rule

Expected description depth scales with MR complexity:
- 1-2 files, simple change: descriptive title alone may suffice
- 3-9 files: should explain what was built and link the issue
- 10+ files: should explain the feature, list key changes, link issues

## Verdict Decision Logic

1. Body empty AND >1 file changed: NEGATIVE, severity MAJOR, confidence 0.95
2. Body empty AND exactly 1 trivial file: NEGATIVE, severity MINOR, confidence 0.85
3. Body is only an issue reference: NEGATIVE, severity MINOR, confidence 0.90
4. Body is an unmodified template: NEGATIVE, severity MINOR, confidence 0.92
5. Minimal content AND large MR (8+ files): NEGATIVE, severity MINOR, confidence 0.65
6. Minimal content AND small MR (1-3 files): POSITIVE, severity INFO, confidence 0.70
7. Adequate custom content: POSITIVE, severity INFO, confidence 0.85
8. Excellent: POSITIVE, severity INFO, confidence 0.95

## Do NOT Flag

- Draft MRs (is_draft=true): do not produce a finding
- Very descriptive title AND small change (1-3 files): do not penalize missing body
- Trivial tasks (single-line edits, README-only): skip

## Severity

- MAJOR: Empty description on large MR (8+ files)
- MINOR: Empty on small MR, unmodified template, issue-reference-only
- INFO: POSITIVE verdicts only
- Never CRITICAL.

## When in Doubt

Do not report. A false positive on description quality discourages contributors from writing descriptions at all.' WHERE slug = 'mr-description-quality';

-- 2. silent-failure-patterns
UPDATE practice SET criteria = 'Identify silent failure patterns — places where errors are suppressed, ignored, or inadequately handled, leaving users without feedback when something goes wrong.

## Detection Hierarchy

Apply these rules strictly in order. Use the MOST SEVERE category that applies.

### Tier 1 — Flag as NEGATIVE, severity MAJOR

**1a. Empty catch blocks (confidence: 0.92+)**
A catch block with no statements, or only a comment, after a do block containing a meaningful operation.
```swift
do {
    try modelContext.save()
} catch {
    // nothing here
}
```

**1b. catch-only-print on user-facing operations (confidence: 0.88+)**
A catch block that only calls print() and/or returns, with no UI state update.
```swift
} catch {
    print("Save failed: \(error)")
    return false
}
```

**1c. try? on modelContext.save() (confidence: 0.90+)**
Using try? on save discards whether user data was actually persisted.
```swift
context.insert(newItem)
try? context.save()
```

**1d. try? on network/fetch calls discarding the result without fallback UI (confidence: 0.85+)**
Using try? await on a network call where the result is the primary purpose of the view, AND there is no visible fallback/error state.

### Tier 2 — Flag as NEGATIVE, severity MINOR

**2a. catch block that logs but does not update UI state (confidence: 0.80+)**
A catch using Logger/os_log but not setting any user-visible state.

**2b. try? on URLSession.data inside guard-let with silent empty-return (confidence: 0.78+)**
If the else branch contains a Logger.error call, downgrade to INFO.

### Tier 3 — Do NOT Flag

- try? on Task.sleep — ALWAYS acceptable
- try? inside Decodable init for optional/defaulted fields with ?? fallback
- catch-with-fatalError on ModelContainer init — handled by fatal-error-crash practice
- catch block that sets errorMessage AND logs — GOOD PATTERN
- catch-rethrow or throws propagation
- try? on modelContext.fetch for read-only queries with ?? fallback
- try? in preview/sample data code
- Test files, preview files, sample data files: skip

## Severity

- MAJOR for Tier 1. MINOR for Tier 2a/2b without logging. INFO for Tier 2b with logging. Never CRITICAL.

## Confidence

- 0.90+ for empty catch and try? on save
- 0.80-0.89 for catch-print-only
- 0.70-0.79 for ambiguous
- Below 0.70: do not report

## When in Doubt

Do not report. Only flag patterns where the user impact is clear: "What does the user experience when this fails?"' WHERE slug = 'silent-failure-patterns';

-- 3. state-ownership-misuse
UPDATE practice SET criteria = 'Analyze the merge request diff for state ownership misuse in SwiftUI views. Core principle: mutable data has ONE owner; all other views access it via a reference.

## SwiftUI State Ownership Rules (iOS 17+)

- **@State**: View creates and owns this data. Should almost always be private. SwiftUI initializes @State storage ONCE — if a parent re-creates the child with a new value, the stored @State value is NOT updated.
- **@Binding**: Parent owns this value; child needs read-write access.
- **@Bindable**: Received @Observable object, needs $-bindings to its properties.
- **@Environment**: Injected into the view hierarchy.
- **Plain let/var**: Just observes an @Observable; no bindings needed.

## Flag (NEGATIVE)

### Pattern A: @State where @Binding is needed — MAJOR
A child view receives a value from its parent and stores it as @State instead of @Binding. Creates a COPY that freezes after first render.
```swift
// Parent: ChildView(username: username)
struct ChildView: View {
    @State var username: String  // BUG: frozen copy
}
```
Fix: `let username: String` (read-only) or `@Binding var username: String` (read-write).
Confidence: 0.85-0.95 if parent-child call visible in diff.

### Pattern B: @Binding where @Bindable should be for @Observable — MINOR
```swift
struct SettingsView: View {
    @Binding var manager: MapManager  // WRONG for @Observable
}
```
Fix: `@Bindable var manager: MapManager`.

### Pattern C: @State for an @Observable the view does NOT create — MAJOR
```swift
struct DetailView: View {
    @State var viewModel: SomeObservableVM  // WRONG if passed from parent
}
```

### Pattern D: Excessive @Binding drilling instead of @Bindable — MINOR/INFO
5+ individual @Binding parameters from a single @Observable object.

### Pattern E: @State not private — INFO
```swift
@State var isVisible = false  // Should be @State private var
```

## Do NOT Flag

1. `@State private var vm = MyViewModel()` — View creates and owns an @Observable. CORRECT.
2. `@State private var showSheet = false` — Local UI state. CORRECT.
3. `@Bindable var vm: MyObservableVM` — Received @Observable, needs bindings. CORRECT.
4. `var manager: MapManager` (no wrapper) — Read-only observation. CORRECT.
5. `@Environment(ThemeManager.self) private var themeManager` — CORRECT.
6. `@Binding var searchText: String` in child mutating parent''s @State String. CORRECT.
7. `@State private var viewModel: X` with init creating it via `State(initialValue:)`. CORRECT when the view creates the instance.
8. `@Previewable @State var x` in #Preview blocks — NEVER FLAG.
9. @State with a default value that looks like local UI state — CORRECT.

## Cross-File Analysis

If only the child is in the diff:
- No default value, not private → medium confidence (0.65-0.84)
- Has default value, looks like local UI state → likely acceptable, do not flag
- Creates a new @Observable instance → acceptable

NEVER report with confidence above 0.90 unless you can see the actual parent-child call site.

## Severity

- MAJOR: Pattern A, Pattern C
- MINOR: Pattern B, Pattern D
- INFO: Pattern E
- Never CRITICAL.

## When in Doubt

Do not report. False positives on state ownership are especially harmful because they confuse contributors about the already-complex ownership model.' WHERE slug = 'state-ownership-misuse';

-- 4. accessibility-support
UPDATE practice SET criteria = 'Identify accessibility issues — places where interactive elements or meaningful images are not accessible to VoiceOver users.

## Flag (NEGATIVE)

### 1. Image(systemName:) without accessibility label when NOT inside a labeled container — MINOR
```swift
// BAD: VoiceOver reads "star dot fill"
Image(systemName: "star.fill")
    .foregroundStyle(.yellow)

// BAD: Icon-only button with no accessible name
Button(action: { showSettings = true }) {
    Image(systemName: "gearshape")
}
```
Fix: `.accessibilityLabel("Favorites")` or `Button("Settings", systemImage: "gearshape")`.
Confidence: 0.85-0.92.

### 2. Hardcoded .font(.system(size: N)) instead of semantic styles — MINOR
```swift
// BAD: Does not respect Dynamic Type
Text("Welcome")
    .font(.system(size: 24))
```
Fix: `.font(.title)` or `.title2`.
Confidence: 0.90.

### 3. Custom interactive views without accessibility traits — MINOR
```swift
// BAD: View with onTapGesture but no accessibility role
RoundedRectangle(cornerRadius: 12)
    .overlay(Text("Tap me"))
    .onTapGesture { /* action */ }
```
Fix: Add `.accessibilityAddTraits(.isButton)` and `.accessibilityLabel("...")`.
Confidence: 0.80.

### 4. Color-only information — MINOR
```swift
// BAD: Status conveyed only by color
Circle()
    .fill(isOnline ? .green : .red)
```
Confidence: 0.75.

## Do NOT Flag

1. `Button("Add Item")` — text IS the label
2. `Button("Settings", systemImage: "gearshape")` — text provides label
3. `Label("Favorites", systemImage: "star.fill")` — designed for accessibility
4. `NavigationLink("Profile")` — auto-accessible
5. `Toggle("Dark Mode", isOn:)` — auto-accessible
6. `Text("Hello")` — inherently accessible
7. Image(systemName:) INSIDE a Button that has a Text label — Button provides context
8. TabView with Label items — auto-accessible
9. .accessibilityLabel on a parent container covers child elements
10. Decorative images (backgrounds, patterns) without `.accessibilityHidden(true)` — downgrade to INFO at most

## Severity

- MINOR: Interactive elements missing labels, hardcoded font sizes, color-only info
- INFO: Decorative image issues
- Never MAJOR or CRITICAL.

## When in Doubt

Do not report. Only flag when the accessibility impact is clear and the fix is obvious. Check the CONTAINER before flagging an Image — if it''s inside a labeled Button, it''s already accessible.' WHERE slug = 'accessibility-support';

-- 5. fatal-error-crash
UPDATE practice SET criteria = 'Find every crash-risk pattern that would terminate the app with a fatal error in production.

## What to Detect

### Category A: Explicit termination calls
- `fatalError(` — unconditionally terminates the process
- `preconditionFailure(` — unconditionally terminates the process

### Category B: Force unwrapping (implicit fatal errors)
- `try!` (force try) — crashes if the expression throws
- Force unwrap on optionals (`someVar!`, `someDict[key]!`, `someArray.first!`) — crashes with "Unexpectedly found nil"
- Force downcast (`as! SomeType`) — crashes if the type doesn''t match

**Category B patterns are the MOST COMMON crash vectors in Swift.** They produce the same fatal runtime error as fatalError() but are easier to overlook.

### How to identify force unwraps in the diff
Look for `!` used as a postfix operator:
- `variable!` — unwrapping an optional variable
- `dictionary[key]!` — force unwrapping dictionary lookup (always Optional)
- `array.first!` / `array.last!` — force unwrapping Optional return values
- `try! someThrowingCall()` — force try
- `value as! ConcreteType` — force downcast
- `URL(string: "https://example.com")!` — even with a valid literal, this is a crash pattern because it teaches force-unwrapping as a habit

Do NOT confuse with:
- `!=` (not-equal operator)
- `Type!` in type annotations (implicitly unwrapped optional declarations)
- Optional chaining `?`
- `if let` / `guard let` unwrapping (CORRECT handling)

## Do NOT Flag (check BEFORE flagging)

1. `assert()` and `assertionFailure()` — debug-only, stripped from release builds
2. `#Preview` blocks — preview-only code
3. `#if DEBUG` blocks — debug-only
4. XCTest files — any file importing XCTest
5. `required init?(coder:)` — compiler-imposed, genuinely unreachable in SwiftUI
6. `@unknown default` — Swift-recommended pattern for future enum cases
7. SampleData classes used only for previews with `isStoredInMemoryOnly: true`
8. Force unwrap immediately after a nil check: `if dict[key] != nil { use(dict[key]!) }` — safe (flag as INFO at most)
9. Implicitly unwrapped optionals in @IBOutlet — UIKit convention

## Severity

### CRITICAL
- fatalError/try!/force unwrap in a ViewModel, Service, or Manager method that runs during normal user interaction

### MAJOR
- fatalError in the @main App struct''s ModelContainer initialization (the Xcode template pattern)
- fatalError/try!/force unwrap in any initializer of a type used in the main app flow
- Force unwrap on network response data (`response.data!`, `json["key"]!`)
- try! on any I/O operation (file read, network call, JSON decoding)

### MINOR
- fatalError in a Secrets/plist loader (crashes only if configuration is missing)
- fatalError/try! in a default: branch of a switch on a type the developer fully controls
- `URL(string: "https://example.com")!` with a hardcoded valid URL — crash pattern even though the URL is valid

### INFO
- Force unwrap immediately after a nil check (safe but not idiomatic)

## Confidence

- ModelContainer catch block: 0.95
- try! on I/O operations: 0.93
- Force unwrap on network/API data: 0.92
- Bare force unwrap on optional properties: 0.90
- Secrets/plist loader: 0.95
- ViewModel/Service method: 0.97
- switch default with all cases listed: 0.85

## Guidance

For force unwraps: show the `guard let` or `if let` or `??` alternative.
For try!: show the do/catch pattern.
For ModelContainer fatalError (Xcode template): explain it comes from Apple''s default template, provide the replacement with ContentUnavailableView.
For Secrets/plist fatalError: suggest Optional return or throwing function.

## When in Doubt

Flag it. Crash-risk patterns are the ONE practice where over-reporting is acceptable. A missed crash bug is far worse than a false positive.' WHERE slug = 'fatal-error-crash';

-- 6. view-logic-separation
UPDATE practice SET criteria = 'Detect whether SwiftUI View structs contain business logic that should be extracted into separate types.

## Definitions

BUSINESS LOGIC (should NOT be in a View):
- Networking: URLSession calls, URL construction, HTTP response validation
- Encoding/Decoding: JSONDecoder().decode(...), JSONEncoder().encode(...)
- Complex algorithms: scoring systems, multi-step data pipelines, >10 lines of branching business rules
- Domain computations: price/balance calculations, statistical aggregations
- Multi-step persistence orchestration: 15+ lines of modelContext operations with business-rule branching

VIEW LOGIC (ACCEPTABLE in a View):
- @Query declarations and @Environment(\.modelContext) access
- Idiomatic SwiftData mutations: .onDelete { context.delete(items[index]) }, simple button insert/toggle/delete
- Simple computed properties: filtering @Query, counting items, date/string formatting
- Conditional rendering logic (if/else, switch on enum)
- Navigation, sheet/alert presentation
- Calling methods on an injected @Observable view model: `.task { await viewModel.load() }`

## Flag (NEGATIVE)

1. URLSession, URLRequest, HTTPURLResponse, URLComponents used inside a View struct or its private methods — MAJOR, confidence 0.85-0.95
2. JSONDecoder().decode(...) or JSONEncoder().encode(...) called inside a View — MAJOR, confidence 0.85-0.95
3. A private function in a View with >15 lines of non-UI logic with domain-specific branching — MAJOR, confidence 0.70-0.85
4. Persistence orchestration beyond simple CRUD (15+ lines with state reconciliation) — MINOR, confidence 0.60-0.75

## Do NOT Flag

- `.onDelete { indexSet in for i in indexSet { context.delete(items[i]) } }` — idiomatic SwiftData
- Simple addItem() that creates one model, inserts it, optionally saves
- Computed property like `var filteredItems: [Item] { items.filter { $0.isActive } }` — simple filter
- `await viewModel.fetchData()` in .task — this is the CORRECT pattern
- @Query + conditional rendering based on query results
- modelContext single delete/insert in a button action
- Files that are not SwiftUI Views (models, services, view models, app entry points)
- Files with only #Preview

## Severity

- MAJOR: networking/algorithms in View
- MINOR: moderate logic
- INFO: suggestion
- Never CRITICAL.

## When in Doubt

Do not report. Only flag when business logic is clearly in a View struct. A View with a single simple API call delegated through a service is borderline — lean POSITIVE.' WHERE slug = 'view-logic-separation';

-- 7. commit-discipline
UPDATE practice SET criteria = 'Evaluate whether the MR demonstrates reasonable commit discipline. This practice has a LENIENT threshold — only flag truly egregious problems.

## Acceptable — Do NOT Flag

- "Add logging" — short but specific enough
- "Implement the EventListView" — clear intent
- "#19: Feedback on user selection and game logic" — excellent
- "Resolve Day 1: Issue 2" — references task
- "33-adjust-views-to-conform-to-apple-s-hig" — auto-generated from branch, acceptable
- "Code Refactor" — vague but tells nature of change
- "Settings View" — identifies what was added
- Any MR title >= 3 words that describes WHAT was changed
- Any MR title that references an issue number
- MRs touching only 1-3 files
- README-only MRs

## Flag (NEGATIVE, severity always MINOR)

1. **Empty or meaningless title**: ".", "asdf", single generic verb alone ("fix", "update", "changes", "stuff", "wip")
2. **Truly generic multi-word**: "further improvements", "small fixes", "some changes" — vague adjective + vague noun, zero specificity
3. **Kitchen-sink MR**: 20+ files across 4+ unrelated concerns AND a vague title. A 20-file MR with a clear title is FINE.

## Confidence

- 0.85-0.95 for clear negatives (empty, meaningless)
- 0.7-0.85 for borderline
- 0.8-0.95 for positives

## Boundary with mr-description-quality

mr-description-quality evaluates the description body. This practice evaluates: (1) egregious title problems, (2) kitchen-sink MR scope. If both would flag the same MR title, let mr-description-quality handle it — this practice focuses on scope/structure.

## Expected Distribution

~50% no finding, ~40% POSITIVE, ~10% NEGATIVE. If giving NEGATIVE >15% of the time, threshold is too strict.

## When in Doubt

Do not report. This practice should be rarely triggered. A false positive on commit discipline is demoralizing and rarely actionable.' WHERE slug = 'commit-discipline';

-- 8. meaningful-naming
UPDATE practice SET criteria = 'Identify naming issues in Swift/SwiftUI code where identifiers do not clearly communicate their purpose.

## Flag (NEGATIVE, severity always MINOR)

### 1. Single-letter names outside trivially small scope
```swift
// BAD: single-letter for domain object used across multiple lines
let c = ColorModel(hex: color.toHex() ?? "#FFF")
let g = Garment(name: name, color: c, material: material, ...)

// BAD: single-letter in optional binding
if let c = partial.company { Text(c) }
// Fix: if let company = partial.company { Text(company) }

// BAD: abbreviation passed between views
@State private var tm = TimerManager()
// Fix: @State private var timerManager = TimerManager()
```
Confidence: 0.85-0.95.

### 2. Generic names in non-generic contexts
```swift
struct WeatherAttributeView: View {
    let data: String  // displays "10C", "85%"
// Fix: let formattedValue: String
```
Confidence: 0.70-0.84.

### 3. Swift convention violations
```swift
case celebrate_winner  // Fix: celebrateWinner
let created_at: String  // Fix: Use CodingKeys, rename to createdAt
```
Confidence: 0.90+.

### 4. Misleading or ambiguous names
```swift
var dirty: Bool  // Fix: isDirty or hasUnsavedChanges
func recommendation() -> Recommendation  // Fix: generateRecommendation()
```

## Do NOT Flag

1. Loop indices: i, j, k, n, idx
2. $0, $1 — always acceptable
3. Single-line ForEach/map closures: `ForEach(MaterialType.allCases) { m in Text(m.rawValue).tag(m) }`
4. Standard abbreviations: id, url, db, api, lhs, rhs, r/g/b in color code, vc in UIKit
5. `vm` in short if-let unwraps (2-3 lines)
6. `item` in ForEach with well-named typed collection
7. `result` in TaskGroup/async context
8. `data` and `response` from URLSession.shared.data(from:) — standard pattern
9. Decoder containers: `let c = try decoder.container(...)` — idiomatic Codable
10. Short names in string formatting one-liners

## Rules

- Every NEGATIVE finding MUST include a specific alternative name. If you cannot suggest a clearly better name, do not flag.
- At most 3-4 naming issues per file. Group related issues into one finding.
- Only analyze CHANGED lines in the diff.
- Confidence below 0.50: do not report.

## When in Doubt

Do not report. Naming is subjective. Only flag when the name is clearly harmful to readability and you can suggest a specific, obviously better alternative.' WHERE slug = 'meaningful-naming';

-- 9. view-decomposition
UPDATE practice SET criteria = 'Identify SwiftUI views that have grown too large and would benefit from decomposition into smaller subviews.

## What to Measure

For each View struct in the diff:

1. **body line count** (exclude blank lines and comments):
   - >60 lines: potential concern — examine complexity
   - >100 lines: clear problem — almost always decomposable
   - >200 lines: severe — flag with high confidence

2. **@State property count**:
   - >8: smell — the view likely manages too many concerns
   - >12: clear problem

3. **Nesting depth** (VStack { HStack { ForEach { if { ... } } } }):
   - >3 levels: readability concern

4. **Number of distinct concerns**: Does the body contain multiple logically independent sections?

## Flag (NEGATIVE)

Large body with multiple concerns:
- >100 lines body with 3+ concerns: MAJOR, confidence 0.85-0.95
- 60-100 lines body or >8 @State: MINOR, confidence 0.70-0.85

KEY RULE: Suggest SPECIFIC extraction points. Do NOT just say "this view is too long." Identify the sections and suggest names: "Lines 15-45 form a header — consider extracting to RecipeHeaderView."

## Do NOT Flag

```swift
// OK: 200-line FILE but body is short — logic extracted into child structs
struct RecipeDetailView: View {
    var body: some View {
        ScrollView {
            RecipeHeaderView(recipe: recipe)
            IngredientsSectionView(ingredients: recipe.ingredients)
        }
    }
}
```

```swift
// OK: Long List/Form with simple repeated rows
var body: some View {
    Form {
        Section("General") { TextField(...) TextField(...) Toggle(...) }
        Section("Appearance") { Picker(...) Picker(...) }
        // 20+ simple form fields — low complexity despite length
    }
}
```

- Files that are not SwiftUI Views
- Small views (<60 lines body)
- Files with only #Preview

## Severity

- MAJOR: >100 lines body with 3+ concerns
- MINOR: 60-100 lines or >8 @State
- INFO: suggestion
- Never CRITICAL.

## When in Doubt

Do not report. Only flag when the view is clearly too large AND you can identify specific extraction points. A long but simple Form is not a decomposition problem.' WHERE slug = 'view-decomposition';

-- 10. preview-quality
UPDATE practice SET criteria = 'Evaluate the quality of #Preview blocks in changed SwiftUI View files.

## What to Analyze

For each changed .swift file that defines a SwiftUI View (`var body: some View`), examine #Preview blocks.

### Simple View (one preview sufficient):
- Takes only primitive/simple parameters
- No conditional rendering
- No @State/@Binding driving different visual states

### Complex View (multiple previews valuable):
- Body contains if/else, switch showing different UI structures
- Uses @Query, @State, @Binding affecting visible content
- Has loading, error, empty, or populated states

## Flag (NEGATIVE, severity always MINOR)

Complex/stateful views missing previews entirely:
```swift
// View has loading, error, and data states but no preview at all
struct RecipeListView: View {
    @State private var isLoading = true
    @State private var errorMessage: String?
    // complex body with 3 states...
}
// No #Preview block
```

Complex views with only a basic single preview despite multiple visual states:
```swift
// Only this, despite having loading/error/success states:
#Preview {
    RecipeListView()
}
```
Confidence: 0.85-0.95.

## Do NOT Flag

- App entry point files (@main)
- Non-View files (models, services, utilities)
- Files not changed in this MR
- Simple views where one preview is sufficient
- Dark mode previews (nice-to-have, not required)

## Positive Findings

When you see good preview coverage (multiple named previews matching view complexity):
```swift
#Preview("With Data") {
    RecipeListView()
        .modelContainer(SampleData.shared.modelContainer)
}
#Preview("Empty State") {
    RecipeListView()
        .modelContainer(for: Recipe.self, inMemory: true)
}
```
Give POSITIVE verdict.

## When in Doubt

Do not report. Preview quality is the lowest-priority practice. Only flag when a complex view with clear multiple states has zero or clearly inadequate previews.' WHERE slug = 'preview-quality';

-- 11. error-state-handling
UPDATE practice SET criteria = 'Evaluate whether views fetching REMOTE/NETWORK data properly implement all three UI states: loading, error, and success.

## Scope

ONLY analyze views that perform network or remote data fetching. Indicators:
- URLSession.shared.data calls
- Calls to service/fetcher classes that make HTTP requests
- Async functions like fetchData(), loadData() contacting remote servers
- AI/LLM API calls

DO NOT analyze:
- SwiftData @Query — synchronous from view''s perspective
- UserDefaults / @AppStorage
- Local file reads
- Timer-based operations without network

## What to Check

### 1. LOADING STATE
Look for: ProgressView(), isLoading driving UI, .redacted(reason: .placeholder)

NEGATIVE example — no loading indicator:
```swift
struct PopularView: View {
    @State private var articles: [Article] = []
    var body: some View {
        ScrollView { ForEach(articles) { a in ArticleCard(article: a) } }
        .task { let fetched = await news.topHeadlines(); self.articles = fetched }
    }
}
// User sees empty content during fetch
```

POSITIVE example:
```swift
if fetcher.questions.isEmpty {
    ProgressView("Loading...").task { try await fetcher.fetchData() }
} else {
    TriviaQuestionView(trivia: fetcher.questions[questionIndex])
}
```

### 2. ERROR STATE
Look for: .alert with error, Text displaying errorMessage, ContentUnavailableView, retry button

NEGATIVE example:
```swift
.task {
    do { try await fetcher.fetchData() }
    catch { logger.error("Failed: \(error)") }
    // No UI update
}
```

POSITIVE example:
```swift
if let error = newsFetcher.errorMessage {
    VStack {
        Image(systemName: "exclamationmark.triangle")
        Text(error)
        Button("Retry") { Task { await newsFetcher.fetchNewsFeed() } }
    }
}
```

## Relationship to silent-failure-patterns

This practice evaluates the USER-FACING UI layer. silent-failure-patterns evaluates the CODE/DEVELOPER layer. They can overlap but evaluate different dimensions.

## Severity

- MAJOR: Both loading AND error display missing
- MINOR: One of loading or error missing
- Never CRITICAL.

## Confidence

- >= 0.85: Clearly see .task with network call and NO ProgressView/error display
- >= 0.80: Loading + error clearly present
- 0.5-0.7: Fetch delegated to ViewModel, can''t see full chain

## When in Doubt

Do not report. If the view delegates fetching to a ViewModel and you cannot see the full chain in the diff, lean POSITIVE.' WHERE slug = 'error-state-handling';

-- 12. code-hygiene
UPDATE practice SET criteria = 'Identify committed development debris in the merge request diff.

## Critical Boundary

This practice is ONLY about development debris. It is NOT about:
- Error handling quality -> silent-failure-patterns
- print() inside catch blocks -> silent-failure-patterns
- Code architecture, naming, design -> other practices

## Flag (NEGATIVE)

### 1. Commented-Out Code Blocks (2+ consecutive lines) — MINOR
```swift
// BAD: old lines left above replacements
//@AppStorage("focusMin") var focusMin: Int = 30
//@AppStorage("shortBreakMin") var shortBreakMin: Int = 5
var focusMin: Int = 30
```
Confidence: 0.90+.

### 2. Debug print() in Normal Code Flow — MINOR
CRITICAL: If print() is inside a catch block or logs an error variable — SKIP IT (silent-failure-patterns handles that).
```swift
func companyNames() -> [String] {
    var result: [String] = []
    print(result)  // debug trace
    return result
}
```
Confidence: 0.80-0.89.

### 3. Buttons/Handlers Where print() Is the ONLY Action — MAJOR
This is the strongest signal. Button does nothing visible to the user.
```swift
Button("Forgot password?") { print("Forgot password tapped") }
Button("Sign in") { print("Sign in tapped") }
```
NOTE: If button has print() AND real logic, that is pattern #2 (MINOR), not pattern #3.
Confidence: 0.90+.

### 4. Xcode Placeholder Tokens — MAJOR
```swift
.font(/*@START_MENU_TOKEN@*/.title/*@END_MENU_TOKEN@*/)
```
Confidence: 0.95+.

### 5. Stale File Header Comments — INFO
Header says different filename than actual file.

### 6. Garbled/Corrupted Comments — INFO

## Do NOT Flag

1. Explanatory comments clarifying intent
2. // MARK: section dividers
3. /// documentation comments
4. Standard Xcode file headers (when filename matches)
5. // TODO: and // FIXME: for planned work
6. Code inside #if DEBUG blocks
7. Logger / os.Logger usage — proper structured logging, NOT debris
8. Single inline comment explaining a non-obvious line
9. print() in catch blocks — belongs to silent-failure-patterns

## Severity

- MAJOR: Print-only buttons (non-functional UI), Xcode placeholder tokens
- MINOR: Debug print alongside real logic, commented-out code blocks
- INFO: Stale file headers, garbled comments
- Never CRITICAL.

## When in Doubt

Do not report. Only flag when the debris is clearly unintentional and the fix is deletion.' WHERE slug = 'code-hygiene';

-- 13. hardcoded-secrets
UPDATE practice SET criteria = 'Detect API keys, tokens, passwords, or private keys in source code string literals.

## What to Detect

Look for:
- Prefixed tokens: sk-, ghp-, glpat-, AIzaSy, AKIA, xox-
- Base64-encoded credentials (long alphanumeric strings in auth contexts)
- URLs with embedded tokens (?token=..., ?api_key=...)
- Secrets in enum/struct constants
- Passwords in string literals

## Do NOT Flag

- Placeholder strings: YOUR_API_KEY_HERE, <token>, "INSERT_KEY_HERE"
- Empty strings
- Info.plist key references populated at build time
- OAuth client IDs that are intentionally public
- Test/mock values clearly marked as such (e.g., "test-api-key", mock data)
- Bundle identifiers or app IDs

## Severity

Always CRITICAL. Always confidence 0.95+.

## Guidance

The fix is DELETE the secret line. Never show commented-out secrets. Direct contributor to .xcconfig, .plist, Keychain, or environment variables. Explain that secrets in source code are visible in git history even after removal — the credential must be rotated.' WHERE slug = 'hardcoded-secrets';
