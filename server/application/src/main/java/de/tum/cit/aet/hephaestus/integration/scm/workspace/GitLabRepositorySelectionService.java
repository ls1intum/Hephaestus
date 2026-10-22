package de.tum.cit.aet.hephaestus.integration.scm.workspace;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.event.RepositoryMonitoringChangedEvent;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionAudit;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionAuditRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.RepositoryIdentityLookup;
import de.tum.cit.aet.hephaestus.workspace.RepositorySelection;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import io.micrometer.common.util.StringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Reads and changes whether a GitLab workspace follows every project of its group or only the ones it monitors. */
@Service
@WorkspaceAgnostic("Manages workspace-repository mapping configuration")
public class GitLabRepositorySelectionService {

    @PersistenceContext
    private @Nullable EntityManager entityManager;

    private static final Logger log = LoggerFactory.getLogger(GitLabRepositorySelectionService.class);

    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final ConnectionRepository connectionRepository;
    private final ConnectionAuditRepository auditRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Map<IntegrationKind, RepositoryIdentityLookup> repositoryIdentityLookups;

    public GitLabRepositorySelectionService(
            WorkspaceRepository workspaceRepository,
            RepositoryToMonitorRepository repositoryToMonitorRepository,
            ConnectionRepository connectionRepository,
            ConnectionAuditRepository auditRepository,
            ApplicationEventPublisher eventPublisher,
            TransactionTemplate transactionTemplate,
            List<RepositoryIdentityLookup> repositoryIdentityLookupList) {
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.connectionRepository = connectionRepository;
        this.auditRepository = auditRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        Map<IntegrationKind, RepositoryIdentityLookup> identityLookupMap = new EnumMap<>(IntegrationKind.class);
        for (RepositoryIdentityLookup lookup : repositoryIdentityLookupList) {
            identityLookupMap.put(lookup.kind(), lookup);
        }
        this.repositoryIdentityLookups = identityLookupMap;
    }

    /** Whether the workspace's GitLab connection follows every project of its group, as stored now. */
    @Transactional(readOnly = true)
    public RepositorySelection getGitLabRepositorySelection(WorkspaceContext workspaceContext) {
        String slug = requireSlug(workspaceContext);
        Long workspaceId = requireWorkspace(slug).getId();
        if (connectionRepository
                .findActive(workspaceId, IntegrationKind.GITLAB)
                .filter(connection -> connection.getConfig() instanceof ConnectionConfig.GitLabConfig)
                .isEmpty()) {
            throw gitLabNotActive(slug);
        }
        return storedSelection(workspaceId);
    }

