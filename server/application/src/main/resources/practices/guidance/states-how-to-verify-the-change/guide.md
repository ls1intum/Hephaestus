## How to do it

- Write the check for the main effect of your change, the one that your title or issue names.
- Say where to start, such as a screen, a command, a named test or a preview that you wrote. You do not need to repeat the setup steps that your README gives.
- Say what to do there, such as run, select, enter or restart.
- Say what the reviewer will see when it works. For example, instead of “Fixed the login flow”, write “Sign in with a wrong password. The form says that the password is wrong”.
- If the check needs a key, a device or test data, say how to get it.

## When it does not apply

- The change is empty.
- The change only fixes whitespace or formatting, or moves files without changing them.
- The change only adds planning notes.
- The change only adds a diagram and the README text that explains it.
- The change only explains the design or a tool in the README, with no setup, run or deploy steps.
- The change only adds or removes starter code, such as an unused “Hello World” view or Xcode’s placeholder text.

It still applies when the change adds setup, run or deploy steps, or changes settings, dependencies, tests or build scripts.
It also applies when starter code sits beside a real change.

## Common mistakes

- Thinking that you need a long, numbered test plan. One sentence that names where to look and what is there now can be enough. An example is “The settings screen now shows a dark mode switch”.
- Writing “Testing: N/A” or leaving the heading empty. That tells the reviewer nothing. Instead, write the check, or name the issue whose acceptance criteria give it, such as with “Closes #12”.
- Listing the parts that you added, such as “a service, a model and a view”. Instead, say what the reviewer will now see.
- Leaving a check that names a button or a screen that the change does not have. Instead, update the check to match the button or screen that the change has.

## Sources

- [Merge request workflow](https://docs.gitlab.com/development/contributing/merge_request_workflow/), GitLab. Its definition of done asks for the steps or setup that reviewers need to see the change.
- [Differential user guide: test plans](https://we.phorge.it/book/phorge/article/differential_test_plans/), Phorge, the maintained fork of the Phabricator code review tool. A test plan lists repeatable steps, with enough detail that someone new to the change can check how it behaves.
- [Commit message style guide](https://fuchsia.dev/fuchsia-src/contribute/commit-message-style-guide), Fuchsia project at Google. A `Test:` line tells reviewers which tests ran, and complex test steps go in a linked issue.
