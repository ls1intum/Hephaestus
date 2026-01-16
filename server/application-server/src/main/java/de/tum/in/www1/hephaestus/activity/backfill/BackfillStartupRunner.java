package de.tum.in.www1.hephaestus.activity.backfill;

import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Startup runner that triggers activity event backfill for all active workspaces.
 *
 * <p>This runner is conditionally enabled via the {@code hephaestus.activity.backfill-on-startup}
 * property. When enabled, it runs after the application context is fully initialized and triggers
 * backfill for all workspaces that are not in PURGED status.
 *
 * <h3>Configuration</h3>
 * <p>Add to your application-local.yml to enable automatic backfill on startup:
 * <pre>{@code
 * hephaestus:
 *   activity:
 *     backfill-on-startup: true
 * }</pre>
 *
 * <h3>Behavior</h3>
 * <ul>
 *   <li><strong>Scope</strong>: Processes all non-PURGED workspaces sequentially</li>
 *   <li><strong>Idempotency</strong>: Safe to run multiple times - duplicate events are
 *       automatically skipped via unique constraints</li>
 *   <li><strong>Error Handling</strong>: Workspace failures are logged but do not abort
 *       the overall backfill - subsequent workspaces continue processing</li>
 *   <li><strong>Progress</strong>: Logs estimate before and progress after each workspace</li>
 * </ul>
 *
 * <h3>Error Recovery</h3>
 * <p>If a workspace fails:
 * <ol>
 *   <li>Error is logged with workspace ID and exception details</li>
 *   <li>Processing continues to next workspace</li>
 *   <li>Re-running the application will retry failed workspaces</li>
 *   <li>Successfully backfilled events are preserved (idempotent)</li>
 * </ol>
 *
 * <h3>Monitoring</h3>
 * <p>Watch for these log patterns:
 * <ul>
 *   <li>{@code "Starting backfill for workspace"} - Workspace processing begins</li>
 *   <li>{@code "Completed backfill for workspace"} - Successful completion with stats</li>
 *   <li>{@code "Failed to backfill workspace"} - Error occurred (check exception)</li>
 *   <li>{@code "Activity event backfill on startup complete"} - All workspaces done</li>
 * </ul>
 *
 * @see ActivityEventBackfillService The underlying backfill implementation
 * @see BackfillProgress Progress tracking for monitoring
 */
@Component
@ConditionalOnProperty(name = "hephaestus.activity.backfill-on-startup", havingValue = "true")
public class BackfillStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillStartupRunner.class);

    private final ActivityEventBackfillService backfillService;
    private final WorkspaceRepository workspaceRepository;

    public BackfillStartupRunner(
        ActivityEventBackfillService backfillService,
        WorkspaceRepository workspaceRepository
    ) {
        this.backfillService = backfillService;
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting activity event backfill on startup");

        // Get all non-purged workspaces
        List<Workspace> workspaces = workspaceRepository.findByStatusNot(Workspace.WorkspaceStatus.PURGED);

        if (workspaces.isEmpty()) {
            log.warn("Skipped backfill, no active workspaces found");
            return;
        }

        log.info("Found workspaces for backfill: count={}", workspaces.size());

        for (Workspace workspace : workspaces) {
            try {
                log.info(
                    "Starting backfill for workspace: scopeSlug={}, scopeId={}, status={}",
                    workspace.getWorkspaceSlug(),
                    workspace.getId(),
                    workspace.getStatus()
                );

                // Get estimate first
                ActivityEventBackfillService.BackfillEstimate estimate = backfillService.estimateBackfill(
                    workspace.getId()
                );
                log.info(
                    "Estimated backfill for workspace: scopeSlug={}, estimate={}",
                    workspace.getWorkspaceSlug(),
                    estimate.summary()
                );

                // Run the backfill
                BackfillProgress progress = backfillService.backfillWorkspace(workspace.getId());

                log.info(
                    "Completed backfill for workspace: scopeSlug={}, progress={}",
                    workspace.getWorkspaceSlug(),
                    progress.summary()
                );
            } catch (Exception e) {
                log.error(
                    "Failed to backfill workspace: scopeSlug={}, scopeId={}",
                    workspace.getWorkspaceSlug(),
                    workspace.getId(),
                    e
                );
            }
        }

        log.info("Completed activity event backfill on startup: workspacesProcessed={}", workspaces.size());
    }
}
