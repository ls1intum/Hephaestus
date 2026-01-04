/**
 * Activity Event Backfill - populates historical activity events from existing entities.
 *
 * <p><strong>DEPRECATION NOTICE:</strong> This entire package is temporary and will be removed
 * once all production environments have been migrated. It exists solely to backfill
 * historical activity events during the transition from the old XP calculation system
 * to the new activity event ledger. Target removal: After all workspaces have been backfilled
 * in production.
 *
 * <h2>Purpose</h2>
 * <p>This package provides services for backfilling the activity event ledger with historical
 * data for entities that existed before the activity tracking system was implemented.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link de.tum.in.www1.hephaestus.activity.backfill.ActivityEventBackfillService} -
 *       Main service orchestrating backfill operations</li>
 *   <li>{@link de.tum.in.www1.hephaestus.activity.backfill.BackfillProgress} -
 *       Progress tracking record for monitoring backfill operations</li>
 * </ul>
 *
 * <h2>Supported Event Types</h2>
 * <ul>
 *   <li><strong>Pull Requests:</strong> OPENED, MERGED, CLOSED</li>
 *   <li><strong>Reviews:</strong> APPROVED, CHANGES_REQUESTED, COMMENTED, UNKNOWN, DISMISSED</li>
 *   <li><strong>Comments:</strong> COMMENT_CREATED (issue comments), REVIEW_COMMENT_CREATED</li>
 *   <li><strong>Issues:</strong> CREATED, CLOSED</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 * <p>All backfill operations are idempotent. Running them multiple times will not create
 * duplicate events because the underlying event recording relies on a unique constraint
 * on the event_key (type:targetId:timestamp).
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>Entities are processed in batches (default: 500) to avoid memory issues</li>
 *   <li>Each batch runs in its own transaction to prevent long-running transactions</li>
 *   <li>EntityManager is cleared after each batch to prevent Hibernate session bloat</li>
 *   <li>Progress is tracked and logged for monitoring large backfill operations</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Backfill all events for a workspace
 * BackfillProgress progress = backfillService.backfillWorkspace(workspaceId);
 * log.info(progress.summary());
 *
 * // Or backfill specific event types
 * backfillService.backfillPullRequests(workspaceId);
 * backfillService.backfillReviews(workspaceId);
 * backfillService.backfillIssueComments(workspaceId);
 * backfillService.backfillReviewComments(workspaceId);
 * backfillService.backfillIssues(workspaceId);
 *
 * // Estimate work before starting
 * BackfillEstimate estimate = backfillService.estimateBackfill(workspaceId);
 * log.info(estimate.summary());
 * }</pre>
 *
 * @see de.tum.in.www1.hephaestus.activity.ActivityEventService The underlying event recording service
 * @deprecated Temporary migration package - remove after production migration is complete
 */
package de.tum.in.www1.hephaestus.activity.backfill;
