## How to do it

- Before you ask for a review, open each wiki page that your description or a linked issue names.
- In each page, find the passages about what your change alters. Look for a default, an API field, a command-line flag, or an error code. Look also for a step to deploy or run the system.
- Fix each passage that your code now makes false, before the change merges. For example, instead of leaving the old default timeout on the setup page, change it to the new one.
- When you remove a flag, a field, or an endpoint, remove or rewrite every passage that still offers it.
- Say in the description which wiki page you updated.

## When it does not apply

- Your description and your linked issues do not name a wiki page.
- Your change is empty.
- Your change does not touch anything that the linked page describes.

It still applies when only a linked issue names the page, and your description does not.

## Common mistakes

- Thinking that every change needs a wiki link or a wiki edit. A change with no related page is fine. Instead, update only the passages that your change makes false.
- Adding a link to the page, or writing “docs updated” in the description, and stopping there. Neither changes what the page says. Instead, read the passage and fix the old value in it.
- Assuming that the page is fine because its title still fits. A page can describe the right feature with the wrong default. Instead, compare each passage about your change with the new code.

## Sources

- [Documentation best practices](https://google.github.io/styleguide/docguide/best_practices.html), Google. Change the docs in the same change as the code, and delete docs that are wrong, because dead docs misinform.
- [What to look for in a code review](https://google.github.io/eng-practices/review/reviewer/looking-for.html), Google Engineering Practices. A reviewer checks that a change to how people build, test, use or release code also updates its documentation.
- [Detecting outdated code element references in software repository documentation](https://doi.org/10.1007/s10664-023-10397-6), Tan, Wagner and Treude, 2024. Of over 3,000 GitHub projects, most had docs that still named deleted code at some point in their history.
