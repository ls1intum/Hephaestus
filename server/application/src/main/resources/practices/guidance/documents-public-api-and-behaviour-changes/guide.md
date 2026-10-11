## How to do it

- Before you ask for a review, list what your change alters that other software depends on. Look for exported functions, published packages and documented endpoints. Include the format of stored or sent data, the errors and responses that callers handle, and default settings.
- For each item, write a note in the same pull request or merge request. Update the doc comment, add a changelog line, update the reference docs, or say it in the description.
- Name the exact parameter, field, route or default that moved. For example, instead of “Updated the users endpoint”, write “The users endpoint returns `fullName` in place of `name`”.
- If you remove or rename something, or change a default, tell consumers how to adapt. Say what to use instead, so their code keeps working after they update.

## When it does not apply

- The changed code is internal and has no separate consumer. Examples are internal types, private signatures, local refactors and fields that only your app uses.
- The diff and the description show no change to a public surface at all.

It still applies when your project is an app and not a library, and other software uses its endpoints, config or data formats.

## Common mistakes

- Deciding by the kind of project or by a keyword such as `public` or `export`. Instead, ask whether a separate consumer depends on the code you changed. That can be another team’s service or a project that installs your package.
- Writing a note that never names what changed, such as “API improvements”. Instead, name the parameter, field, route or default that a consumer must adapt to.
- Leaving the note for a later change. Instead, ship it with the change, so consumers hear about it when the change lands.

## Sources

- [What to look for in a code review](https://google.github.io/eng-practices/review/reviewer/looking-for.html), Google Engineering Practices. A change to how people use code needs matching docs, including generated references.
- [Semantic Versioning 2.0.0](https://semver.org/), Tom Preston-Werner. Software that follows it declares a public API and updates its docs when it deprecates part of that API.
- [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/), Olivier Lacan. A changelog lists notable changes for people, with changed, deprecated and removed features in their own groups.
- [How to break an API: cost negotiation and community values in three software ecosystems](https://www.cs.cmu.edu/~ckaestne/pdf/fse16.pdf), Bogart and others, FSE 2016. In interviews with 28 developers, maintainers eased the cost of breaking changes by telling users and documenting how to upgrade.
