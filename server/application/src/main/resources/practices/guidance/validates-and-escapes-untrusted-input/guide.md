## How to do it

- Treat each value from outside your code as untrusted. This means request data, uploaded file names, command-line arguments, and replies from other services.
- Put a value into a database query only as a bound parameter. For example, instead of `"… WHERE name = '" + name + "'"`, write `WHERE name = ?` and bind `name`.
- In a page, keep the template’s auto-escaping on. If you build markup yourself, encode the value for its exact place: text, an attribute, or a script.
- In a command, a file path, or a URL, keep the value in its own slot. Examples are one list item per argument, one part of a built URL, and a path under a base folder.
- Some values cannot be bound, such as a column name. Check each of them against a list of allowed values.

## When it does not apply

- In the lines you added, no outside value reaches a query, a command, a file path, a URL, or markup.
- The value is only shown in a text control that does not read markup.

It still applies when you use a safe API, such as a prepared statement, a URL builder, or an auto-escaped template. The safe API is how you meet the practice.

## Common mistakes

- Encoding a whole URL with one general encoder. Characters such as `&`, `=` and `/` can stay, and change the URL’s structure. Instead, encode each path segment and query value on its own.
- Escaping for the wrong place. HTML escaping does not protect a value inside a script or an event handler. Instead, use the encoder for the place where the value lands.
- Turning off auto-escaping for one value, such as with React’s `dangerouslySetInnerHTML`. Instead, keep escaping on, or first clean the HTML with a trusted sanitizer, a library that removes dangerous tags.
- Writing your own escaping for a query. It breaks easily. Instead, bind the value, and check the parts that cannot be bound against a list.

## Sources

- [SQL Injection Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html), OWASP. Bound parameters keep code and data apart, and hand-made escaping is strongly discouraged.
- [Cross Site Scripting Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html), OWASP. Encoding must match the exact place in the page, and each URL query value needs its own encoding.
- [OS Command Injection Defense Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/OS_Command_Injection_Defense_Cheat_Sheet.html), OWASP. Prefer a library function to a shell command, and pass the program and each argument separately.
- [2025 CWE Top 25 Most Dangerous Software Weaknesses](https://cwe.mitre.org/top25/archive/2025/2025_cwe_top25.html), MITRE, 2025. Cross-site scripting and SQL injection rank first and second. Path traversal and OS command injection are also in the top 10.
