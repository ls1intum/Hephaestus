package de.tum.cit.aet.hephaestus.integration.outline.collection;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.connect.OutlineConnectionResolver;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.SyncStatus;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineSyncDispatch;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin control plane for the mirrored-collection registry. Owns every repository touch for the
 * collection admin surface (the controller stays thin per the {@code controllersDoNotAccessRepositories}
 * arch rule) and resolves the workspace's ACTIVE Outline connection up front so every operation 404s
 * cleanly when the integration is not connected.
 *
 * <p>Deliberately not transactional around live Outline calls so no pooled connection is held across
 * the wire.
 */
@Service
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineCollectionAdminService {

    private static final Logger log = LoggerFactory.getLogger(OutlineCollectionAdminService.class);

    /**
     * Page budget for the interactive candidates proxy (5 pages × 100 = 500 collections). The sync
     * path keeps the client's large safety cap; this endpoint answers an admin picker on the request
     * thread, so it must stay bounded — the client logs when the cap truncates the live list.
     */
    private static final int CANDIDATES_MAX_PAGES = 5;

    /** Matches {@code outline_collection.description}'s column width. */
    private static final int MAX_DESCRIPTION_LENGTH = 2048;

    private final ConnectionService connectionService;
    private final OutlineCollectionRepository collectionRepository;
    private final OutlineDocumentRepository documentRepository;
    private final OutlineApiClient outlineApiClient;
    private final OutlineDocumentSyncScheduler syncScheduler;
    private final AsyncTaskExecutor taskExecutor;

    public OutlineCollectionAdminService(
        ConnectionService connectionService,
        OutlineCollectionRepository collectionRepository,
        OutlineDocumentRepository documentRepository,
        OutlineApiClient outlineApiClient,
        OutlineDocumentSyncScheduler syncScheduler,
        @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor
    ) {
        this.connectionService = connectionService;
        this.collectionRepository = collectionRepository;
        this.documentRepository = documentRepository;
        this.outlineApiClient = outlineApiClient;
        this.syncScheduler = syncScheduler;
        this.taskExecutor = taskExecutor;
    }

    /** Whether a registration created the row (201) or hit an existing one (idempotent 200). */
    public record RegistrationOutcome(boolean created, OutlineCollectionDTO collection) {}

    /** The workspace's ACTIVE Outline install, resolved once per operation. */
    private record OutlineInstall(long workspaceId, long connectionId, String serverUrl) {}

    /** The registered collections of the workspace's install, ordered by registration time. */
    public List<OutlineCollectionDTO> listCollections(long workspaceId) {
        OutlineInstall install = requireInstall(workspaceId);
        Map<String, Long> liveCounts = liveCountsByCollection(install);
        return registeredRows(install)
            .stream()
            .map(row -> OutlineCollectionDTO.from(row, liveCounts.getOrDefault(row.getCollectionId(), 0L)))
            .toList();
    }

    /** One mirrored collection by its Outline id, or {@link EntityNotFoundException} (404) when not registered. */
    public OutlineCollectionDTO getCollection(Long workspaceId, String collectionId) {
        OutlineInstall install = requireInstall(workspaceId);
        return toDto(install, requireRegistered(install, collectionId));
    }

    /**
     * Live proxy to {@code collections.list} with the stored token: every collection the token can
     * see, flagged with whether it is already mirrored. This doubles as the connectivity probe — an
     * unreachable server surfaces as 502, throttling as 503 (via {@link OutlineCollectionControllerAdvice}).
     * Bounded to {@link #CANDIDATES_MAX_PAGES} pages — an interactive picker, not an exhaustive export.
     */
    public List<OutlineCollectionCandidateDTO> listCandidates(long workspaceId) {
        OutlineInstall install = requireInstall(workspaceId);
        Set<String> mirrored = registeredRows(install)
            .stream()
            .map(OutlineCollection::getCollectionId)
            .collect(Collectors.toSet());
        return outlineApiClient
            .listCollections(install.serverUrl(), requireToken(workspaceId), CANDIDATES_MAX_PAGES)
            .stream()
            .filter(c -> c.id() != null && !c.id().isBlank())
            .map(c ->
                new OutlineCollectionCandidateDTO(
                    c.id(),
                    c.name(),
                    c.urlId(),
                    c.color(),
                    c.icon(),
                    c.description(),
                    mirrored.contains(c.id())
                )
            )
            .sorted(
                Comparator.comparing(
                    OutlineCollectionCandidateDTO::name,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                )
            )
            .toList();
    }

    /**
     * Registers a collection for mirroring. Idempotent on the natural key: an existing row is
     * returned as-is. A new id is verified against the live {@code collections.list} (unknown →
     * {@link UnknownOutlineCollectionException}, 422), its catalog fields are captured server-side,
     * the row lands {@code ENABLED + PENDING}, and a targeted recency-first sync is kicked off the
     * request thread.
     */
    public RegistrationOutcome register(long workspaceId, String collectionId) {
        OutlineInstall install = requireInstall(workspaceId);
        Optional<OutlineCollection> existing = collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(
            workspaceId,
            install.connectionId(),
            collectionId
        );
        if (existing.isPresent()) {
            return new RegistrationOutcome(false, toDto(install, existing.get()));
        }

        OutlineCollectionListResponse.Collection live = outlineApiClient
            .listCollections(install.serverUrl(), requireToken(workspaceId))
            .stream()
            .filter(c -> collectionId.equals(c.id()))
            .findFirst()
            .orElseThrow(() -> new UnknownOutlineCollectionException(collectionId));

        OutlineCollection row = new OutlineCollection();
        row.setWorkspaceId(workspaceId);
        row.setConnectionId(install.connectionId());
        row.setCollectionId(collectionId);
        row.setName(live.name());
        row.setUrlId(live.urlId());
        row.setColor(live.color());
        row.setIcon(live.icon());
        row.setDescription(truncateDescription(live.description()));
        row.setState(MirrorState.ENABLED);
        row.setSyncStatus(SyncStatus.PENDING);
        OutlineCollection saved = collectionRepository.save(row);

        kickCollectionSync(workspaceId, collectionId);
        return new RegistrationOutcome(true, OutlineCollectionDTO.from(saved, 0L));
    }

    /**
     * Moves a mirrored collection to the target state ({@code ENABLED ⇄ PAUSED}). Requesting the
     * current state is an idempotent no-op. Resuming resets the sync status to {@code PENDING} —
     * a frozen collection drifted while paused, so the catch-up tick must reconverge it — and kicks
     * a targeted sync off the request thread.
     */
    @Transactional
    public OutlineCollectionDTO updateState(long workspaceId, String collectionId, MirrorState targetState) {
        OutlineInstall install = requireInstall(workspaceId);
        OutlineCollection row = requireRegistered(install, collectionId);
        if (row.getState() != targetState) {
            row.setState(targetState);
            if (targetState == MirrorState.ENABLED) {
                row.setSyncStatus(SyncStatus.PENDING);
                kickCollectionSync(workspaceId, collectionId);
            }
            row = collectionRepository.save(row);
        }
        return toDto(install, row);
    }

    /**
     * Removes a collection from the mirror: deletes the registry row AND hard-deletes that
     * collection's {@code outline_document} rows. Erase is the point — the mirrored bodies leave
     * the database; nothing is tombstoned or retained.
     */
    @Transactional
    public void delete(long workspaceId, String collectionId) {
        OutlineInstall install = requireInstall(workspaceId);
        OutlineCollection row = requireRegistered(install, collectionId);
        long erased = documentRepository.deleteByWorkspaceIdAndConnectionIdAndCollectionId(
            workspaceId,
            install.connectionId(),
            collectionId
        );
        collectionRepository.delete(row);
        log.info(
            "outline.audit: collection erase — actor={} removed collectionId={} from the mirror for workspaceId={} ({} mirrored document(s) erased)",
            LoggingUtils.sanitizeForLog(SecurityUtils.getCurrentUserLogin().orElse("system")),
            LoggingUtils.sanitizeForLog(collectionId),
            workspaceId,
            erased
        );
    }

    private List<OutlineCollection> registeredRows(OutlineInstall install) {
        return collectionRepository
            .findByWorkspaceIdOrderByCreatedAtAsc(install.workspaceId())
            .stream()
            .filter(row -> row.getConnectionId() == install.connectionId())
            .toList();
    }

    private OutlineCollection requireRegistered(OutlineInstall install, String collectionId) {
        return collectionRepository
            .findByWorkspaceIdAndConnectionIdAndCollectionId(
                install.workspaceId(),
                install.connectionId(),
                collectionId
            )
            .orElseThrow(() -> new EntityNotFoundException("Outline collection", collectionId));
    }

    private OutlineCollectionDTO toDto(OutlineInstall install, OutlineCollection row) {
        long liveCount = documentRepository.countByWorkspaceIdAndConnectionIdAndCollectionIdAndDeletedAtIsNull(
            install.workspaceId(),
            install.connectionId(),
            row.getCollectionId()
        );
        return OutlineCollectionDTO.from(row, liveCount);
    }

    private Map<String, Long> liveCountsByCollection(OutlineInstall install) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] rowCount : documentRepository.countLiveByCollection(
            install.workspaceId(),
            install.connectionId()
        )) {
            counts.put((String) rowCount[0], (Long) rowCount[1]);
        }
        return counts;
    }

    /**
     * Fires the targeted single-collection sync off the request thread through the shared
     * {@link OutlineSyncDispatch}. Routed through the {@link OutlineDocumentSyncScheduler} pass-through
     * so the executor thread (which carries no request tenancy scope) crosses the
     * {@code @WorkspaceAgnostic} bypass hop. Deliberately unguarded, unlike
     * {@code OutlineConnectionAdminService.syncNow}: both call sites (a fresh registration, a
     * PAUSED→ENABLED resume) are themselves idempotent/state-gated, so there is no human-repeatable
     * double-click path that would pile up concurrent kicks for the same collection.
     */
    private void kickCollectionSync(long workspaceId, String collectionId) {
        OutlineSyncDispatch.fireAndForget(
            taskExecutor,
            () -> syncScheduler.syncCollectionNow(workspaceId, collectionId),
            e ->
                // The registration/resume already succeeded; the catch-up tick retries a failed kick.
                log.warn(
                    "outline.admin: targeted sync kick failed for workspaceId={}, collectionId={}: {}",
                    workspaceId,
                    collectionId,
                    e.toString()
                )
        );
    }

    /** The workspace's ACTIVE Outline install, or a 404 when not connected — see {@link OutlineConnectionResolver}. */
    private OutlineInstall requireInstall(long workspaceId) {
        Connection connection = OutlineConnectionResolver.requireActiveConnection(connectionService, workspaceId);
        ConnectionConfig.OutlineConfig config = (ConnectionConfig.OutlineConfig) connection.getConfig();
        return new OutlineInstall(workspaceId, connection.getId(), config.serverUrl());
    }

    /** The install's stored API token; missing credentials read as "not usably connected" (404). */
    private String requireToken(long workspaceId) {
        return connectionService
            .findActiveBearerToken(workspaceId, IntegrationKind.OUTLINE)
            .map(BearerToken::token)
            .orElseThrow(() -> new EntityNotFoundException("Outline connection", Long.toString(workspaceId)));
    }

    /** Truncates a collection description to fit {@code outline_collection.description}'s column width. */
    private static String truncateDescription(String description) {
        if (description == null) {
            return null;
        }
        return description.length() <= MAX_DESCRIPTION_LENGTH
            ? description
            : description.substring(0, MAX_DESCRIPTION_LENGTH);
    }
}
