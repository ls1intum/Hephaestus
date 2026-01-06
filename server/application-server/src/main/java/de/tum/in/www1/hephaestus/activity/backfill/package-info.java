/**
 * Activity Event Backfill - populates historical activity events from existing entities.
 *
 * <h2>Purpose</h2>
 * <p>This package provides services for backfilling the activity event ledger with historical
 * data. Use cases include:
 * <ul>
 *   <li>Initial population when activity tracking is first enabled for a workspace</li>
 *   <li>Re-syncing after data recovery or migration</li>
 *   <li>Populating test environments with realistic historical data</li>
 * </ul>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link de.tum.in.www1.hephaestus.activity.backfill.ActivityEventBackfillService} -
 *       Main service orchestrating backfill operations with batch processing</li>
 *   <li>{@link de.tum.in.www1.hephaestus.activity.backfill.BackfillStartupRunner} -
 *       Optional automatic backfill on application startup (configuration-driven)</li>
 *   <li>{@link de.tum.in.www1.hephaestus.activity.backfill.BackfillProgress} -
 *       Thread-safe progress tracking record for monitoring operations</li>
 * </ul>
 *
 * <h2>Supported Event Types</h2>
 * <table border="1">
 *   <caption>Events generated per entity type</caption>
 *   <tr><th>Entity</th><th>Events</th><th>Description</th></tr>
 *   <tr><td>Pull Request</td><td>OPENED, MERGED, CLOSED</td><td>Up to 3 events per PR</td></tr>
 *   <tr><td>Review</td><td>APPROVED, CHANGES_REQUESTED, COMMENTED, UNKNOWN, DISMISSED</td><td>1-2 events per review</td></tr>
 *   <tr><td>Issue Comment</td><td>COMMENT_CREATED</td><td>1 event per comment</td></tr>
 *   <tr><td>Review Comment</td><td>REVIEW_COMMENT_CREATED</td><td>1 event per comment</td></tr>
 *   <tr><td>Issue</td><td>CREATED, CLOSED</td><td>1-2 events per issue</td></tr>
 * </table>
 *
 * <h2>Idempotency</h2>
 * <p>All backfill operations are idempotent. Running them multiple times will not create
 * duplicate events because the underlying event recording relies on a unique constraint
 * on the event_key (type:targetId:timestamp). This makes backfills safe to retry.
 *
 * <h2>Batch Processing</h2>
 * <ul>
 *   <li><strong>Batch Size:</strong> 500 entities per transaction (configurable)</li>
 *   <li><strong>Transaction Isolation:</strong> Each batch commits independently via
 *       {@code REQUIRES_NEW} propagation, allowing partial progress to persist</li>
 *   <li><strong>Memory Management:</strong> EntityManager cleared after each batch</li>
 *   <li><strong>Thread Safety:</strong> Progress tracking uses atomic counters</li>
 * </ul>
 *
 * <h2>Error Recovery</h2>
 * <p>The backfill system is designed for resilience:
 * <ul>
 *   <li><strong>Entity-level failures</strong> are logged and counted but don't abort the batch</li>
 *   <li><strong>Batch-level failures</strong> preserve previously committed batches</li>
 *   <li><strong>Re-running</strong> after failure skips already-recorded events (idempotent)</li>
 *   <li><strong>Investigation</strong>: Search logs for "Error backfilling" with entity IDs</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Option 1: Automatic backfill on startup (via configuration)
 * // application.yml:
 * // hephaestus.activity.backfill-on-startup: true
 *
 * // Option 2: Manual backfill for a specific workspace
 * BackfillProgress progress = backfillService.backfillWorkspace(workspaceId);
 * log.info(progress.summary());
 * if (progress.failed() > 0) {
 *     log.warn("Some entities failed - check logs for details");
 * }
 *
 * // Option 3: Backfill specific event types (useful for partial re-runs)
 * backfillService.backfillPullRequests(workspaceId);
 * backfillService.backfillReviews(workspaceId);
 * backfillService.backfillIssueComments(workspaceId);
 * backfillService.backfillReviewComments(workspaceId);
 * backfillService.backfillIssues(workspaceId);
 *
 * // Estimate work before starting (useful for progress bars)
 * BackfillEstimate estimate = backfillService.estimateBackfill(workspaceId);
 * log.info(estimate.summary());
 * }</pre>
 *
 * @see de.tum.in.www1.hephaestus.activity.ActivityEventService The underlying event recording service
 * @see de.tum.in.www1.hephaestus.activity Activity module documentation
 */
package de.tum.in.www1.hephaestus.activity.backfill;
