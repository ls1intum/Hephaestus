package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.events.WorkspaceCreatedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Gives a workspace the instance's catalog, once.
 *
 * <p>What it copies is the catalog as this instance has curated it — an administrator's own practice
 * counts exactly as much as one the build shipped, and an administrator's edit to a shipped practice
 * is what the workspace receives. What it does not do is keep copying: a workspace's practices are
 * its own from the moment it has them, and later catalog changes never rewrite them.
 */
@Component
@ConditionalOnServerRole
class DefaultPracticeCatalogSeeder {

    private static final Logger log = LoggerFactory.getLogger(DefaultPracticeCatalogSeeder.class);

    private final boolean enabled;
    private final PracticeAreaService areaService;
    private final PracticeService practiceService;
    private final PracticeAreaRepository areaRepository;
    private final PracticeRepository practiceRepository;
    private final CuratedCatalogService catalogService;
    private final PracticeCatalogInstallationRepository installationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AsyncTaskExecutor taskExecutor;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    DefaultPracticeCatalogSeeder(
        @Value("${hephaestus.practices.seed-default-catalog:true}") boolean enabled,
        PracticeAreaService areaService,
        PracticeService practiceService,
        PracticeAreaRepository areaRepository,
        PracticeRepository practiceRepository,
        CuratedCatalogService catalogService,
        PracticeCatalogInstallationRepository installationRepository,
        WorkspaceRepository workspaceRepository,
        @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) AsyncTaskExecutor taskExecutor,
        TransactionOperations transactionOperations,
        Clock clock
    ) {
        this.enabled = enabled;
        this.areaService = areaService;
        this.practiceService = practiceService;
        this.areaRepository = areaRepository;
        this.practiceRepository = practiceRepository;
        this.catalogService = catalogService;
        this.installationRepository = installationRepository;
        this.workspaceRepository = workspaceRepository;
        this.taskExecutor = taskExecutor;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
    }

    @EventListener(WorkspacesInitializedEvent.class)
    public void seed() {
        if (!enabled) {
            return;
        }
        try {
            workspaceRepository
                .findAll()
                .stream()
                .sorted(Comparator.comparing(Workspace::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(this::seedCatalogSafely);
        } catch (RuntimeException exception) {
            log.error("Could not load workspaces for default practice catalog installation", exception);
        }
    }

    @EventListener(WorkspaceCreatedEvent.class)
    public void onWorkspaceCreated(WorkspaceCreatedEvent event) {
        if (!enabled) {
            return;
        }
        try {
            taskExecutor.execute(() -> {
                try {
                    workspaceRepository.findById(event.workspaceId()).ifPresent(this::seedCatalogSafely);
                } catch (RuntimeException exception) {
                    log.error(
                        "Could not load workspace {} for default practice catalog seeding",
                        event.workspaceId(),
                        exception
                    );
                }
            });
        } catch (RuntimeException exception) {
            log.error(
                "Could not schedule default practice catalog seeding: workspaceId={}",
                event.workspaceId(),
                exception
            );
        }
    }

    private void seedCatalogSafely(Workspace workspace) {
        try {
            transactionOperations.executeWithoutResult(ignored -> seedCatalog(workspace));
        } catch (RuntimeException exception) {
            log.error("Default practice catalog seeding failed: workspaceId={}", workspace.getId(), exception);
        }
    }

    private void seedCatalog(Workspace workspace) {
        Workspace lockedWorkspace = workspaceRepository.findByIdForUpdate(workspace.getId()).orElse(null);
        if (lockedWorkspace == null || installationRepository.existsById(lockedWorkspace.getId())) {
            return;
        }
        WorkspaceContext context = WorkspaceContext.fromWorkspace(lockedWorkspace, Set.of(), null);
        EffectiveCatalog catalog = catalogService.catalog();
        List<CatalogEntry<AreaDefinition>> areas = catalog.installableAreas();
        int seededAreas = 0;
        for (CatalogEntry<AreaDefinition> area : areas) {
            if (!areaRepository.existsByWorkspaceIdAndSlug(context.id(), area.slug())) {
                areaService.createAreaFromCatalog(context, area.slug(), area.effective(), area.position());
                seededAreas++;
            }
        }
        int seededPractices = 0;
        for (CatalogEntry<PracticeDefinition> entry : catalog.installablePractices()) {
            if (!practiceRepository.existsByWorkspaceIdAndSlug(context.id(), entry.slug())) {
                practiceService.createPracticeFromCatalog(context, entry.slug(), entry.effective());
                seededPractices++;
            }
        }
        Instant now = clock.instant();
        // Linked at birth, so the one-time backfill for older workspaces never has to look at this one.
        installationRepository.save(new PracticeCatalogInstallation(lockedWorkspace.getId(), now, now));
        if (seededPractices > 0) {
            log.info(
                "Seeded practice catalog: {} areas, {} practices into workspace {}",
                seededAreas,
                seededPractices,
                lockedWorkspace.getId()
            );
        }
    }
}
