## How to do it

- Before you ask for a review, name the job of each new or rewritten function in a short phrase. If its job is unclear, look for mixed jobs, deep nesting, or a body that is hard to scan.
- When one body writes out several jobs and mixes them, move each job into its own function. For example, instead of one function that reads, totals and renders a report, write `readRows`, `totalRows` and `renderReport`.
- When the main action sits deep inside nested conditions, handle each special case first and return early.

![With nested checks, the main action sits deep inside the conditions and is buried. With early returns, each special case returns first, so the main action is no longer nested and is easy to find.](figures/early-returns.svg)

## When it does not apply

- Your change adds or rewrites no function body.
- Your change only touches docs, diagrams, images or configuration.
- Your change only deletes or renames code.
- Your change is mostly generated files or copied third-party code, with almost no function body that you wrote.

It still applies when every function that you added or rewrote is already small and focused. It also applies when your change copies code or leaves dead code.

## Common mistakes

- Counting lines or jobs. There is no line limit and no fixed number of jobs. Instead, ask if a reader can name the function’s job in a phrase and follow it at a glance.
- Splitting a function that already does one job. A function that checks its input, does the work and returns the result does one job. So does a function that only calls named helpers one after another. Instead, split only where several full jobs are mixed in one body.
- Splitting a long function into parts with vague names, such as `step2` or `handleData`. A vague name hides the purpose of the function. Instead, name each part after its job.

## Sources

- [Function length](https://martinfowler.com/bliki/FunctionLength.html), Martin Fowler, 2016. When you must study code to see what it does, move it into a function named after what it does. Then the purpose of the function is clear, and a reader rarely needs the body.
- [Replace nested conditional with guard clauses](https://refactoring.com/catalog/replaceNestedConditionalWithGuardClauses.html), Martin Fowler, Refactoring catalog. Check each special case first and return early, so the normal path is no longer nested.
- [Write short functions](https://google.github.io/styleguide/cppguide.html#Write_Short_Functions), Google C++ Style Guide. Google prefers small, focused functions because they are easier to read, change and test. It sets no hard length limit.
- [An empirical validation of cognitive complexity as a measure of source code understandability](https://doi.org/10.48550/arXiv.2007.12520), Muñoz Barón, Wyrich and Wagner, ESEM 2020. The study pooled about 24,000 measurements of how well people understood 427 code snippets. Code took longer to understand when it scored high on a measure that adds weight for deeper nesting. Results for correct answers were mixed.
