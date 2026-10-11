## How to do it

- When a call fails and this code cannot fix it, pass the error to the caller with context. For example, instead of an empty `catch` on `saveOrder`, throw an error that names the order and wraps the original.
- When no caller can react, record the failure where a person will see it. Use a log that someone reads, a failure metric, or an error state on the screen.
- When the code does not show why a failure is safe to ignore, explain the reason in the handler.
- Handle each error that a call returns and each promise that rejects.
- When a lookup or a parse can fail, handle its failure branch too. A bare early return tells nobody what went wrong.

## When it does not apply

- Your hand-written code adds no call that can throw, reject or return an error. Generated or vendored files do not count.
- Your change adds no source code, such as docs, diagrams, images, or configuration only.
- Your change only ends work that a newer request replaces, or whose owner went away.
- The source proves that the guarded bad case cannot occur, such as parsing a fixed valid URL.

It still applies when your new code already handles each failure well.

## Common mistakes

- Thinking that every handler must rethrow. A log that a person will see keeps the failure visible. So does an error state on the screen. A normal empty state counts only when the failure is also logged with enough context to diagnose it.
- Catching an error and never using it, or dropping it with a shortcut such as Swift’s `try?` or Go’s `_`. Instead, pass it on, log it, or write why it is safe to ignore.
- Writing “ignore” over an empty handler. Say why silence is safe, such as “the file can already be gone”.
- Leaving a TODO in place of a handler. Handle the failure before review.

## Sources

- [Simple testing can prevent most critical failures](https://www.eecg.utoronto.ca/~stumm/Yuan-OSDI-14.html), Yuan and others, 2014. The study looked at 198 failures of five distributed systems. Wrong handling of signaled errors caused 92% of the catastrophic failures. About a third came from simple mistakes, such as an empty handler or a TODO.
- [Caught exceptions: not ignored](https://google.github.io/styleguide/javaguide.html#s6.2-caught-exceptions), Google Java Style Guide. It is rarely correct to do nothing with a caught exception. When it is, a comment explains why.
- [Handle errors](https://go.dev/wiki/CodeReviewComments#handle-errors), Go Code Review Comments, the Go project. Do not throw away an error with `_`. Handle it or return it.
- [CWE-390: Detection of error condition without action](https://cwe.mitre.org/data/definitions/390.html), MITRE. An attacker can use an ignored error to put the system in an unexpected state.
