# Accessibility Support
**Category:** UI Quality

**Scope:** Interactive elements and meaningful images must be usable by VoiceOver and respect Dynamic Type. Decorative elements must not pollute the accessibility tree.

## Positive Signals
- `.accessibilityLabel` on icon-only buttons or standalone `Image(systemName:)`
- Semantic font styles (`.font(.title)`, `.headline`) instead of hardcoded point sizes
- `.accessibilityHidden(true)` on decorative/background images
- `.accessibilityAddTraits(.isButton)` on custom views using `onTapGesture`
- `Button("Label", systemImage:)` or `Label("text", systemImage:)` patterns

## Negative Signals
- `Image(systemName:)` with NO `.accessibilityLabel` AND NOT inside a labeled container (Button with Text, Label, NavigationLink)
- Icon-only `Button { Image(systemName:) }` with no text sibling and no `.accessibilityLabel`
- `.font(.system(size: N))` with hardcoded point sizes — breaks Dynamic Type scaling
- `onTapGesture` on a non-Button view without `.accessibilityAddTraits(.isButton)` — invisible to VoiceOver as interactive
- Decorative/background images without `.accessibilityHidden(true)`
- Color as sole information carrier (red/green with no text/icon alternative)

## Exclusions — Do NOT Flag
These are already accessible:
- `Button("Text")` or `Button("Text", systemImage:)` — text IS the label
- `Label("Text", systemImage:)` — designed for accessibility
- `NavigationLink("Text")`, `Toggle("Text", isOn:)`, `Text(...)` — auto-accessible
- `Image(systemName:)` inside a Button/HStack containing a `Text` sibling
- `.accessibilityLabel` on a parent container (covers children)
- `TabView` with `Label` items

## Severity
- **CRITICAL**: Never
- **MAJOR**: Never
- **MINOR**: All findings — missing labels, hardcoded font sizes, missing traits, color-only information
