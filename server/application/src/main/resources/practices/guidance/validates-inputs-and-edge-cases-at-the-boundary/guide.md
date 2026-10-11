## How to do it

- Find each value in your change that comes from outside your code. Examples are request data, typed text, a decoded file, a configuration value, and a service response.
- Find the lines that index it, slice it, divide by it or add it up. Also find the lines that make it a whole number or pass it to code that expects a shape.
- For each line, ask what its obvious bad case is, such as empty, missing, zero, or out of range.
- Rule out that case before the line runs. Return early, reject the input with a clear error, use a safe default, or catch the failure. For example, instead of dividing by the item count from a response, return early when the count is zero.

## When it does not apply

- Your change is empty.
- Your change only passes outside values on, saves them, or shows them as they are.
- Your change holds only generated or vendored files.
- Your change adds no source code, such as a change to docs, images, or configuration only.
- Your change uses only typed data from your own app, view state, or controls that already limit the value.

It still applies when the value comes from a data file that ships with your app, or from a list that the user fills.

## Common mistakes

- Trying to check every possible value. Instead, find the obvious input that breaks the line, such as an empty list, and rule out that one.
- Checking a smaller case than the line needs. A check for an empty list protects `items[0]`, but not `items[1]`. Instead, check for as many items as the line reads.
- Trusting a parsed number when you divide by it or use it as an index. Parsing only proves its type. Instead, also check that it is not zero or out of range before that line.
- Starting a minimum at the largest possible number and showing it while the list is empty. Instead, show an empty state or a sensible default.

## Sources

- [Input Validation Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html), OWASP. Check each value before use, including the range of a parsed number. Reject bad input with a clear error.
- [CWE-20: Improper Input Validation](https://cwe.mitre.org/data/definitions/20.html), MITRE. Lists sizes, positions and types to check, with an example of a zero size that the code did not reject.
- [CWE-369: Divide By Zero](https://cwe.mitre.org/data/definitions/369.html), MITRE. A division by zero usually comes from a value that the code did not expect.
- [Collection Types](https://docs.swift.org/swift-book/documentation/the-swift-programming-language/collectiontypes/), The Swift Programming Language, the Swift project. An index outside an array causes a runtime error, and an empty array has no valid index.
