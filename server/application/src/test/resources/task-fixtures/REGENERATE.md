# Task envelope fixture

`practice-review.json` is shared by `TaskEnvelopeFixtureTest` and `pi-task-paths.spec.ts`.
Update the input in the Java test and serialize it with `TaskEnvelopeWriter`.
Write that JSON to the fixture, then run both tests.

The shared candidate and execution fixtures are in `../practice-results/`.
Set `UPDATE_RUNNER_OUTPUT=1` when running `pi-runner-server-contract.spec.ts` to regenerate the output from the real runner.
Run the same test without that variable to verify the result.
