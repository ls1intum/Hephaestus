## How to do it

- Cover the presentation of each new SwiftUI view with a `#Preview` or a `PreviewProvider` in the same change. A preview of a host view counts when it shows that presentation.
- When the view shows variable data, supply known values that match what it shows. For example, instead of `TaskList(tasks: [])`, pass tasks with varied titles.
- Take the sample values from the preview, a sample helper, or fixed values that the view or its owner holds. A live fetch does not give sample data.
- Preview each empty, loading or error state that the new presentation supports.
- Some new views only start work and hold the model, and pass all that they show to a content view. Preview what the outer view draws itself, such as a navigation title, a toolbar or an overlay. Previews of the content view cover the rest.

## When it does not apply

- Your change adds no new SwiftUI view. It declares no new `struct … : View` outside test files.
- Your change only edits a view that already exists.
- Your change only touches models or other code that is not a view.
- Your change only adds or edits previews.

It still applies when the new view is `private` or used only inside one file.

## Common mistakes

- Using only an empty list or a placeholder to preview a view that shows variable data. The preview exists, but it shows nothing that you can check. Instead, give it varied values that look like real data.
- Relying on a fresh or live view model, or on a stored value such as `@AppStorage`, for the data. Nobody knows what the preview then shows. Instead, give the preview known sample values.
- Adding a new row or section to a screen and counting on the screen’s preview. That preview covers the new view only when the screen really draws it with the preview’s data. Instead, check that it does, or preview the new view on its own.

## Sources

- [Adding previews to your interface files](https://developer.apple.com/documentation/xcode/adding-previews-to-your-interface-files), Apple Developer Documentation. A preview shows a view without running the app, and it can use sample data kept apart from the app.
- [Build programmatic UI with Xcode Previews](https://developer.apple.com/videos/play/wwdc2023/10252/), Kevin Cathey, Apple, WWDC 2023. A preview sets up data and passes it into the view, and it runs the real code of your project.
- [What to look for in a code review](https://google.github.io/eng-practices/review/reviewer/looking-for.html), Google Engineering Practices. A user interface change is hard to judge from its code alone, so a reviewer can ask to see it.
