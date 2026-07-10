package de.tum.cit.aet.hephaestus.integration.outline.sync;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.OutlineProperties;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiException;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineRateLimitedException;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionDocumentsResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineDocumentListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.SyncStatus;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.lifecycle.OutlineWebhookRegistrar;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles a workspace's registered Outline collections ({@code outline_collection}, ENABLED rows)
 * into the local {@code outline_document} mirror.
 *
 * <p>Four entry paths share one upsert core:
 * <ul>
 *   <li>{@link #syncWorkspace} — the authoritative full reconcile (6h cron + connect + admin "sync now"):
 *       catalog refresh, every ENABLED collection stalest-first under a shared export budget, webhook
 *       subscription self-heal, size cap + staleness drop.</li>
 *   <li>{@link #syncPendingCollections} — the catch-up tick's worklist: only ENABLED rows still
 *       {@code PENDING} (a budget-exhausted or freshly registered collection); zero rows → zero API calls.</li>
 *   <li>{@link #syncCollection} — one collection, targeted (admin registration kick).</li>
 *   <li>{@link #refreshDocument} / {@link #refreshCollectionCatalog} — webhook targeted refresh (≤2 API
 *       calls per document event; tombstone routing without any call for delete events).</li>
 * </ul>
 *
 * <p>Each method runs in its own {@code REQUIRES_NEW} transaction so one workspace's failure (or a
 * mid-cycle rate-limit) cannot unwind another's — callers cross a real proxy hop to reach it.
 *
 * <p>Correctness invariants: a collection's watermark ({@code documentsSyncedThrough}) and its
 * tombstone-by-absence sweep advance only on a CLEAN pass — full enumeration succeeded and no export was
 * skipped for budget. Visibility loss (collection absent from {@code collections.list}) records an error
 * and skips the collection but never tombstones its documents: forbidden/gone is not "documents removed".
 * An HTTP 429 aborts the pass — progress so far commits and the remainder resumes next tick.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineDocumentSyncService {

    private static final Logger log = LoggerFactory.getLogger(OutlineDocumentSyncService.class);

    /** Document events whose meaning is "the mirrored body must go away" — no API round-trip needed. */
    private static final Set<String> TOMBSTONE_EVENTS = Set.of(
        "documents.delete",
        "documents.permanent_delete",
        "documents.archive"
    );

    private static final int MAX_ERROR_LENGTH = 2048;

    private final ConnectionService connectionService;
    private final OutlineApiClient outlineApiClient;
    private final OutlineDocumentRepository documentRepository;
    private final OutlineCollectionRepository collectionRepository;
    private final OutlineWebhookRegistrar webhookRegistrar;
    private final OutlineProperties properties;

    public OutlineDocumentSyncService(
        ConnectionService connectionService,
        OutlineApiClient outlineApiClient,
        OutlineDocumentRepository documentRepository,
        OutlineCollectionRepository collectionRepository,
        OutlineWebhookRegistrar webhookRegistrar,
        OutlineProperties properties
    ) {
        this.connectionService = connectionService;
        this.outlineApiClient = outlineApiClient;
        this.documentRepository = documentRepository;
        this.collectionRepository = collectionRepository;
        this.webhookRegistrar = webhookRegistrar;
        this.properties = properties;
    }

    /**
     * Full reconcile of the workspace's mirror against upstream Outline. A no-op when the workspace has
     * no ACTIVE Outline Connection, no server URL, no resolvable token — or no registered collections.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncWorkspace(long workspaceId) {
        SyncContext ctx = resolveContext(workspaceId).orElse(null);
        if (ctx == null) {
            return;
        }
        Instant now = Instant.now();
        try {
            Map<String, OutlineCollectionListResponse.Collection> live = refreshCatalog(ctx);
            List<OutlineCollection> collections = collectionRepository.findForSync(
                workspaceId,
                ctx.connectionId(),
                MirrorState.ENABLED
            );
            ExportBudget budget = new ExportBudget(properties.sync().exportBudget());
            Map<String, OutlineDocument> existing = loadExisting(ctx);
            int synced = 0;
            for (OutlineCollection collection : collections) {
                if (!live.containsKey(collection.getCollectionId())) {
                    // Visibility loss ≠ deletion: never tombstone documents we merely cannot see.
                    collection.setLastSyncError("Collection is no longer visible to the integration token");
                    collectionRepository.save(collection);
                    continue;
                }
                if (syncOneCollectionRecordingError(ctx, collection, existing, budget, now)) {
                    synced++;
                }
            }
            // Self-heal the change-notification subscription each reconcile (Outline auto-disables a
            // subscription after repeated delivery failures); best-effort, never throws.
            webhookRegistrar.ensureSubscription(workspaceId);
            enforceSizeCap(workspaceId);
            long stale = documentRepository.deleteByWorkspaceIdAndDeletedAtBefore(
                workspaceId,
                now.minus(properties.staleness())
            );
            log.info(
                "outline.sync: workspaceId={} collections={} synced={} budgetLeft={} staleDropped={}",
                workspaceId,
                collections.size(),
                synced,
                budget.remaining(),
                stale
            );
        } catch (OutlineRateLimitedException e) {
            logRateLimited(workspaceId, e);
        }
    }

    /**
     * Catch-up: sync only the ENABLED collections still awaiting a clean pass ({@code PENDING}). This is
     * the resume path after a budget-exhausted pass and the convergence path for a freshly registered
     * collection; a fully caught-up workspace makes zero API calls.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncPendingCollections(long workspaceId) {
        SyncContext ctx = resolveContext(workspaceId).orElse(null);
        if (ctx == null) {
            return;
        }
        List<OutlineCollection> pending = collectionRepository
            .findByWorkspaceIdAndStateAndSyncStatus(workspaceId, MirrorState.ENABLED, SyncStatus.PENDING)
            .stream()
            .filter(c -> c.getConnectionId() == ctx.connectionId())
            .toList();
        if (pending.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        ExportBudget budget = new ExportBudget(properties.sync().exportBudget());
        Map<String, OutlineDocument> existing = loadExisting(ctx);
        try {
            for (OutlineCollection collection : pending) {
                syncOneCollectionRecordingError(ctx, collection, existing, budget, now);
            }
        } catch (OutlineRateLimitedException e) {
            logRateLimited(workspaceId, e);
        }
        // The catch-up tick exports bodies too — without this only the 6h reconcile would ever
        // shrink an over-cap mirror grown by catch-up passes.
        enforceSizeCap(workspaceId);
    }

    /** Targeted sync of one registered collection (the admin-registration kick). No-op unless ENABLED. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncCollection(long workspaceId, String collectionId) {
        SyncContext ctx = resolveContext(workspaceId).orElse(null);
        if (ctx == null) {
            return;
        }
        Optional<OutlineCollection> collection = collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(
            workspaceId,
            ctx.connectionId(),
            collectionId
        );
        if (collection.isEmpty() || !collection.get().isEnabled()) {
            return;
        }
        try {
            syncOneCollectionRecordingError(
                ctx,
                collection.get(),
                loadExisting(ctx),
                new ExportBudget(properties.sync().exportBudget()),
                Instant.now()
            );
        } catch (OutlineRateLimitedException e) {
            logRateLimited(workspaceId, e);
        }
    }

    /**
     * Webhook targeted refresh of one document (≤2 API calls). Delete-shaped events tombstone the
     * mirrored row without any call; everything else resolves {@code documents.info} — a vanished
     * document tombstones, a live one is exported iff its collection is a mirrored ENABLED collection.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshDocument(long workspaceId, String eventName, String documentId) {
        SyncContext ctx = resolveContext(workspaceId).orElse(null);
        if (ctx == null || documentId == null || documentId.isBlank()) {
            return;
        }
        Optional<OutlineDocument> mirrored = documentRepository.findByWorkspaceIdAndConnectionIdAndDocumentId(
            workspaceId,
            ctx.connectionId(),
            documentId
        );
        if (TOMBSTONE_EVENTS.contains(eventName)) {
            mirrored.filter(d -> !d.isDeleted()).ifPresent(d -> tombstone(d, Instant.now()));
            return;
        }
        try {
            Optional<OutlineDocumentListResponse.Meta> info = outlineApiClient.getDocumentInfo(
                ctx.serverUrl(),
                ctx.token(),
                documentId
            );
            if (info.isEmpty()) {
                mirrored.filter(d -> !d.isDeleted()).ifPresent(d -> tombstone(d, Instant.now()));
                return;
            }
            OutlineDocumentListResponse.Meta meta = info.get();
            if (meta.collectionId() == null) {
                return;
            }
            Optional<OutlineCollection> collection =
                collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(
                    workspaceId,
                    ctx.connectionId(),
                    meta.collectionId()
                );
            if (collection.isEmpty() || !collection.get().isEnabled()) {
                return; // the document lives outside the mirrored collections — not ours to ingest
            }
            Map<String, OutlineDocument> existing = new HashMap<>();
            mirrored.ifPresent(d -> existing.put(documentId, d));
            upsert(ctx, collection.get(), null, meta, existing, /* budget: single doc */ null, Instant.now());
        } catch (OutlineRateLimitedException e) {
            logRateLimited(workspaceId, e);
        }
    }

    /**
     * Webhook collection-event refresh. {@code collections.delete} tombstones the mirrored collection's
     * documents and records the deletion on the row (the row itself stays — removing it is the admin's
     * call); create/update refresh the mirrored rows' catalog fields with one {@code collections.list}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshCollectionCatalog(long workspaceId, String eventName, @Nullable String collectionId) {
        SyncContext ctx = resolveContext(workspaceId).orElse(null);
        if (ctx == null) {
            return;
        }
        if ("collections.delete".equals(eventName)) {
            if (collectionId == null || collectionId.isBlank()) {
                return;
            }
            collectionRepository
                .findByWorkspaceIdAndConnectionIdAndCollectionId(workspaceId, ctx.connectionId(), collectionId)
                .ifPresent(collection -> {
                    Instant now = Instant.now();
                    for (OutlineDocument doc : documentRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(
                        workspaceId,
                        ctx.connectionId(),
                        collection.getCollectionId()
                    )) {
                        if (!doc.isDeleted()) {
                            tombstone(doc, now);
                        }
                    }
                    collection.setLastSyncError("Collection was deleted in Outline");
                    collectionRepository.save(collection);
                });
            return;
        }
        try {
            refreshCatalog(ctx);
        } catch (OutlineRateLimitedException e) {
            logRateLimited(workspaceId, e);
        } catch (OutlineApiException e) {
            // Catalog fields are cosmetic; the periodic reconcile refreshes them anyway.
            log.warn("outline.sync: catalog refresh failed for workspaceId={}: {}", workspaceId, e.toString());
        }
    }

    /**
     * One {@code collections.list} call: refresh every mirrored row's human-facing catalog fields and
     * return the live collections by id (the visibility signal for the reconcile).
     */
    private Map<String, OutlineCollectionListResponse.Collection> refreshCatalog(SyncContext ctx) {
        Map<String, OutlineCollectionListResponse.Collection> live = new LinkedHashMap<>();
        for (OutlineCollectionListResponse.Collection collection : outlineApiClient.listCollections(
            ctx.serverUrl(),
            ctx.token()
        )) {
            if (collection.id() != null) {
                live.put(collection.id(), collection);
            }
        }
        for (OutlineCollection row : mirroredCollections(ctx)) {
            OutlineCollectionListResponse.Collection upstream = live.get(row.getCollectionId());
            if (upstream == null) {
                continue;
            }
            row.setName(upstream.name());
            row.setUrlId(upstream.urlId());
            row.setColor(upstream.color());
            row.setIcon(upstream.icon());
            collectionRepository.save(row);
        }
        return live;
    }

    /**
     * Sync one collection, converting a per-collection API failure into {@code lastSyncError} so the
     * remaining collections still run. Rate limits propagate — they abort the whole pass.
     *
     * @return whether the collection synced without an API failure
     */
    private boolean syncOneCollectionRecordingError(
        SyncContext ctx,
        OutlineCollection collection,
        Map<String, OutlineDocument> existing,
        ExportBudget budget,
        Instant now
    ) {
        try {
            syncOneCollection(ctx, collection, existing, budget, now);
            return true;
        } catch (OutlineRateLimitedException e) {
            throw e;
        } catch (OutlineApiException e) {
            collection.setLastSyncError(truncateError(e.getMessage()));
            collectionRepository.save(collection);
            return false;
        }
    }

    /**
     * Enumerate + upsert one collection under the shared export budget. A CLEAN pass (full enumeration,
     * no export skipped for budget) tombstones this collection's vanished rows, advances the watermark,
     * and marks the row COMPLETE; a budget-exhausted pass leaves the row PENDING with watermarks
     * untouched so nothing is ever skipped silently.
     */
    private void syncOneCollection(
        SyncContext ctx,
        OutlineCollection collection,
        Map<String, OutlineDocument> existing,
        ExportBudget budget,
        Instant now
    ) {
        String collectionId = collection.getCollectionId();
        // documents.list is newest-first, so the budget is spent on the most recently edited documents.
        List<OutlineDocumentListResponse.Meta> metas = outlineApiClient.listDocuments(
            ctx.serverUrl(),
            ctx.token(),
            collectionId
        );
        Map<String, FlatNode> nodeById = new LinkedHashMap<>();
        List<FlatNode> flat = new ArrayList<>();
        flatten(outlineApiClient.listCollectionDocuments(ctx.serverUrl(), ctx.token(), collectionId), null, flat);
        for (FlatNode node : flat) {
            nodeById.put(node.id(), node);
        }

        Set<String> seen = new HashSet<>();
        int skippedForBudget = 0;
        Instant maxUpdatedAt = null;
        for (OutlineDocumentListResponse.Meta meta : metas) {
            if (meta.id() == null) {
                continue;
            }
            seen.add(meta.id());
            if (meta.updatedAt() != null && (maxUpdatedAt == null || meta.updatedAt().isAfter(maxUpdatedAt))) {
                maxUpdatedAt = meta.updatedAt();
            }
            if (
                upsert(ctx, collection, nodeById.get(meta.id()), meta, existing, budget, now) ==
                UpsertOutcome.SKIPPED_FOR_BUDGET
            ) {
                skippedForBudget++;
            }
        }
        // Tree-only nodes (present in the structure but absent from documents.list) still count as seen
        // so a clean pass does not tombstone them.
        for (FlatNode node : flat) {
            if (!seen.add(node.id())) {
                continue;
            }
            if (upsert(ctx, collection, node, null, existing, budget, now) == UpsertOutcome.SKIPPED_FOR_BUDGET) {
                skippedForBudget++;
            }
        }

        // Coverage counters are written on EVERY full enumeration (clean or budget-exhausted): the seen
        // set is complete either way — only exports were skipped, never the enumeration itself.
        collection.setDocumentsUpstream(seen.size());
        collection.setExportsSkippedForBudget(skippedForBudget);
        if (skippedForBudget > 0) {
            collection.setSyncStatus(SyncStatus.PENDING);
            collectionRepository.save(collection);
            return;
        }
        int tombstoned = tombstoneVanished(existing, collectionId, seen, now);
        if (tombstoned > 0) {
            log.info(
                "outline.sync: tombstoned {} vanished document(s) in collection {} (workspaceId={})",
                tombstoned,
                collectionId,
                ctx.workspaceId()
            );
        }
        if (maxUpdatedAt != null) {
            collection.setDocumentsSyncedThrough(maxUpdatedAt);
        }
        collection.setDocumentsSyncedAt(now);
        collection.setSyncStatus(SyncStatus.COMPLETE);
        collection.setLastSyncError(null);
        collectionRepository.save(collection);
    }

    /**
     * Upsert one document. The unchanged-{@code updatedAt} fast path refreshes metadata without an
     * export; an export consumes one budget unit — with the budget exhausted the document is left
     * entirely untouched (a partially-written row would masquerade as an eviction placeholder).
     */
    private UpsertOutcome upsert(
        SyncContext ctx,
        OutlineCollection collection,
        @Nullable FlatNode node,
        OutlineDocumentListResponse.@Nullable Meta meta,
        Map<String, OutlineDocument> existing,
        @Nullable ExportBudget budget,
        Instant now
    ) {
        String documentId = node != null ? node.id() : (meta == null ? null : meta.id());
        if (documentId == null) {
            return UpsertOutcome.UNCHANGED;
        }
        Instant upstreamUpdatedAt = meta == null ? null : meta.updatedAt();
        OutlineDocument existingRow = existing.get(documentId);

        boolean unchanged =
            existingRow != null &&
            !existingRow.isDeleted() &&
            existingRow.getContentHash() != null &&
            existingRow.getOutlineUpdatedAt() != null &&
            upstreamUpdatedAt != null &&
            existingRow.getOutlineUpdatedAt().equals(upstreamUpdatedAt);
        if (!unchanged && budget != null && !budget.tryConsume()) {
            return UpsertOutcome.SKIPPED_FOR_BUDGET;
        }

        OutlineDocument doc = existingRow;
        if (doc == null) {
            doc = new OutlineDocument();
            doc.setWorkspaceId(ctx.workspaceId());
            doc.setConnectionId(ctx.connectionId());
            doc.setDocumentId(documentId);
        }
        doc.setCollectionId(collection.getCollectionId());
        doc.setCollectionSlug(collectionSlug(collection));
        doc.setParentDocumentId(
            node != null && node.parentId() != null ? node.parentId() : (meta == null ? null : meta.parentDocumentId())
        );
        doc.setTitle(node != null && node.title() != null ? node.title() : (meta == null ? null : meta.title()));
        doc.setSlug(resolveSlug(node, meta));
        if (unchanged) {
            // Metadata may have shifted (renamed/moved) but the body is current — do not re-export.
            documentRepository.save(doc);
            existing.put(documentId, doc);
            return UpsertOutcome.UNCHANGED;
        }

        String body = outlineApiClient.exportDocument(ctx.serverUrl(), ctx.token(), documentId);
        doc.setBodyMarkdown(body);
        doc.setContentHash(body == null ? null : sha256Hex(body));
        doc.setBodyEvictedAt(null); // the body is back in the mirror — the eviction marker must not linger
        doc.setOutlineUpdatedAt(upstreamUpdatedAt);
        if (meta != null) {
            doc.setOutlineCreatedAt(meta.createdAt());
            doc.setCreatedBySubject(meta.createdBy() == null ? null : meta.createdBy().id());
            doc.setCreatedByName(meta.createdBy() == null ? null : meta.createdBy().name());
            doc.setUpdatedBySubject(meta.updatedBy() == null ? null : meta.updatedBy().id());
            doc.setUpdatedByName(meta.updatedBy() == null ? null : meta.updatedBy().name());
            doc.setCollaboratorSubjects(
                meta.collaboratorIds() == null || meta.collaboratorIds().isEmpty()
                    ? null
                    : List.copyOf(meta.collaboratorIds())
            );
        }
        doc.setDeletedAt(null); // revive a previously tombstoned document that reappeared upstream
        doc.setLastMaterializedAt(now);
        documentRepository.save(doc);
        existing.put(documentId, doc);
        return UpsertOutcome.EXPORTED;
    }

    /** Tombstone this collection's mirrored rows that were not seen in a clean pass. */
    private int tombstoneVanished(
        Map<String, OutlineDocument> existing,
        String collectionId,
        Set<String> seen,
        Instant now
    ) {
        int count = 0;
        for (OutlineDocument doc : existing.values()) {
            if (!collectionId.equals(doc.getCollectionId()) || seen.contains(doc.getDocumentId()) || doc.isDeleted()) {
                continue;
            }
            tombstone(doc, now);
            count++;
        }
        return count;
    }

    /**
     * Drop everything person- or content-bearing: the body, its hash, and the author/collaborator
     * fields share the same PII posture — a document that no longer exists upstream keeps only its
     * structural marker. The event log ({@code outline_document_event}) is deliberately untouched:
     * it is the audit trail and erases with the workspace/connection, not with a document.
     */
    private void tombstone(OutlineDocument doc, Instant now) {
        doc.setDeletedAt(now);
        doc.setBodyMarkdown(null);
        doc.setContentHash(null); // unlike an eviction, a tombstone drops the hash too (enforced by ck_outline_document_tombstone)
        doc.setCreatedBySubject(null);
        doc.setCreatedByName(null);
        doc.setUpdatedBySubject(null);
        doc.setUpdatedByName(null);
        doc.setCollaboratorSubjects(null);
        documentRepository.save(doc);
    }

    /**
     * Enforce the per-workspace body-size cap by nulling the least-recently-materialized bodies until the
     * mirror is back under the cap. Size is measured in characters against the byte cap — an approximation
     * that treats one character as one byte, which is exact for ASCII Markdown and conservative otherwise.
     */
    private void enforceSizeCap(long workspaceId) {
        long capBytes = (long) properties.cache().maxSizeMb() * 1024L * 1024L;
        long total = documentRepository.sumBodySizeByWorkspaceId(workspaceId);
        if (total <= capBytes) {
            return;
        }
        List<Long> toEvict = new ArrayList<>();
        long remaining = total;
        for (Object[] row : documentRepository.findEvictionCandidates(workspaceId)) {
            if (remaining <= capBytes) {
                break;
            }
            toEvict.add(((Number) row[0]).longValue());
            remaining -= ((Number) row[1]).longValue();
        }
        if (!toEvict.isEmpty()) {
            documentRepository.evictBodies(workspaceId, toEvict);
            log.info(
                "outline.sync: evicted {} body(ies) for workspaceId={} to honor {}MB cap",
                toEvict.size(),
                workspaceId,
                properties.cache().maxSizeMb()
            );
        }
    }

    /** Resolve the ACTIVE Outline connection, its server URL, and a decrypted token — or empty (logged). */
    private Optional<SyncContext> resolveContext(long workspaceId) {
        Optional<Connection> active = connectionService.findActive(workspaceId, IntegrationKind.OUTLINE);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        Connection connection = active.get();
        if (!(connection.getConfig() instanceof ConnectionConfig.OutlineConfig config)) {
            log.warn("outline.sync: connection {} is not an OutlineConfig — skipping", connection.getId());
            return Optional.empty();
        }
        String serverUrl = config.serverUrl();
        if (serverUrl == null || serverUrl.isBlank()) {
            log.warn("outline.sync: no server URL on connection {} — skipping", connection.getId());
            return Optional.empty();
        }
        Optional<BearerToken> bearer = connectionService.findActiveBearerToken(workspaceId, IntegrationKind.OUTLINE);
        if (bearer.isEmpty()) {
            log.warn("outline.sync: no resolvable token for workspaceId={} — skipping", workspaceId);
            return Optional.empty();
        }
        return Optional.of(new SyncContext(workspaceId, connection.getId(), serverUrl, bearer.get().token()));
    }

    /** The mirror rows the reconcile diffs against, keyed by document id (spans all collections). */
    private Map<String, OutlineDocument> loadExisting(SyncContext ctx) {
        Map<String, OutlineDocument> existing = new HashMap<>();
        for (OutlineDocument doc : documentRepository.findByWorkspaceIdAndConnectionId(
            ctx.workspaceId(),
            ctx.connectionId()
        )) {
            existing.put(doc.getDocumentId(), doc);
        }
        return existing;
    }

    /** Mirrored collection rows for this install (all states — catalog fields refresh on PAUSED too). */
    private List<OutlineCollection> mirroredCollections(SyncContext ctx) {
        return collectionRepository
            .findByWorkspaceIdOrderByCreatedAtAsc(ctx.workspaceId())
            .stream()
            .filter(c -> c.getConnectionId() == ctx.connectionId())
            .toList();
    }

    /** Directory-layout slug for a collection: its Outline {@code urlId}, falling back to the name. */
    private static @Nullable String collectionSlug(OutlineCollection collection) {
        String urlId = collection.getUrlId();
        return urlId != null && !urlId.isBlank() ? urlId : collection.getName();
    }

    private static void logRateLimited(long workspaceId, OutlineRateLimitedException e) {
        // Pause, don't abort: progress committed so far is kept and the remainder resumes next tick. The
        // tombstone pass is intentionally skipped so a document we simply did not reach is never dropped.
        log.warn(
            "outline.sync: rate-limited for workspaceId={} — pausing this pass (retryAfter={})",
            workspaceId,
            e.getRetryAfter()
        );
    }

    private static @Nullable String truncateError(@Nullable String message) {
        if (message == null) {
            return "Outline API call failed";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    /** Depth-first flatten of the document tree, carrying each node's parent id down the recursion. */
    private static void flatten(
        @Nullable List<OutlineCollectionDocumentsResponse.Node> nodes,
        @Nullable String parentId,
        List<FlatNode> out
    ) {
        if (nodes == null) {
            return;
        }
        for (OutlineCollectionDocumentsResponse.Node node : nodes) {
            if (node.id() == null) {
                continue;
            }
            out.add(new FlatNode(node.id(), node.title(), slugFromUrl(node.url()), parentId));
            flatten(node.children(), node.id(), out);
        }
    }

    /**
     * The document's mirrored slug. The tree node's slug (already derived via {@link #slugFromUrl}) takes
     * precedence when the full-reconcile path supplied one; otherwise the webhook targeted-refresh /
     * catch-up paths (node {@code null}) derive the identical full-URL-trailing-segment slug from the
     * metadata's {@code url}, falling back to the short {@code urlId} only when Outline omits {@code url}.
     * Keeping both paths' slug shape identical is what lets a reference extracted from a full Outline URL
     * (e.g. {@code setup-guide-psUl8qCles}) resolve a document regardless of which path last wrote the row.
     */
    private static @Nullable String resolveSlug(
        @Nullable FlatNode node,
        OutlineDocumentListResponse.@Nullable Meta meta
    ) {
        if (node != null && node.slug() != null) {
            return node.slug();
        }
        if (meta == null) {
            return null;
        }
        String fromUrl = slugFromUrl(meta.url());
        return fromUrl != null ? fromUrl : meta.urlId();
    }

    /** The document slug is the last path segment of its Outline {@code url} (e.g. {@code /doc/<slug>}). */
    private static @Nullable String slugFromUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        int lastSlash = url.lastIndexOf('/');
        String candidate = lastSlash >= 0 ? url.substring(lastSlash + 1) : url;
        return candidate.isBlank() ? null : candidate;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandated JDK algorithm; its absence is a broken runtime, not a recoverable state.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** The resolved per-pass call context: workspace, install, host, and a decrypted token. */
    private record SyncContext(long workspaceId, long connectionId, String serverUrl, String token) {}

    /** One document flattened out of the collection tree, with its parent id resolved from the nesting. */
    private record FlatNode(String id, @Nullable String title, @Nullable String slug, @Nullable String parentId) {}

    private enum UpsertOutcome {
        EXPORTED,
        UNCHANGED,
        SKIPPED_FOR_BUDGET,
    }

    /** Mutable countdown of exports one pass may spend; shared across a pass's collections. */
    private static final class ExportBudget {

        private int remaining;

        ExportBudget(int budget) {
            this.remaining = budget;
        }

        boolean tryConsume() {
            if (remaining <= 0) {
                return false;
            }
            remaining--;
            return true;
        }

        int remaining() {
            return remaining;
        }
    }
}
