## How to do it

- Start the work that a screen needs in `.task` on its view. SwiftUI cancels that task when the view goes away. For example, instead of `.onAppear { Task { await model.load() } }`, write `.task { await model.load() }`.
- Match the lifetime of a button-started task to its operation. A `Task { }` in the button action is a valid way to start it. Work that must stop with the screen needs a way to be canceled. A save that the user asked for can finish after the screen closes.
- Keep changes to view state on the main actor. The model can already have this protection by inheritance or through the target’s default isolation. Otherwise, mark the model `@MainActor` or use `await MainActor.run`.
- Use `Task.detached` or a `DispatchQueue` only when the work must run apart from its caller. Otherwise, use `async let` or a task group, so cancellation reaches the work.
- If your code needs the result of a task, await the task’s value and use it.

## When it does not apply

- Your change adds no concurrency code to Swift files. That means no new `async`, `await`, `Task`, `.task`, `@MainActor`, `MainActor.run`, `Task.detached`, `DispatchQueue` or actor declaration. Concurrency code that your change does not touch does not count.

## Common mistakes

- Treating every `Task { }` as a bug. A task in a button action is the usual way to start async work. Not every task needs a stored handle or a comment. Instead, check that the lifetime of each task fits its work.
- Setting a `@Published` property from a background queue or a detached task. SwiftUI can miss the change, and the screen shows old data. Instead, keep the model on the main actor and `await` the slow work.
- Moving work into `Task.detached` to keep it off the main thread. The detached task drops its actor and the cancellation of its caller. Instead, `await` the slow call. The main actor stays free while it waits.

## Sources

- [Concurrency](https://docs.swift.org/swift-book/documentation/the-swift-programming-language/concurrency/), The Swift Programming Language, Swift project. Child tasks inherit cancellation from their parent, while you manage each unstructured task yourself.
- [`task(name:priority:file:line:_:)`](https://developer.apple.com/documentation/swiftui/view/task(name:priority:file:line:_:)), SwiftUI documentation, Apple. SwiftUI cancels a view’s task if the view goes away before the task finishes.
- [Discover concurrency in SwiftUI](https://developer.apple.com/videos/play/wwdc2021/10019/), Apple, WWDC 2021. Use `.task` to tie work to a view, and change an observable object only on the main actor.
- [`Task.detached`](https://developer.apple.com/documentation/swift/task/detached(name:priority:operation:)-795w1), Swift standard library documentation, Apple. Use a detached task only when child tasks cannot do the work, and handle its cancellation yourself.
