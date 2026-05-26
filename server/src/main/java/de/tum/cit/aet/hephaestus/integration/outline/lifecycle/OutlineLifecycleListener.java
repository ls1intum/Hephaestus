package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.refs.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.refs.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges Outline vendor lifecycle events to Outline-shaped persistence.
 *
 * <p>Soft-delete semantics on {@code collections.delete} (and the cascading documents):
 * we keep the row + tombstone timestamp so the cross-vendor
 * {@code FeedbackPost.subject_external_id} audit trail remains intact. Physical removal
 * happens only on full uninstall (Connection cascade).
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = true)
public class OutlineLifecycleListener implements IntegrationLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(OutlineLifecycleListener.class);

    private final ConnectionRepository connectionRepository;
    private final OutlineCollectionRepository collectionRepository;
    private final OutlineDocumentRepository documentRepository;
    private final Clock clock;

    public OutlineLifecycleListener(
        ConnectionRepository connectionRepository,
        OutlineCollectionRepository collectionRepository,
        OutlineDocumentRepository documentRepository,
        Clock clock
    ) {
        this.connectionRepository = connectionRepository;
        this.collectionRepository = collectionRepository;
        this.documentRepository = documentRepository;
        this.clock = clock;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    /**
     * Outline scope change: collection deleted in upstream Outline → soft-delete the
     * collection row AND all child documents (so they no longer surface in mentor
     * context fetches). Physical purge is deferred to Connection uninstall (cascade).
     */
    @Override
    @Transactional
    public void onScopeChanged(IntegrationRef ref, ScopeDelta delta) {
        if (delta.removedExternalIds() == null || delta.removedExternalIds().isEmpty()) {
            return;
        }
        Optional<Connection> connectionOpt = resolveConnection(ref);
        if (connectionOpt.isEmpty()) {
            log.warn(
                "Outline scope-change for unknown connection ref={}, skipping soft-delete of {} collections",
                ref,
                delta.removedExternalIds().size()
            );
            return;
        }
        long connectionId = connectionOpt.get().getId();
        long workspaceId = ref.workspaceId();
        Instant now = Instant.now(clock);

        // Soft-delete documents first, then the collection — order matters only for
        // logging/audit clarity; both operations are idempotent (deleted_at IS NULL
        // guard in the UPDATE). The repository methods pin the tenant predicate on
        // both connection.workspace.id and connection.id so a mismatched tuple is
        // a guaranteed no-op rather than a cross-tenant leak.
        for (String collectionId : delta.removedExternalIds()) {
            int docs = documentRepository.softDeleteByCollection(workspaceId, connectionId, collectionId, now);
            log.info(
                "Outline collection soft-delete: workspace={}, connection={}, collection={}, documentsTombstoned={}",
                workspaceId,
                connectionId,
                collectionId,
                docs
            );
        }
        int collections = collectionRepository.softDeleteByConnectionIdAndCollectionIdIn(
            workspaceId,
            connectionId,
            List.copyOf(delta.removedExternalIds()),
            now
        );
        log.info(
            "Outline collection soft-delete: workspace={}, connection={}, collectionsTombstoned={}",
            workspaceId,
            connectionId,
            collections
        );
    }

    @Override
    public void onInstanceUninstalled(IntegrationRef ref) {
        log.info("Outline instance uninstalled (cascade handled by Connection FK): ref={}", ref);
    }

    private Optional<Connection> resolveConnection(IntegrationRef ref) {
        if (ref.instanceKey() == null) {
            return Optional.empty();
        }
        return connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            ref.workspaceId(),
            ref.kind(),
            ref.instanceKey()
        );
    }
}
