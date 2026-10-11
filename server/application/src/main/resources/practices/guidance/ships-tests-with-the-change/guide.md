## How to do it

- Before you ask for a review, list the behavior that your change adds or changes. Look for a new function or endpoint, a new branch or calculation, and a changed validation or state change.
- For each item, add or update a test in the same pull request or merge request. For example, you add a check for expired accounts to sign-in. Instead of leaving it untested, add a test that signs in with an expired account and expects a refusal.
- Make the test call the function, endpoint or path that you changed, or check the corrected result.
- Put the new test where the repository keeps the tests for the code near it.
- When you fix a bug, write the test first. Run it on the old code to see it fail. Fix the code in the same change. Run the test again to see it pass.

![Three steps for a bug fix follow in order. First you write the test, and it shows the bug. Then you fix the code in the same change. Last, you run the test again, and it confirms the fix.](figures/fix-order.svg)

## When it does not apply

- The change is empty.
- The change holds only tests, only docs, or only configuration. Generated files, copied third-party code, lockfiles and database migrations do not count.
- The change only renames, formats, or changes type declarations, and adds no behavior. Those files do not count.
- The change only touches layout, such as a title, a color, the spacing, or a button that does nothing yet.

It still applies when your repository has no tests yet.

## Common mistakes

- Chasing every edge case or polishing test names before the new behavior has any test. Instead, first add one test that exercises the new behavior.
- Adding any test to the change and calling it done. A test counts only when it exercises the new behavior. Instead, make it call the code you changed, or check the corrected result.
- Fixing a bug with nothing that holds the fix in place. Instead, add a test that shows the bug, and fix the bug in the same change.
- Promising the tests in a later change. Instead, ship them with the code, so later changes cannot break the behavior unnoticed.

## Sources

- [What to look for in a code review](https://google.github.io/eng-practices/review/reviewer/looking-for.html), Google Engineering Practices. Google advises adding tests in the same change as the production code.
- [Testing overview](https://abseil.io/resources/swe-book/html/ch11.html), Adam Bender, in Software Engineering at Google, 2020. Automated tests catch mistakes early, so a team can change code with confidence and keep found bugs from coming back.
- [Self-testing code](https://martinfowler.com/bliki/SelfTestingCode.html), Martin Fowler, 2014. Work is complete only with the tests that keep it working. A bug fix starts with a test that exposes the bug.
