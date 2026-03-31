# Accessibility Support
**Category:** UI Quality

## What This Practice Means
Interactive elements and meaningful images must be usable by VoiceOver and respect Dynamic Type. Decorative elements must not pollute the accessibility tree.

## Positive Signals (-> verdict POSITIVE)
- `.accessibilityLabel` on icon-only buttons or standalone `Image(systemName:)`
- Semantic font styles (`.font(.title)`, `.headline`) used instead of hardcoded sizes
- `.accessibilityHidden(true)` or `Image(decorative:)` on decorative/background images
- `.accessibilityAddTraits(.isButton)` on custom interactive views using `onTapGesture`
- `Button("Label", systemImage:)` or `Label("text", systemImage:)` patterns

## Negative Signals (-> verdict NEGATIVE)
- `Image(systemName:)` with NO `.accessibilityLabel` AND NOT inside a labeled container (Button with Text, Label, NavigationLink)
- Icon-only `Button { Image(systemName:) }` with no text sibling and no `.accessibilityLabel`
- `.font(.system(size: N))` hardcoded point sizes — breaks Dynamic Type
- `onTapGesture` on a non-Button view without `.accessibilityAddTraits(.isButton)`
- Decorative/background images without `.accessibilityHidden(true)`
- Color as sole information carrier (e.g., red/green circle with no text/icon alternative)

### Critical False-Positive Exclusions
Do NOT flag any of these — they are already accessible:
- `Button("Text")` or `Button("Text", systemImage:)` — text IS the label
- `Label("Text", systemImage:)` — designed for accessibility
- `NavigationLink("Text")`, `Toggle("Text", isOn:)`, `Text("...")` — auto-accessible
- `Image(systemName:)` inside a Button/HStack that contains a `Text` sibling
- `.accessibilityLabel` on a parent container covers all children
- `TabView` with `Label` items

## Severity Guide
- CRITICAL: Never used for this practice
- MAJOR: Never used for this practice
- MINOR: Icon-only buttons/images missing labels, hardcoded font sizes, missing accessibility traits, color-only information
