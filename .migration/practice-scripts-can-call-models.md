#### 🔴 Practice scripts are the review program, and results replace observations

This release changes the database, the API, the agent image and the browser extension together.
The old and new versions cannot run against the same database.
There is no rolling upgrade and no grace period for practice scripts in the earlier form.

**Before you upgrade.**

1. Stop each worker role, and let its [graceful drain](https://docs.hephaestus.build/admin/runtime-roles#graceful-drain) finish.
   The drain waits for active practice reviews, and returns the others to the queue.
   An attempt of the earlier release cannot finish against the new database.
2. Stop the server and webhook roles.
   Keep this downtime short.
   A provider does not send a push event again when it arrives while the webhook role is stopped.
3. Make a database backup, as [Backup & Restore](https://docs.hephaestus.build/admin/backup-restore) tells.
4. Verify the backup: restore it into a test database, and make sure that the restore completes.

**Upgrade.**

1. Upgrade every runtime role to this release: server, worker and webhook.
2. If you set `HEPHAESTUS_IMAGE_AGENT_PI` yourself instead of from the release lock, use the `agent-pi` image of this release.
   With an older image, the server logs a runtime contract mismatch at start.
   Each practice review then fails before it calls a model.
3. Start the server, and wait until its migration is complete.
4. Ask your users to update the Hephaestus browser extension to this release.
   An extension from an earlier release cannot read practice results from this server.

To undo the upgrade, restore the backup with the previous release.
Do not start the previous release against the migrated database.
Results that this release recorded after the upgrade are not in the backup.

**Workspace practice scripts in the earlier form.**
A practice script is now the review program of its practice, and its `run` returns the practice's result.
A workspace script from an earlier release is a plain function, and it no longer runs.
Its practice fails in each review with the error *This script uses the retired positional form. Rewrite it with definePracticeScript.*

The practice then has no result in that review, and the error never becomes a statement about the work.
Ask the admins of each workspace to rewrite each such script, as [Upgrade a script from an earlier release](https://docs.hephaestus.build/admin/practice-scripts#upgrade-a-script-from-an-earlier-release) tells.

**Bundled practice scripts.**
At its first start, the server gives the new script to each copy of a bundled practice whose script nobody changed.
This applies to the copies of each workspace and to the instance's own copies, with an audit entry for each.
A copy with a changed script keeps it, and its practice fails as above until an admin accepts the practice's update or rewrites the script.

**Custom API clients.**
The verdict of one practice in one review is now a **result**.
An **observation** is now one problem under a not met result.
Each result keeps the ID of the observation that it was.
Custom clients must use these new names:

| Before | After |
| --- | --- |
| `/workspaces/{workspaceSlug}/practices/observations` | `/workspaces/{workspaceSlug}/practices/results` |
| `/workspaces/{workspaceSlug}/practices/observations/summary` | `/workspaces/{workspaceSlug}/practices/results/summary` |
| `/workspaces/{workspaceSlug}/practices/observations/{observationId}` | `/workspaces/{workspaceSlug}/practices/results/{practiceResultId}` |
| `/workspaces/{workspaceSlug}/practices/reviews/observations` | `/workspaces/{workspaceSlug}/practices/reviews/results` |
| `/workspaces/{workspaceSlug}/practices/reviews/observations/{observationId}` | `/workspaces/{workspaceSlug}/practices/reviews/results/{practiceResultId}` |
| `/workspaces/{workspaceSlug}/practices/reviews/observations/{observationId}/validity` | `/workspaces/{workspaceSlug}/practices/reviews/results/{practiceResultId}/validity` |
| Operations `listObservations`, `getObservation` | `listResults`, `getPracticeResult` |
| Operations `listPracticeReviewObservations`, `getPracticeReviewObservation`, `updatePracticeReviewObservationValidity` | `listPracticeReviewResults`, `getPracticeReviewResult`, `updatePracticeReviewResultValidity` |
| Tag `Practice Observations` | `Practice Results` |
| Schemas `ObservationDetail`, `ObservationEvidence`, `ObservationInvalidation`, `ObservationList` | `PracticeResultDetail`, `PracticeResultEvidence`, `PracticeResultInvalidation`, `PracticeResultList` |
| Schemas `PageResponseDTOObservationList`, `PagedModelReviewObservation` | `PageResponseDTOPracticeResultList`, `PagedModelReviewResult` |
| Schemas `ReviewObservation`, `ReviewObservationCounts`, `ReviewObservationDetail`, `ReviewBoundObservation` | `ReviewResult`, `ReviewResultCounts`, `ReviewResultDetail`, `ReviewBoundResult` |
| Schemas `PracticeStandingObservation`, `UpdateObservationValidityRequest` | `PracticeStandingResult`, `UpdatePracticeResultValidityRequest` |
| `EvidenceUndecidability`, one object | `OpenQuestion[]`, a list of `{ openQuestion, wouldSettleIt, citation? }` |

The problems of results across a workspace are a new list, `GET /workspaces/{workspaceSlug}/practices/reviews/results/observations` (operation `listPracticeReviewResultObservations`).
No earlier path or operation name has a new meaning.
| `observationId` on `PracticeStandingResult` and `ReviewBoundResult` | `practiceResultId` |
| `observations` on `PracticeGroupReviewRun`, `PracticeGroupStanding`, `PracticeReviewBucket`, `ProfileReviewRunDetail`, `ReviewFeedbackDetail`, `ReviewRunSummary`, `PracticeReviewCounts` and `PracticeReviewOverview` | `practiceResults` |
| `observationsInvalidated` on `PracticeReviewCounts` and `PracticeReviewOverview` | `practiceResultsInvalidated` |
| `observationCount` on `PracticeTraceEntry` and `ReviewFeedback` | `practiceResultCount` |
| `totalObservations` on `DeveloperPracticeSummary` | `totalResults` |
| `kind=OBSERVATION` on `/workspaces/{workspaceSlug}/practices/reviews/lineage` | `kind=RESULT` |
| `observations`, `moreObservations` on `ReviewLineage`, and the schema `ReviewLineageObservation` | `results`, `moreResults`, and `ReviewLineageResult` with `problemCount` |
| `observationIds` on `ReviewLineageFeedback` | `practiceResultIds` |
| Query parameter `observationId` on `/workspaces/{workspaceSlug}/practices/reviews/feedback` | `practiceResultId` |
| `recurrenceKey` on a result | `recurrenceKey` is gone from the result. Each problem has its own. |

`PracticeResultDetail` and `ReviewResultDetail` gain `observations`, `example` and `coverage`.
`ReviewResultDetail` also gains `provenance`, and `ReviewResult` gains `problemCount`.
`AgentJobUsage.totalCostUsd` now includes what the models of the job's practice scripts cost, and `practiceScriptCostUsd` gives that part.

`PracticeResultList` gains `problems`, a list of `{ severity, summary }` in the review's order.
It is empty when the result is not `NOT_MET`, or when the result detail does not show the evidence.
It never holds feedback text.

A result's own `evidence.citations` no longer repeat the citations of its problems.
The severity `INFO` keeps its value, and the app now labels it **Trivial**.

Custom clients of the practice API must also send and read `practiceScript` instead of `precomputeScript`.
The field changes on `Practice`, `PracticeDefinition`, `CreatePracticeRequest`, `UpdatePracticeRequest`, `CuratedPracticeDefinition` and `CuratedPracticeRequest`.
In `fieldsToClear` and in the fields of a release review, use `PRACTICE_SCRIPT` instead of `PRECOMPUTE_SCRIPT`.
A request that still sends `precomputeScript` succeeds, but Hephaestus ignores the script in it.
Thus, a create request saves no script, and an update request leaves the stored script unchanged.
The bundled webapp needs no operator action.

**Feedback on the work.**
A met result is never sent to the work as praise.
The review on a pull request or merge request speaks only about results that are not met.
When a review has no such result, Hephaestus posts no review on the work.
The practice pages and Heph still show met results.

**Custom agent images.**
The history files that the server stages for a review or for Heph name each result by `practiceResultId`, not by `id`.
These files are `inputs/history/observations.json` and the `observations.jsonl` file of each person under `context/people/`.
Feedback history rows keep `id`.
A custom agent image that reads these files must read the new key.

**Configuration.**
No setting is removed.
Two optional settings are new: `PRACTICE_REVIEW_DIRECT_MODEL_TOKENS_PER_ATTEMPT` (default 200000) and `PRACTICE_REVIEW_DIRECT_MODEL_SEARCH_UNITS_PER_ATTEMPT` (default 1000).
They limit the direct model calls of the practice scripts of one review attempt.
A practice-review timeout below 61 seconds now runs at 61 seconds.

**What applies automatically.**
The migration renames `observation` to `practice_result`, `observation_invalidation` to `practice_result_invalidation`, and `feedback_observation` to `feedback_evidence`.
Every row keeps its ID.

Each not met result gets one observation with a new ID, from its stored severity, summary, rationale and citations.
The migration invents no examples and no coverage for earlier results.
Feedback that Hephaestus delivered before the upgrade is not delivered again.
The migration also renames the script columns and moves the script key in the stored adoption bases.
Audit entries from before the upgrade name the script hash `precomputeScriptSha256`.
Newer entries name it `practiceScriptSha256`.
