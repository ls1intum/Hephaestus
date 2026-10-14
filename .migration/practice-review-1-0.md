#### 🔴 Upgrade the practice model, scripts and clients together

This release changes practice definitions, people, reviews, results and feedback records in one migration.
Old and new runtime roles cannot use the database together.
There is no rolling upgrade or compatibility period.

Before the outage, test this release's migration on a verified database restore.
Prepare any script changes with the procedure under **Custom practice scripts**.
Repeat the test on a fresh restore until the migration completes.

Before the upgrade:

1. Quiesce new review submission and provider input.
2. Apply the script changes tested during the rehearsal through the previous release's server.
3. Drain active worker operations through the [role drain procedure](https://docs.hephaestus.build/admin/runtime-roles#graceful-drain).
4. Let active worker reviews finish.
5. Stop every worker role after its drain completes.
6. Drain and stop the server and webhook roles.
7. Make a database backup.
8. Restore the backup to a test database and verify the restore.
9. Record the release lock and configuration of the previous release.

A provider can lose events that arrive while the webhook role is stopped.
After restart, normal source sync repairs only the events that its adapter can recover.
Keep the outage short and verify source sync afterward.

Upgrade:

1. Install this release for the server, worker and webhook roles.
2. Use this release's verified agent image.
3. If HEPHAESTUS_IMAGE_AGENT_PI is overridden, update it to the matching agent-pi image.
4. Start the server and wait for the migration to complete.
5. Deploy the matching webapp.
6. Restore saved custom definitions with tested version 3 scripts.
7. Start the worker and webhook roles.
8. Ask developers to update the browser extension.
9. Update custom API clients before they use practice resources.
10. Verify one item review and one repository scan in the operator interface.
11. Check results, withheld reasons and provider-copy receipts.

The migration preserves retained result IDs and feedback relationships.
It does not post migrated feedback again.
Historical unknown visibility stays Unconfirmed. A confirmed sibling copy keeps its delivery time.
Old positive measurements do not establish the complete current standard merely because their names changed.

**Practice settings.**
Practice definitions are stored on practice versions.
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
Membership and reviewed developers use native accounts rather than SCM user IDs.
Roster sync can create an UNCLAIMED account before sign-in.

Sign-in claims the roster account instead of creating a second account.
AI choice and the preference for external comments and reminders apply across the account's workspaces.
Review conflicting imported preferences in the account settings.
A former member keeps membership history but has no active workspace access.

**Custom practice scripts.**
A script is a default export of definePracticeScript with meta and run(ctx).
run returns one candidate result for the supplied item. The script contract is version 3.

A null script uses the built-in script. A changed invalid script does not.
The migration transforms only exact audited bundled scripts.
An unsupported script stops the migration, including a script retained in a catalog comparison base.
Rewriting a custom script before upgrade does not add its source to the audited map.
The refusal names the practice ID or catalog slug and identifies a comparison base when it applies.

Prepare these changes on the restored database with the previous release:

1. Save complete custom definitions and scripts outside the database, with their workspace and catalog identifiers.
2. For affected bundled instance entries, select **Restore Hephaestus default**.
3. For remaining custom instance entries, clear **Static analysis script** and save the definition.
4. If an affected catalog entry is absent, recreate it from the saved workspace definition. Use its original slug and no script.
5. Accept affected workspace catalog updates to advance their comparison bases to the prepared definitions.
6. For remaining custom workspace practices, clear **Static analysis script** and save the definition.
7. Test the migration again on a fresh restore with these changes.

A reset removes local customization. Save that content before the reset.
These changes create new active definitions. Do not edit pinned historical definitions or migration checksums.
Keep new review submission quiesced when you apply the tested changes to production.
Resume reviews for those practices only after you restore their custom content with tested version 3 scripts.

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
See [Practice review operations](https://docs.hephaestus.build/admin/practice-review-operations) for the minimum practice-review timeout.

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

The [operator result-text entry](https://github.com/hephaestus-build/Hephaestus/blob/main/MIGRATION.md) owns nullable summaries and withheld evidence.
Show the unavailable state while retaining safe outcome, practice, item and delivery facts.
Do not substitute an empty problem list for unavailable problem detail.
Feedback response resolution and usefulness remain independent dimensions.
Result outcomes and feedback response values are different enums.

**Rollback.**
Stop this release's roles and restore the verified pre-upgrade backup.
Start the previous release with its matching images and configuration.
Do not start old binaries against the migrated database.
The backup does not contain data recorded after the upgrade.
