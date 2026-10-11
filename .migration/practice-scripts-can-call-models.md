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
