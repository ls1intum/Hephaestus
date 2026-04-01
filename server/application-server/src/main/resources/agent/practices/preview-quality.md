# Preview Quality
**Category:** SwiftUI

**Scope:** SwiftUI `#Preview` blocks should match view complexity. Simple views need one preview. Stateful or multi-branch views need multiple named previews covering each visual state.

## Positive Signals
- Simple view (no conditionals, no branching): one `#Preview` is sufficient
- Complex view (if/else, switch, loading/error/empty/populated states): multiple `#Preview` blocks with descriptive labels: `#Preview("Empty State") { ... }`
- `@Previewable @State` used for `@Binding` parameters
- In-memory `ModelContainer` with sample data for SwiftData views
- Realistic mock data, not empty defaults

## Negative Signals
- Complex/stateful view with zero `#Preview` blocks
- View has loading, error, and data states but only a single unnamed `#Preview { ViewName() }`
- Missing previews for key visual branches visible in the diff
- `#Preview` with no data or empty initializers when the view requires model data to render meaningfully

## Exclusions — Do NOT Flag
- `@main` app entry point files
- Non-View files (models, services, utilities)
- Files not changed in this MR
- Dark mode previews: nice-to-have, never required
- Simple views where one preview genuinely covers all rendering paths
- Landscape/device-variant previews: never required

## Severity
- **CRITICAL**: Never
- **MAJOR**: Never
- **MINOR**: Complex view missing previews entirely, or single basic preview despite multiple visual states
