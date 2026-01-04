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
 * <h3>Usage</h3>
 * <p>Add to your application-local.yml:
 * <pre>{@code
 * hephaestus:
 *   activity:
 *     backfill-on-startup: true
 * }</pre>
 *
 * <h3>Idempotency</h3>
 * <p>All backfill operations are idempotent - running them multiple times will not create
 * duplicate events due to unique constraints on event keys.
 *
 * <p><strong>DEPRECATION NOTICE:</strong> This component is temporary and will be removed
 * once all production environments have been migrated. It exists solely to backfill
 * historical activity events during the transition from the old XP calculation system
 * to the new activity event ledger.
 *
 * <p>Target removal: After all workspaces have been backfilled in production.
 *
 * @see ActivityEventBackfillService
 * @deprecated Temporary migration component - remove after production migration is complete
 */
@Deprecated(forRemoval = true)
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
        log.info("=== Starting activity event backfill on startup ===");

        // Get all non-purged workspaces
        List<Workspace> workspaces = workspaceRepository.findByStatusNot(Workspace.WorkspaceStatus.PURGED);

        if (workspaces.isEmpty()) {
            log.warn("No active workspaces found for backfill");
            return;
        }

        log.info("Found {} workspaces to backfill", workspaces.size());

        for (Workspace workspace : workspaces) {
            try {
                log.info(
                    "Starting backfill for workspace: {} (id={}, status={})",
                    workspace.getWorkspaceSlug(),
                    workspace.getId(),
                    workspace.getStatus()
                );

                // Get estimate first
                ActivityEventBackfillService.BackfillEstimate estimate = backfillService.estimateBackfill(
                    workspace.getId()
                );
                log.info("Backfill estimate for {}: {}", workspace.getWorkspaceSlug(), estimate.summary());

                // Run the backfill
                BackfillProgress progress = backfillService.backfillWorkspace(workspace.getId());

                log.info("Completed backfill for workspace {}: {}", workspace.getWorkspaceSlug(), progress.summary());
            } catch (Exception e) {
                log.error(
                    "Failed to backfill workspace {} (id={}): {}",
                    workspace.getWorkspaceSlug(),
                    workspace.getId(),
                    e.getMessage(),
                    e
                );
            }
        }

        log.info("=== Activity event backfill on startup complete ===");
    }
}
