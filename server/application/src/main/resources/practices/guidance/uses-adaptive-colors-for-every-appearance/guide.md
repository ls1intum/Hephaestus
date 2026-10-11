## How to do it

- Color text with a semantic style such as `.primary` or `.secondary`. For example, instead of `.foregroundStyle(.black)`, write `.foregroundStyle(.primary)`.
- Give a screen or a card a system background such as `Color(.systemBackground)`, or a material such as `.regularMaterial`.
- For a highlight, use the accent color with `.tint`, or a standard SwiftUI color such as `.green`. SwiftUI picks its value for the current appearance.
- Use a named asset color whose color set defines both light and dark values.
- Keep a fixed color only where the color is part of the content. One example is text on a fill that the same view sets. A brand color that you keep the same on purpose is another. So is a shadow or a see-through layer over an image.

## When it does not apply

- Your change sets no color in a SwiftUI view. It adds no `.foregroundStyle`, `.foregroundColor`, `.background`, `.fill`, `.tint` or `.shadow(color:)`, and no `Color` or `UIColor` inside a view.

It still applies when every color you add already adapts.

## Common mistakes

- Judging a color by its name. `.green` passed to `foregroundStyle` is SwiftUI’s standard green, and it adapts. `Color(.green)` converts UIKit’s fixed `UIColor.green`, and it does not. Instead, check the type and, for a name that your app defines, its definition or color set. `UIColor.systemGreen` is the adaptive UIKit green.
- Using a color set with only one appearance for an interface color that must adapt. `Color("brand")` then shows the same value in light and dark. Instead, add a dark value to the color set.
- Building an interface color from fixed parts, such as `Color(red:green:blue:)` or a hex value, in a shared extension. Every screen that uses it gets one value. Instead, point the extension at an asset color or a system color.
- Adding opacity to fixed white or black, such as `Color.black.opacity(0.7)`, for text on a system background. It still holds one value. Instead, use a semantic style or an adaptive system color.

## Sources

- [Dark Mode](https://developer.apple.com/design/human-interface-guidelines/dark-mode), Apple Human Interface Guidelines. Apple recommends system colors that adapt and custom color assets with values for both appearances.
- [Supporting Dark Mode in your interface](https://developer.apple.com/documentation/uikit/supporting-dark-mode-in-your-interface), Apple Developer Documentation. Semantic colors and asset variants adapt to appearance changes, while colors built from fixed components do not.
- [Color](https://developer.apple.com/documentation/swiftui/color), Apple Developer Documentation for SwiftUI. SwiftUI resolves system and asset colors when it draws, so their values can depend on the appearance.
