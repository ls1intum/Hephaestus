## How to do it

- Give each control that shows only an icon a label that says what it does. For example, instead of a button that holds only `Image(systemName: "trash")`, add `.accessibilityLabel("Delete")` to it, or use `Label("Delete", systemImage: "trash")`.
- Label an image that carries information that the nearby text does not give.
- Use `.accessibilityHidden(true)` for an image that only decorates, or whose meaning the text already gives.
- Use a text style, such as `.font(.body)` or `.font(.headline)`, for text and for any SF Symbol beside it. SF Symbols are Apple’s built-in icons. A text style grows with the text size that the person chooses.
- Leave room for text to grow. A `.frame(height:)` or `.lineLimit(1)` on body text is a problem only when it cuts the text off at larger sizes.
- When color shows a state, such as red for an error, also show the state with text or an icon.

## When it does not apply

- Your change adds no interface code to a SwiftUI view. It adds no `Image`, `Button`, `Toggle`, `Text` or `Label`. It adds no `.font(` or `.frame(` line, and no state shown by color.

It still applies when every interface line you add already follows this practice.

## Common mistakes

- Treating every `.frame(height:)` as a problem. A fixed height on an image or a shape is fine. Only a height cap on text can clip it at a larger text size.
- Putting `.accessibilityLabel` on a stack around a row of icon buttons. That label does not name the icons inside it. Instead, put a label on each control.
- Sizing an SF Symbol in points, such as `.font(.system(size: 14))`, next to text that scales. The symbol stays small when the text grows. Instead, give it the text style of the text.
- Choosing `.custom(_:fixedSize:)` for a custom font. It is the only custom font call that never grows. Instead, use `.custom(_:size:relativeTo:)` with the text style that the text replaces.

## Sources

- [VoiceOver](https://developer.apple.com/design/human-interface-guidelines/voiceover), Apple Human Interface Guidelines. Give every key control a label, describe meaningful images, and hide images that only decorate.
- [Typography: Supporting Dynamic Type](https://developer.apple.com/design/human-interface-guidelines/typography#Supporting-Dynamic-Type), Apple Human Interface Guidelines. Layouts adapt to every text size, icons grow with the text, and text truncation stays to a minimum.
- [Applying custom fonts to text](https://developer.apple.com/documentation/swiftui/applying-custom-fonts-to-text), Apple SwiftUI documentation. A custom font from `custom(_:size:)` scales with the body style, and `relativeTo` picks the style to scale with.
- [Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility), Apple Human Interface Guidelines. Convey information with more than color alone, and let people enlarge the text.
