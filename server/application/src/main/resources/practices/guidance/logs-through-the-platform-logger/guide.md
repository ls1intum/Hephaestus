## How to do it

- Use the logger that your project already uses for the code around your change.
- If the project has no logger yet, add the standard logger for your platform or framework.
- Send each state, error or step through the logger, at the level that fits it. For example, instead of `print("Sync failed")`, write `logger.error("Sync failed")`.
- If you print only while you debug, log the message at the debug level instead. On Apple platforms, you can also put the print in an `#if DEBUG` block.

## When it does not apply

- Your change adds no diagnostic line to application code: no print call, such as `print` or `console.log`, and no logger call.
- The only prints you add are the program’s own output, as in a command-line tool or a script.
- The only diagnostic lines you add show a secret, such as a password or a token. A security practice covers those.

It still applies when your change adds only logger calls.

## Common mistakes

- Replacing every print with a logger call. A print that is the program’s own output is correct, as in a command-line tool or a test. Instead, keep those prints and move only the diagnostics to the logger.
- Reporting an error path only with a print. Instead, log the error at the error level. Then it reaches the platform logs at the error level, where you can filter for it.
- Printing personal data, such as an email address. Instead, leave it out of the message, or log it through a logger that can hide private values.

## Sources

- [Generating log messages from your code](https://developer.apple.com/documentation/os/generating-log-messages-from-your-code), Apple. Log messages carry a subsystem, a category and a level, so you can filter them. Errors are kept on disk, and private values can be hidden.
- [Logging HOWTO](https://docs.python.org/3/howto/logging.html), Python Software Foundation. Use `print()` for the console output of a command-line program. Use a logger to report events, warnings and handled errors while a program runs.
- [Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html), OWASP. Keep logging consistent across an application, and build on the logging that your framework gives you. Keep sensitive personal data out of logs, and take care with other personal data, such as email addresses.
