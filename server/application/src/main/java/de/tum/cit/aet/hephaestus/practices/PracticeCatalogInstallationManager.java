package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogLock;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeReleaseService;
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
    private final PracticeGroupService groupService;
    private final PracticeService practiceService;
    private final PracticeGroupRepository groupRepository;
    private final PracticeRepository practiceRepository;
    private final CuratedCatalogService catalogService;
    private final CuratedCatalogLock catalogLock;
    private final PracticeCatalogInstallationRepository installationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TransactionOperations transactionOperations;
    private final Clock clock;
    private final PracticeReleaseService releaseService;
    private final CuratedPracticeReleaseService instanceReleases;

    PracticeCatalogInstallationManager(
            @Value("${hephaestus.practices.seed-default-catalog:true}") boolean enabled,
            PracticeGroupService groupService,
            PracticeService practiceService,
            PracticeGroupRepository groupRepository,
            PracticeRepository practiceRepository,
            CuratedCatalogService catalogService,
            CuratedCatalogLock catalogLock,
            PracticeCatalogInstallationRepository installationRepository,
            WorkspaceRepository workspaceRepository,
            TransactionOperations transactionOperations,
            Clock clock,
            PracticeReleaseService releaseService,
            CuratedPracticeReleaseService instanceReleases) {
        this.enabled = enabled;
        this.groupService = groupService;
        this.practiceService = practiceService;
        this.groupRepository = groupRepository;
        this.practiceRepository = practiceRepository;
        this.catalogService = catalogService;
        this.catalogLock = catalogLock;
        this.installationRepository = installationRepository;
        this.workspaceRepository = workspaceRepository;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
        this.releaseService = releaseService;
        this.instanceReleases = instanceReleases;
    }

    @EventListener(WorkspacesInitializedEvent.class)
    public void repairIncompleteInstallations() {
        if (!enabled) {
            return;
        }
        try {
            workspaceRepository.findAll().stream()
                    .sorted(Comparator.comparing(Workspace::getId, Comparator.nullsLast(Long::compareTo)))
                    .forEach(this::repairCatalogSafely);
        } catch (RuntimeException exception) {
            log.error("Could not load workspaces for default practice catalog installation", exception);
        }
        upgradePracticeScripts();
    }

    /**
     * The one-shot practice program upgrade. Each copy of a bundled practice script that nobody changed takes the
     * program the catalog now ships, first at the instance, then in each workspace not yet upgraded, one transaction
     * per workspace; the workspace is then marked, so the step runs once. A copy that was changed keeps its script and
     * its normal offer: a script of the retired leads contract then fails its practice at run time with a clear
     * error, until an admin accepts the offer or rewrites it.
     */
    void upgradePracticeScripts() {
        List<Long> pending;
        try {
            pending = installationRepository.findWorkspaceIdsAwaitingPracticeScriptUpgrade();
        } catch (RuntimeException exception) {
            log.error("Could not load workspaces for the practice script upgrade", exception);
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        try {
            int adopted = instanceReleases.adoptShippedPracticeScripts();
            if (adopted > 0) {
                log.info("Moved {} unchanged instance practice scripts to the shipped programs", adopted);
            }
        } catch (RuntimeException exception) {
            // Marked against the old instance copies, a workspace copy that follows one would never move.
            log.error("Instance practice script upgrade failed; the workspaces wait for the next start", exception);
            return;
        }
        for (Long workspaceId : pending) {
            try {
                transactionOperations.executeWithoutResult(ignored -> upgradePracticeScripts(workspaceId));
            } catch (RuntimeException exception) {
                log.error("Practice script upgrade failed: workspaceId={}", workspaceId, exception);
            }
        }
    }

    private void upgradePracticeScripts(Long workspaceId) {
        PracticeCatalogInstallation installation =
                installationRepository.findByWorkspaceIdForUpdate(workspaceId).orElse(null);
        if (installation == null || installation.getPracticeScriptsUpgradedAt() != null) {
            return;
        }
        int adopted = 0;
        for (String slug : releaseService.proposedSlugs(workspaceId)) {
            if (releaseService.adoptOfferedPracticeScript(workspaceId, slug)) adopted++;
        }
        installation.markPracticeScriptsUpgraded(clock.instant());
        installationRepository.save(installation);
        if (adopted > 0) {
            log.info(
                    "Moved {} unchanged practice scripts to the shipped programs: workspaceId={}",
                    adopted,
                    workspaceId);
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
        PracticeCatalogInstallation installation = new PracticeCatalogInstallation(workspaceId, now, now);
        // Installed from the catalog as it ships now: nothing to upgrade.
        installation.markPracticeScriptsUpgraded(now);
        installationRepository.save(installation);
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
        Workspace lockedWorkspace =
                workspaceRepository.findByIdForUpdate(workspace.getId()).orElse(null);
        if (lockedWorkspace == null || installationRepository.existsById(lockedWorkspace.getId())) {
            return;
        }
        WorkspaceContext context = WorkspaceContext.fromWorkspace(lockedWorkspace, Set.of(), null);
        EffectiveCatalog catalog = catalogService.catalog();
        List<CatalogEntry<GroupDefinition>> groups = catalog.installableGroups();
        int seededGroups = 0;
        for (CatalogEntry<GroupDefinition> group : groups) {
            if (!groupRepository.existsByWorkspaceIdAndSlug(context.id(), group.slug())) {
                groupService.createGroupFromCatalog(context, group.slug(), group.effective(), group.position());
                seededGroups++;
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
        PracticeCatalogInstallation installation = new PracticeCatalogInstallation(lockedWorkspace.getId(), now, now);
        // A repair can only seed what is missing; copies installed earlier are upgraded by upgradePracticeScripts.
        if (seededPractices == catalog.installablePractices().size()) {
            installation.markPracticeScriptsUpgraded(now);
        }
        installationRepository.save(installation);
        if (seededPractices > 0) {
            log.info(
                    "Seeded practice catalog: {} groups, {} practices into workspace {}",
                    seededGroups,
                    seededPractices,
                    lockedWorkspace.getId());
        }
    }
}
