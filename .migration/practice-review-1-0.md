#### 🔴 Upgrade the practice model, scripts and clients together

This release changes practice definitions, people, reviews, results and feedback records in one migration.
Old and new runtime roles cannot use the database together.
There is no rolling upgrade or compatibility period.

Before the upgrade:

1. Quiesce new review submission and provider input.
2. Drain active server and worker operations through the normal role drain procedure.
3. Let active worker reviews finish.
4. Stop every worker role after its drain completes.
5. Stop the server and webhook roles.
6. Make a database backup.
7. Restore the backup to a test database and verify the restore.
8. Record the release lock and configuration of the previous release.

A provider can lose events that arrive while the webhook role is stopped.
After restart, normal source sync repairs only the events that its adapter can recover.
Keep the outage short and verify source sync afterward.

Upgrade:

1. Install this release for the server, worker and webhook roles.
2. Use this release's verified agent image.
3. If HEPHAESTUS_IMAGE_AGENT_PI is overridden, update it to the matching agent-pi image.
4. Start the server and wait for the migration to complete.
5. Start the worker and webhook roles.
6. Deploy the matching webapp.
7. Ask developers to update the browser extension.
8. Update custom API clients before they use practice resources.
9. Verify one item review and one repository scan in the operator interface.
10. Check results, withheld reasons and provider-copy receipts.

The migration preserves retained result IDs and feedback relationships.
It does not post migrated feedback again.
Historical unknown visibility stays Unconfirmed. A confirmed sibling copy keeps its delivery time.
Old positive measurements do not establish the complete current standard merely because their names changed.

**Practice settings.**
Practice definitions are now stored on practice versions.
The new definition has reviewOn, appliesWhen, about, reads, reviewedBy, criteria, script, scan, lineNotes and guidance.
The source contract stays in reads.contract.
Human review explanations and limits stay in reviewedBy.
The role MERGER remains supported.

Feedback mode uses OFF, APPROVE_FIRST or AUTOMATIC.
OFF stops feedback at every destination, while eligible reviews can still record results.
PEOPLE or NOBODY under reviewedBy prevents automated review.
Review every practice left Off if the previous setting was intended to stop AI review.
Instance silent mode and workspace delivery pause control delivery, not review execution.

**People.**
Membership and reviewed developers now use native accounts rather than SCM user IDs.
Roster sync can create an UNCLAIMED account before sign-in.

Sign-in claims that record instead of making another person.
AI choice and the preference for external comments and reminders apply across the account's workspaces.
Review conflicting imported preferences in the account settings.
A former member keeps membership history but has no active workspace access.

**Custom practice scripts.**
A script is a default export of definePracticeScript with meta and run(ctx).
run returns one candidate result for the supplied item. The script contract is version 3.

A null script uses the built-in script. A changed invalid script does not.
Unchanged bundled copies receive the matching bundled script through the audited catalog update.
Changed copies keep their text until a workspace admin accepts the update or rewrites it.

The old positional function and leads-only output do not run.
Remove meta.version, meta.kinds and meta.tokens from preview scripts. Use the core entry point and the kit for the item’s domain.
Do not return candidate-level severity.
Set severity on each problem, with CRITICAL, MAJOR, MINOR or TRIVIAL.
Use DOES_NOT_APPLY instead of NOT_APPLICABLE.

Use COULD_NOT_DETERMINE instead of UNDETERMINED.
Use evidence.openQuestions instead of evidence.undecidability.
Read item, review.origin, practice.versionId and about from the new context.
For a scan, about is null and the supplied item is a file at the pinned repository version.
A required model that is unavailable withholds the result. It does not produce a claim about missing evidence.
Use the practice-script guide's local test procedure before enabling the rewritten script.

Remaining queued work from the previous runtime is cancelled with CONTRACT_CHANGED.
The migration preserves its history and write receipts. It does not replay old output.
Start a fresh review when you need another result.

Direct script model calls default to 200,000 tokens and 1,000 search units per attempt.
Set PRACTICE_REVIEW_DIRECT_MODEL_TOKENS_PER_ATTEMPT to change the token limit.
Set PRACTICE_REVIEW_DIRECT_MODEL_SEARCH_UNITS_PER_ATTEMPT to change the search-unit limit.
A spent limit returns HTTP 402. Calls already in flight can finish above the limit.
A practice-review timeout below 61 seconds runs at 61 seconds.

**Custom API clients.**
Workspace paths below begin with /workspaces/{workspaceSlug}.
Use the generated OpenAPI contract from this release.

Changed practice-model bigint IDs and monetary amounts are decimal strings.
SCM mirror APIs keep their existing identifier types.
Nullable response fields are present with null rather than omitted.
There are no redirects from retired practice resource names.

| Earlier contract | New contract |
| --- | --- |
| /practices/reviews/observations | /practices/reviews/results |
| /practices/reviews/result-observations | /practices/reviews/problems |
| /practices/trace/{artifactKind}/{artifactId} | /practices/items/{itemId}/trace |
| /agents/jobs/{jobId} | /practices/reviews/{reviewId} |
| /practice-profile/review-runs | /practice-profile/reviews |
| /practices/backfill-runs | /practices/review-batches |
| artifactKind and artifactId | item reference and itemId |
| agentJobId | reviewId |
| aboutUserId and recipientUserId | developerId with a native account ID |
| practiceRevisionId | practiceVersionId |
| observations on a result | problems |
| evidenceRationale | explanation |
| evidence.undecidability | evidence.openQuestions |
| observedAt | recordedAt |
| feedback.channel | feedback.destination |
| IN_CONTEXT, IN_APP, IN_CHAT | ON_WORK, PRACTICE_PAGE, CONVERSATION |
| feedback.deliveryState | feedback.state |
| placements and dispatches | copies and sends |
| precomputeScript and practiceScript | definition.script |
| NOT_APPLICABLE and UNDETERMINED outcomes | DOES_NOT_APPLY and COULD_NOT_DETERMINE |
| INFO severity | TRIVIAL |
| HUMAN_APPROVAL autonomy | APPROVE_FIRST feedbackMode |
| invalidation and validity updates | result marks with create/remove operations |
| DISPUTED response resolution | DISAGREE |

Operator result/problem summaries and evidence can be absent when source access cannot be verified.
Show the unavailable state while retaining safe outcome, practice, item and delivery facts.
Do not substitute an empty problem list for unavailable problem detail.
Feedback response resolution and usefulness remain independent dimensions.
Result outcomes and feedback response values are different enums.

**Rollback.**
Stop this release's roles and restore the verified pre-upgrade backup.
Start the previous release with its matching images and configuration.
Do not start old binaries against the migrated database.
The backup does not contain data recorded after the upgrade.
