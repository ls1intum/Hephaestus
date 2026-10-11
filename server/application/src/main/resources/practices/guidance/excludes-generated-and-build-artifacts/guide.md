## How to do it

- Before you ask for a review, compare each changed path with the project’s ignore rules and documented generation steps. Tool output includes build folders, coverage reports, logs, minified bundles and editor files.
- For output that the project does not track, add its path to the project’s ignore file.
- Remove that output from Git tracking. For example, instead of leaving `dist/app.min.js` in the change, run `git rm --cached -- dist/app.min.js`.
- Put the files that your own editor and operating system make in your personal ignore file. Then they never reach any repository.
- For a generated file that the project tracks, follow its documented generation steps. Regeneration can be valid even when its source does not change.

## When it does not apply

- The change is empty.

It still applies when every file in the change is generated.

## Common mistakes

- Removing every generated file. Generated output is not wrong in itself. Some projects track it on purpose, such as a route tree or a diagram. Instead, check the project’s ignore rules and docs before you remove one.
- Fixing a generated file by hand. The next run of the generator undoes your fix. Instead, change the source and regenerate. A banner such as `DO NOT EDIT` or `@generated` at the top tells you that a tool wrote the file.
- Changing the source of a tracked generated file without regenerating it. The file then no longer matches its source. Instead, regenerate it in the same change.
- Adding a path to the ignore file and expecting the tracked copy to go away. An ignore rule does not affect a file that is already tracked. Instead, also remove the file from tracking with `git rm --cached`.

## Sources

- [gitignore](https://git-scm.com/docs/gitignore), Git documentation. Ignore rules name intentionally untracked files, such as temporary editor files, and do not affect files that Git already tracks.
- [What to look for in a code review](https://google.github.io/eng-practices/review/reviewer/looking-for.html), Google Engineering Practices. Reviewers can scan over generated code, but they read every line that a person wrote.
- [Generate Go files by processing source](https://pkg.go.dev/cmd/go#hdr-Generate_Go_files_by_processing_source), Go documentation. A generated file carries a `DO NOT EDIT` line, and the build never runs the generator for you.
