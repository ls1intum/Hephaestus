## How to do it

- Start each new setting closed, with no access, and add access for one named need at a time.
- Name the exact websites that may read your endpoint’s responses in a browser. For example, instead of copying the request’s `Origin` header into the response, check it against a fixed list first.
- Turn on each debug setting only in local development. Examples are detailed error pages and a skipped certificate check.
- Before you log, print or save a value, check what it can hold at runtime. Leave out or mask any token, password, key, cookie or `Authorization` header.
- If you must widen access or loosen an access check, say why in a code comment next to the setting. Keep the real limit in the code too, such as an access check or a development-only setting.

## When it does not apply

- Your change only changes the user interface, layout, docs, data models or tests. It adds no transport, origin, permission, file mode, access check, credential, logging or debug setting.
- Your change is mostly generated or vendored files. None of the lines you wrote by hand touches such a setting.

It still applies when you add a log, print or save call that includes any runtime value, because that value can hold a credential.

## Common mistakes

- Trusting a reason to make a wide setting safe. A comment such as “internal only”, or a test or sample file name, does not limit who can reach it. Instead, check the real boundary and who is allowed through it.
- Fixing one setting and stopping. Instead, check every setting that your change adds.
- Trusting the variable name. A log of a login response or a request header can leak a token, even when nothing is named “token”. Instead, follow what the value can hold.
- Using a wildcard origin, or a file that anyone on the machine can change, to get past an error. Instead, name the origin, user or group that needs access, and grant only that.

## Sources

- [The protection of information in computer systems](https://web.mit.edu/Saltzer/www/publications/protection/Basic.html), Saltzer and Schroeder, 1975. Base access on permission, so the default is no access. Give each program only the privileges it needs.
- [A02:2025 Security Misconfiguration](https://owasp.org/Top10/2025/A02_2025-Security_Misconfiguration/), OWASP Top 10, 2025. Every application tested had some misconfiguration, such as unneeded features or error messages that say too much.
- [Testing cross origin resource sharing](https://wstg.owasp.org/v4.2/4-Web_Application_Security_Testing/11-Client-side_Testing/07-Testing_Cross_Origin_Resource_Sharing/), OWASP Web Security Testing Guide. A `*` origin, or an origin sent back unchecked, is insecure unless the API is public.
- [Logging cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html), OWASP Cheat Sheet Series. Remove or mask access tokens, session IDs, passwords and keys before they reach a log.
