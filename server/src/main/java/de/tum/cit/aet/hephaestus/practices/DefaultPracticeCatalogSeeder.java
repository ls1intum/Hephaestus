package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPractice;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeArea;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.events.WorkspaceCreatedEvent;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

@Component
class DefaultPracticeCatalogSeeder {

    private static final Logger log = LoggerFactory.getLogger(DefaultPracticeCatalogSeeder.class);

    private final boolean enabled;
    private final PracticeAreaService areaService;
    private final PracticeService practiceService;
    private final PracticeAreaRepository areaRepository;
    private final PracticeRepository practiceRepository;
    private final CuratedPracticeRepository curatedPracticeRepository;
    private final CuratedPracticeAreaRepository curatedAreaRepository;
    private final PracticeCatalogInstallationRepository installationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AsyncTaskExecutor taskExecutor;
    private final TransactionOperations transactionOperations;

    DefaultPracticeCatalogSeeder(
        @Value("${hephaestus.practices.seed-default-catalog:true}") boolean enabled,
        PracticeAreaService areaService,
        PracticeService practiceService,
        PracticeAreaRepository areaRepository,
        PracticeRepository practiceRepository,
        CuratedPracticeRepository curatedPracticeRepository,
        CuratedPracticeAreaRepository curatedAreaRepository,
        PracticeCatalogInstallationRepository installationRepository,
        WorkspaceRepository workspaceRepository,
        @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) AsyncTaskExecutor taskExecutor,
        TransactionOperations transactionOperations
    ) {
        this.enabled = enabled;
        this.areaService = areaService;
        this.practiceService = practiceService;
        this.areaRepository = areaRepository;
        this.practiceRepository = practiceRepository;
        this.curatedPracticeRepository = curatedPracticeRepository;
        this.curatedAreaRepository = curatedAreaRepository;
        this.installationRepository = installationRepository;
        this.workspaceRepository = workspaceRepository;
        this.taskExecutor = taskExecutor;
        this.transactionOperations = transactionOperations;
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
        Map<String, CuratedPracticeArea> curatedAreas = curatedAreaRepository
            .findAllByOrderByDisplayOrderAscNameAsc()
            .stream()
            .collect(Collectors.toMap(CuratedPracticeArea::getSlug, Function.identity()));
        int seededAreas = 0;
        int seededPractices = 0;
        for (CuratedPractice curated : curatedPracticeRepository.findInstallableBundledPractices()) {
            String areaSlug = curated.getCurrentRevision().getAreaSlug();
            if (
                areaSlug != null &&
                !areaRepository.existsByWorkspaceIdAndSlug(context.id(), areaSlug) &&
                createArea(context, curatedAreas.get(areaSlug))
            ) {
                seededAreas++;
            }
            if (!practiceRepository.existsByWorkspaceIdAndSlug(context.id(), curated.getSlug())) {
                practiceService.createPracticeFromCurated(
                    context,
                    curated.getSlug(),
                    PracticeDefinition.from(curated.getCurrentRevision()),
                    curated,
                    curated.getCurrentRevision()
                );
                seededPractices++;
            }
        }
        installationRepository.save(new PracticeCatalogInstallation(lockedWorkspace.getId()));
        if (seededAreas > 0 || seededPractices > 0) {
            log.info(
                "Seeded default practice catalog: {} areas, {} practices into workspace {}",
                seededAreas,
                seededPractices,
                lockedWorkspace.getId()
            );
        }
    }

    private boolean createArea(WorkspaceContext context, CuratedPracticeArea area) {
        if (area == null) {
            throw new IllegalStateException("Bundled practice references an unknown curated area");
        }
        areaService.createArea(
            context,
            area.getSlug(),
            new AreaAttributes(
                area.getName(),
                area.getDescription(),
                area.getDisplayOrder(),
                area.getIcon(),
                area.getColor()
            )
        );
        return true;
    }
}
