#### 🔴 `practiceScript` replaces `precomputeScript` in the practice API

Custom clients of the practice API must send and read `practiceScript` instead of `precomputeScript`.
The field changes on `Practice`, `PracticeDefinition`, `CreatePracticeRequest`, `UpdatePracticeRequest`, `CuratedPracticeDefinition` and `CuratedPracticeRequest`.
In `fieldsToClear` and in the fields of a release review, use `PRACTICE_SCRIPT` instead of `PRECOMPUTE_SCRIPT`.
A request that still sends `precomputeScript` succeeds, but Hephaestus ignores the script in it.
Thus, a create request saves no script, and an update request leaves the stored script unchanged.

If you set `HEPHAESTUS_IMAGE_AGENT_PI` yourself instead of from the release lock, use the `agent-pi` image of the server's release.
With an older image, no practice script runs, and reviews continue without the scripts' leads.

The database migration renames the script columns and moves the script key in the stored adoption bases.
It applies automatically.
Audit entries from before the upgrade name the script hash `precomputeScriptSha256`.
Newer entries name it `practiceScriptSha256`.
The bundled webapp needs no operator action.

#### 🔴 Workspace practice scripts in the old form stop giving leads

Every practice script now uses `definePracticeScript`.
A workspace practice script whose default export is a plain function, the form of earlier releases, no longer runs.
Its review shows the error *This script uses the retired positional form. Rewrite it with definePracticeScript.*
The review continues on its other evidence, and the error never becomes a statement about the work.
Ask the admins of each workspace to rewrite such a script, as [Rewrite a script in the retired form](https://docs.hephaestus.build/admin/practice-scripts#rewrite-a-script-in-the-retired-form) tells.

The scripts and criteria of the bundled practices changed.
Each workspace that adopted a bundled practice gets an update offer for it.
