## How to do it

- Identify the view that owns each piece of state that your change adds.
- Use `@State private var` for a value that one view owns and changes, or `@AppStorage` for a setting that must persist.
- Create a shared model once, in its owner. Pass it to children as an argument or through the environment.
- When a child changes its parent’s value, give the child a binding. For example, instead of `@State var isOn` in a toggle row, use `@Binding var isOn` and pass `$isOn` from the parent.
- When a value comes from other state, such as a count or a filtered list, make it a computed property.

## When it does not apply

- Your change adds no state to a SwiftUI view, and no wiring that hands state from one view to another. It adds no `@State`, `@Binding`, `@Environment`, `@Bindable`, `@AppStorage`, `@Observable`, `ObservableObject`, `StateObject` or `ObservedObject`, and no `var` that a view changes.
- Your change only alters the layout of a view.

## Common mistakes

- Looking at names or at the file a view is in. Neither decides where state belongs. Instead, look at where each value is declared and how it reaches other views.
- Creating a shared model again in a child, such as `@State private var store = Store()` in a row. The row then has its own store, which can reset or differ from the parent’s. Instead, pass the parent’s store in, or read it with `@Environment`.
- Copying a parent’s value into the child’s own `@State` and editing the copy. The parent never sees the edit. Instead, take a `@Binding`.
- Keeping a count in its own `@State` and updating it by hand. One missed update leaves a wrong number on the screen. Instead, compute the count from the list.

## Sources

- [Managing user interface state](https://developer.apple.com/documentation/swiftui/managing-user-interface-state), Apple. Store shared state in the closest common parent of the views that need it, as one source of truth.
- [State](https://developer.apple.com/documentation/swiftui/state), Apple. Private state in the highest view that needs it reaches editable children through a `Binding`.
- [Managing model data in your app](https://developer.apple.com/documentation/swiftui/managing-model-data-in-your-app), Apple. Create an observable model once with `@State`, then pass it down or add it to the environment.
- [Data flow through SwiftUI](https://developer.apple.com/videos/play/wwdc2019/226/), Apple, WWDC 2019. One source of truth avoids the bugs that come from copies that you keep in sync by hand.
