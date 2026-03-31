# Preview Quality
**Category:** SwiftUI

## What This Practice Means
SwiftUI `#Preview` blocks should match the complexity of the view -- simple views need one preview, stateful/multi-branch views need multiple named previews covering each visual state.

## Positive Signals (-> verdict POSITIVE)
- Simple view (no conditionals, no state-driven branching): one `#Preview` block is sufficient
- Complex view (if/else, switch, loading/error/empty/populated states): multiple `#Preview` blocks with descriptive labels like `#Preview("Empty State") { ... }`
- `@Previewable @State` used for `@Binding` parameters
- In-memory `ModelContainer` with sample data for SwiftData views
- Realistic mock data (not just empty defaults)

## Negative Signals (-> verdict NEGATIVE)
- Complex/stateful view with zero `#Preview` blocks
- View has loading, error, and data states but only a single unnamed `#Preview { ViewName() }`
- Missing previews for key visual branches visible in the diff

## False-Positive Exclusions
- `@main` app entry point files: skip
- Non-View files (models, services, utilities): skip
- Files not changed in this MR: skip
- Dark mode previews: nice-to-have, never required
- Simple views where one preview genuinely covers all rendering paths

## Severity Guide
- CRITICAL: never
- MAJOR: never
- MINOR: complex view missing previews entirely, or single basic preview despite multiple visual states
