## How to do it

- Before you add a package, check whether you need it.
- Before you add a package, check that its name is exactly the one you meant.
- When you add a direct dependency or make a notable version change, write one sentence on why. Say what the package does in this change, or why its version changes. For example, instead of “Add yaml-lite”, write “Add yaml-lite to keep comments in plugin config files”.
- Put that sentence where a reviewer reads it. Use the description, a commit subject, or a comment in the manifest, the file that lists packages.
- Keep the manifest and any lockfile (the file of chosen versions) consistent with your package manager and repository policy. A compatible lockfile can stay unchanged.

## When it does not apply

- Your change does not add, remove or change a third-party dependency, its version, its source or its fixed version. Dependencies include packages, libraries, frameworks, plugins, container images and workflow actions.
- Your change only wires up your own code. Examples are an internal module, a test target, build settings, sources or resources. This is true even when the edit is inside a dependency manifest.

It still applies when your change only refreshes a lockfile.

## Common mistakes

- Writing a reason for every version change. A patch or minor version change needs no sentence, and neither does a change to the dependencies of your dependencies. A large lockfile diff from your tool is normal. Instead, explain each new direct dependency and each notable version change.
- Giving the package name as the reason. “Add yaml-lite” or “Update yaml-lite to 2.0” only says what changed. This is true even when the name hints at what the package does. Instead, say what the package does in this change, or why its version changes.
- Removing a fixed version or a lockfile entry to get past a conflict when your repository asks for reproducible builds. Instead, let your package manager resolve the conflict, and keep the lockfile consistent with the manifest.

## Sources

- [Concise guide for evaluating open source software](https://best.openssf.org/Concise-Guide-for-Evaluating-Open-Source-Software), OpenSSF Best Practices Working Group, 2025. OpenSSF recommends checking whether you need a dependency and whether its name is authentic.
- [Dependency management](https://abseil.io/resources/swe-book/html/ch21.html), Titus Winters, in Software Engineering at Google, 2020. A new dependency is not free, so weigh its costs before you import it.
- [package-lock.json](https://docs.npmjs.com/cli/v10/configuring-npm/package-lock-json), npm Docs. npm records exact dependency versions in a lockfile intended for version control.
- [Why have Cargo.lock in version control?](https://doc.rust-lang.org/cargo/faq.html#why-have-cargolock-in-version-control), The Cargo Book. A lockfile gives the same dependency versions at different times and on different systems.