    /**
     * Narrowing keeps the current monitors as the selection. Widening keeps every explicit stop, so only Add resumes a
     * stopped project. Neither changes memberships or stored content. A monitor that knows only its path is confirmed
     * with GitLab before the write transaction opens; the write refuses when the connection, the group or those
     * monitors changed in the meantime, and then changes nothing.
     */
    public RepositorySelection updateGitLabRepositorySelection(
            WorkspaceContext workspaceContext, RepositorySelection requested, long actorAccountId) {
        String slug = requireSlug(workspaceContext);
        Long workspaceId = requireWorkspace(slug).getId();
        Connection connection = connectionRepository
                .findActive(workspaceId, IntegrationKind.GITLAB)
                .orElseThrow(() -> gitLabNotActive(slug));
        if (!(connection.getConfig() instanceof ConnectionConfig.GitLabConfig initialConfig)) {
            throw gitLabNotActive(slug);
        }
        long connectionId = Objects.requireNonNull(connection.getId());
        Long initialVersion = connection.getVersion();
        String group = workspaceRepository
                .findMonitorPolicy(workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Workspace", slug))
                .getAccountLogin();

        // A selected workspace admits a project only by the id its monitor carries, so a narrowing first asks GitLab
        // for the id of each monitor that has none. A mirror with the same path may be a project another workspace
        // recorded, so it names nothing here.
        Map<Long, String> unresolvedBefore = new HashMap<>();
        Map<Long, Long> confirmedNativeIds = new HashMap<>();
        if (requested == RepositorySelection.SELECTED && storedSelection(workspaceId) != RepositorySelection.SELECTED) {
            for (RepositoryToMonitor monitor : repositoryToMonitorRepository.findByWorkspaceId(workspaceId)) {
                if (monitor.getNativeId() == null) {
                    unresolvedBefore.put(monitor.getId(), monitor.getNameWithOwner());
                }
            }
            if (!unresolvedBefore.isEmpty()) {
                RepositoryIdentityLookup lookup = repositoryIdentityLookups.get(IntegrationKind.GITLAB);
                if (lookup == null) throw gitLabNotActive(slug);
                for (Map.Entry<Long, String> unresolved : unresolvedBefore.entrySet()) {
                    String path = unresolved.getValue();
                    long nativeId = lookup.resolve(workspaceId, path)
                            .filter(reported ->
                                    path.equalsIgnoreCase(reported.fullPath()) && inGroup(group, reported.fullPath()))
                            .orElseThrow(() -> projectNotConfirmed(path))
                            .nativeId();
                    confirmedNativeIds.put(unresolved.getKey(), nativeId);
                }
            }
        }

        RepositorySelection selection = transactionTemplate.execute(status -> {
            EntityManager em = Objects.requireNonNull(entityManager);
            connectionRepository.acquireLifecycleLock(connectionId, workspaceId);
            // Read after the lock; an entity this persistence context loaded earlier may predate a disconnect.
            Connection current = connectionRepository
                    .findByIdAndWorkspaceId(connectionId, workspaceId)
                    .orElseThrow(() -> gitLabNotActive(slug));
            em.refresh(current);
            if (current.getState() != IntegrationState.ACTIVE
                    || !(current.getConfig() instanceof ConnectionConfig.GitLabConfig currentConfig)
                    || !Objects.equals(currentConfig.serverUrl(), initialConfig.serverUrl())
                    || connectionRepository
                            .findActive(workspaceId, IntegrationKind.GITLAB)
                            .filter(active -> Objects.equals(active.getId(), connectionId))
                            .isEmpty()) {
                throw gitLabNotActive(slug);
            }
            RepositorySelection stored = storedSelection(workspaceId);
            if (stored == requested) {
                return stored;
            }
            // A reconnect revives the same row, so only its version shows that GitLab answered for an earlier
            // connection.
            if (!Objects.equals(current.getVersion(), initialVersion)
                    || workspaceRepository
                            .findMonitorPolicy(workspaceId)
                            .filter(policy -> policy.getAccountLogin().equals(group))
                            .isEmpty()) {
                throw new RepositorySelectionChangedException();
            }
            if (requested == RepositorySelection.SELECTED) {
                List<RepositoryToMonitor> monitors = repositoryToMonitorRepository.findByWorkspaceId(workspaceId);
                Map<Long, String> unresolvedNow = new HashMap<>();
                Set<Long> heldNativeIds = new HashSet<>();
                for (RepositoryToMonitor monitor : monitors) {
                    em.refresh(monitor);
                    Long nativeId = monitor.getNativeId();
                    if (nativeId == null) {
                        unresolvedNow.put(monitor.getId(), monitor.getNameWithOwner());
                    } else {
                        heldNativeIds.add(nativeId);
                    }
                }
                // A Stop, an Add or a discovery may have changed the monitors GitLab was asked about.
                if (!unresolvedNow.equals(unresolvedBefore)) {
                    throw new RepositorySelectionChangedException();
                }
                for (RepositoryToMonitor monitor : monitors) {
                    Long nativeId = confirmedNativeIds.get(monitor.getId());
                    if (nativeId == null) continue;
                    if (!heldNativeIds.add(nativeId)) {
                        throw projectMonitoredTwice(monitor.getNameWithOwner());
                    }
                    monitor.setNativeId(nativeId);
                    repositoryToMonitorRepository.saveAndFlush(monitor);
                    eventPublisher.publishEvent(new RepositoryMonitoringChangedEvent(
                            workspaceId,
                            repositoryToMonitorRepository.findSourceRepositoryIdsOfMonitor(monitor.getId())));
                }
            }
            Workspace workspace = workspaceRepository
                    .findById(workspaceId)
                    .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId));
            em.refresh(workspace);
            workspace.setRepositorySelection(requested);
            workspaceRepository.saveAndFlush(workspace);
            recordChange(current, stored, requested, actorAccountId);
            log.info(
                    "Changed GitLab project selection: workspaceId={}, from={}, to={}, resolvedMonitors={}",
                    workspaceId,
                    stored,
                    requested,
                    confirmedNativeIds.size());
            return requested;
        });
        return Objects.requireNonNull(selection);
    }

    /** A workspace without a stored choice follows every project, as discovery and admission already treat it. */
    RepositorySelection storedSelection(long workspaceId) {
        return workspaceRepository
                        .findMonitorPolicy(workspaceId)
                        .filter(policy -> policy.getRepositorySelection() == RepositorySelection.SELECTED)
                        .isPresent()
                ? RepositorySelection.SELECTED
                : RepositorySelection.ALL;
    }

    /** Written in the caller's transaction, under the connection's lifecycle lock, beside the change it records. */
    private void recordChange(
            Connection connection, RepositorySelection from, RepositorySelection to, long actorAccountId) {
        ConnectionAudit audit = new ConnectionAudit(
                connection,
                "PROJECT_SELECTION_CHANGED",
                connection.getState(),
                connection.getState(),
                "ADMIN",
                null,
                // The idempotency index treats an absent correlation id as one value, so each change carries its own.
                UUID.randomUUID().toString(),
                from + " → " + to);
        audit.setActorAccountId(actorAccountId);
        auditRepository.save(audit);
    }

    private Workspace requireWorkspace(String slug) {
        if (StringUtils.isBlank(slug)) {
            throw new IllegalArgumentException("Workspace slug must not be blank.");
        }
        return workspaceRepository
                .findByWorkspaceSlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));
    }

    private static String requireSlug(WorkspaceContext workspaceContext) {
        Objects.requireNonNull(workspaceContext, "WorkspaceContext must not be null");
        String slug = workspaceContext.slug();
        if (StringUtils.isBlank(slug)) {
            throw new IllegalArgumentException("Workspace context slug must not be blank.");
        }
        return slug;
    }

    private static boolean inGroup(String group, String path) {
        return path.toLowerCase(Locale.ROOT).startsWith(group.toLowerCase(Locale.ROOT) + "/");
    }

    private static ResponseStatusException gitLabNotActive(String slug) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                LoggingUtils.sanitizeForLog("The workspace “" + slug + "” has no active GitLab connection."
                        + " Only a connected GitLab workspace chooses which projects it follows."));
    }

    private static ResponseStatusException projectNotConfirmed(String path) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                LoggingUtils.sanitizeForLog("GitLab did not confirm the project “" + path + "” in this group."
                        + " Stop monitoring it, or try again when GitLab shows it."));
    }

    private static ResponseStatusException projectMonitoredTwice(String path) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                LoggingUtils.sanitizeForLog("GitLab reports “" + path + "” as a project this workspace already"
                        + " monitors under another path. Stop one of them, then try again."));
    }
}
