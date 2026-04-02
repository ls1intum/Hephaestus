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

## Guidance Rule
When suggesting additional `#Preview` blocks, each MUST seed DIFFERENT state. If the view's state is injectable (via init parameters, `@Previewable @State`, or model property assignment), show the injection code. If the state is private and cannot be injected from outside the view, do NOT show identical constructor calls with different labels — instead, suggest the student add a preview-only initializer or use an internal enum to switch preview state. Show the initializer signature, not fake identical previews.

## Severity
- **CRITICAL**: Never
- **MAJOR**: Never
- **MINOR**: Complex view missing previews entirely, or single basic preview despite multiple visual states
