package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogLock;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

@Component
@ConditionalOnServerRole
class PracticeCatalogInstallationManager {

    private static final Logger log = LoggerFactory.getLogger(PracticeCatalogInstallationManager.class);

    private final boolean enabled;
    private final PracticeAreaService areaService;
    private final PracticeService practiceService;
    private final PracticeAreaRepository areaRepository;
    private final PracticeRepository practiceRepository;
    private final CuratedCatalogService catalogService;
    private final CuratedCatalogLock catalogLock;
    private final PracticeCatalogInstallationRepository installationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    PracticeCatalogInstallationManager(
        @Value("${hephaestus.practices.seed-default-catalog:true}") boolean enabled,
        PracticeAreaService areaService,
        PracticeService practiceService,
        PracticeAreaRepository areaRepository,
        PracticeRepository practiceRepository,
        CuratedCatalogService catalogService,
        CuratedCatalogLock catalogLock,
        PracticeCatalogInstallationRepository installationRepository,
        WorkspaceRepository workspaceRepository,
        TransactionOperations transactionOperations,
        Clock clock
    ) {
        this.enabled = enabled;
        this.areaService = areaService;
        this.practiceService = practiceService;
        this.areaRepository = areaRepository;
        this.practiceRepository = practiceRepository;
        this.catalogService = catalogService;
        this.catalogLock = catalogLock;
        this.installationRepository = installationRepository;
        this.workspaceRepository = workspaceRepository;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
    }

    @EventListener(WorkspacesInitializedEvent.class)
    public void repairIncompleteInstallations() {
        if (!enabled) {
            return;
        }
        try {
            workspaceRepository
                .findAll()
                .stream()
                .sorted(Comparator.comparing(Workspace::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(this::repairCatalogSafely);
        } catch (RuntimeException exception) {
            log.error("Could not load workspaces for default practice catalog installation", exception);
        }
    }

    @EventListener(WorkspaceCreatedEvent.class)
    public void onWorkspaceCreated(WorkspaceCreatedEvent event) {
        transactionOperations.executeWithoutResult(ignored -> markCatalogReady(event.workspaceId()));
    }

    private void markCatalogReady(Long workspaceId) {
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId).orElse(null);
        if (workspace == null || installationRepository.existsById(workspaceId)) {
            return;
        }
        Instant now = clock.instant();
        installationRepository.save(new PracticeCatalogInstallation(workspaceId, now, now));
    }

    private void repairCatalogSafely(Workspace workspace) {
        try {
            transactionOperations.executeWithoutResult(ignored -> repairCatalog(workspace));
        } catch (RuntimeException exception) {
            log.error("Default practice catalog repair failed: workspaceId={}", workspace.getId(), exception);
        }
    }

    private void repairCatalog(Workspace workspace) {
        catalogLock.acquire();
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
