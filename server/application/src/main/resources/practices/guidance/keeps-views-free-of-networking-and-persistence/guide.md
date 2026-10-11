## How to do it

- Put I/O in a model, a store or a service. I/O means reading or writing data, such as network requests, decoding, and file or `UserDefaults` access. The view calls a method or passes a binding. A binding lets the view read and change data that another type owns.
- Start the work from a modifier by calling the model. For example, instead of calling `URLSession` in a view’s `.task`, write `.task { await store.load() }` and keep the request in the store.
- In a button action, call a method of the model, such as `viewModel.save(item)`. The action does not encode the item or write the file.
- Hand the view the state it shows, such as the loaded items. Then a preview or a test can pass in sample state, with no network and no disk.
- Use the framework’s own view-side storage in the view. It is `@Query`, `@FetchRequest`, `modelContext.insert` and `delete` through `@Environment(\.modelContext)`, `@AppStorage` and `@SceneStorage`.

## When it does not apply

- Your change adds no line inside a SwiftUI view type: a `struct` that conforms to `View`, or a computed property that returns `some View`.
- Your change touches only models, services, tests or files that are not Swift.

It still applies when the lines you add to a view do no I/O.

## Common mistakes

- Looking for one architecture, such as a view model for each screen. The practice asks only where the work runs. Instead, check that any request or write runs outside the view, in whatever model, store or service you already have.
- Moving `@Query`, `@FetchRequest`, `@AppStorage` or `@SceneStorage` out of a view to follow this practice. They are the framework’s own way for a view to read and store data. Instead, keep them in the view.
- Moving the request into a private method of the view, such as `fetchItems()`. The method is still part of the view. Instead, move it to the model and call it from the view.
- Reading `UserDefaults` or calling `FileManager` in a view for one value. Instead, use `@AppStorage` for a setting, or let the model read the value.

## Sources

- [Managing model data in your app](https://developer.apple.com/documentation/swiftui/managing-model-data-in-your-app), Apple. A data model keeps the data apart from the views that show it, which makes the app easier to test.
- [Data Essentials in SwiftUI](https://developer.apple.com/videos/play/wwdc2020/10040/), Apple, 2020. A separate model handles storage, syncing and side effects, while a view’s `body` only describes the screen.
- [SwiftData](https://developer.apple.com/documentation/swiftdata), Apple. SwiftData provides `Query` for stored models, and a view reaches the model context through its environment.
