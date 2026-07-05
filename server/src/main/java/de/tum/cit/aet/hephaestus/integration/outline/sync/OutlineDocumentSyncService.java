package de.tum.cit.aet.hephaestus.integration.outline.sync;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.OutlineProperties;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineRateLimitedException;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionDocumentsResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineDocumentListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
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
 * Reconciles one workspace's allow-listed Outline collections into the local {@code outline_document} mirror.
 *
 * <p>Runs per workspace in its own {@code REQUIRES_NEW} transaction so one workspace's failure (or a
 * mid-cycle rate-limit) cannot unwind another's — the scheduler crosses a real proxy hop to reach it.
 *
 * <p>The reconcile is incremental: for each allow-listed collection it fetches the document tree
 * (membership + parent nesting + slug) and the per-document metadata (the {@code updatedAt} cursor), and
 * only re-exports a document whose upstream {@code updatedAt} moved (or that has no cached body yet). A
 * document that vanishes upstream is tombstoned (its body dropped); a tombstone older than the staleness
 * horizon is deleted outright. A per-workspace size cap evicts the least-recently-materialized bodies when
 * the mirror grows past it. An HTTP 429 pauses the cycle — progress so far commits and the rest resumes
 * next tick — rather than aborting.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineDocumentSyncService {

    private static final Logger log = LoggerFactory.getLogger(OutlineDocumentSyncService.class);

    private final ConnectionService connectionService;
    private final OutlineApiClient outlineApiClient;
    private final OutlineDocumentRepository documentRepository;
    private final OutlineProperties properties;

    public OutlineDocumentSyncService(
        ConnectionService connectionService,
        OutlineApiClient outlineApiClient,
        OutlineDocumentRepository documentRepository,
        OutlineProperties properties
    ) {
        this.connectionService = connectionService;
        this.outlineApiClient = outlineApiClient;
        this.documentRepository = documentRepository;
        this.properties = properties;
    }

    /**
     * Reconcile the workspace's mirror against upstream Outline. A no-op when the workspace has no ACTIVE
     * Outline Connection, no server URL, or no resolvable token.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncWorkspace(long workspaceId) {
        Optional<Connection> active = connectionService.findActive(workspaceId, IntegrationKind.OUTLINE);
        if (active.isEmpty()) {
            return;
        }
        Connection connection = active.get();
        if (!(connection.getConfig() instanceof ConnectionConfig.OutlineConfig config)) {
            log.warn("outline.sync: connection {} is not an OutlineConfig — skipping", connection.getId());
            return;
        }
        String serverUrl = config.serverUrl();
        if (serverUrl == null || serverUrl.isBlank()) {
            log.warn("outline.sync: no server URL on connection {} — skipping", connection.getId());
            return;
        }
        Optional<BearerToken> bearer = connectionService.findActiveBearerToken(workspaceId, IntegrationKind.OUTLINE);
        if (bearer.isEmpty()) {
            log.warn("outline.sync: no resolvable token for workspaceId={} — skipping", workspaceId);
            return;
        }
        String token = bearer.get().token();
        long connectionId = connection.getId();

        Map<String, OutlineDocument> existing = new HashMap<>();
        for (OutlineDocument doc : documentRepository.findByWorkspaceIdAndConnectionId(workspaceId, connectionId)) {
            existing.put(doc.getDocumentId(), doc);
        }
        Set<String> seen = new HashSet<>();
        Instant now = Instant.now();

        try {
            List<ResolvedCollection> collections = resolveCollections(serverUrl, token, config.collectionAllowList());
            int exported = 0;
            for (ResolvedCollection collection : collections) {
                List<OutlineCollectionDocumentsResponse.Node> tree = outlineApiClient.listCollectionDocuments(
                    serverUrl,
                    token,
                    collection.id()
                );
                Map<String, OutlineDocumentListResponse.Meta> metaById = new HashMap<>();
                for (OutlineDocumentListResponse.Meta meta : outlineApiClient.listDocuments(
                    serverUrl,
                    token,
                    collection.id()
                )) {
                    if (meta.id() != null) {
                        metaById.put(meta.id(), meta);
                    }
                }
                List<FlatNode> flat = new ArrayList<>();
                flatten(tree, null, flat);
                for (FlatNode node : flat) {
                    seen.add(node.id());
                    boolean didExport = upsert(
                        workspaceId,
                        connectionId,
                        collection,
                        node,
                        metaById.get(node.id()),
                        existing.get(node.id()),
                        serverUrl,
                        token,
                        now
                    );
                    if (didExport) {
                        exported++;
                    }
                }
            }

            int tombstoned = tombstoneVanished(existing, seen, now);
            enforceSizeCap(workspaceId);
            long stale = documentRepository.deleteByWorkspaceIdAndDeletedAtBefore(
                workspaceId,
                now.minus(properties.staleness())
            );
            log.info(
                "outline.sync: workspaceId={} collections={} exported={} tombstoned={} staleDropped={}",
                workspaceId,
                collections.size(),
                exported,
                tombstoned,
                stale
            );
        } catch (OutlineRateLimitedException e) {
            // Pause, don't abort: progress committed so far is kept and the remainder resumes next cycle. The
            // tombstone pass is intentionally skipped so a document we simply did not reach is never dropped.
            Duration retryAfter = e.getRetryAfter();
            log.warn(
                "outline.sync: rate-limited for workspaceId={} — pausing this cycle (retryAfter={})",
                workspaceId,
                retryAfter
            );
        }
    }

    /**
     * Upsert one document. Returns {@code true} when the body was (re-)exported, {@code false} when the
     * unchanged-{@code updatedAt} fast path skipped the export.
     */
    private boolean upsert(
        long workspaceId,
        long connectionId,
        ResolvedCollection collection,
        FlatNode node,
        OutlineDocumentListResponse.@Nullable Meta meta,
        @Nullable OutlineDocument existingRow,
        String serverUrl,
        String token,
        Instant now
    ) {
        Instant upstreamUpdatedAt = meta == null ? null : meta.updatedAt();
        String title = node.title() != null ? node.title() : (meta == null ? null : meta.title());
        String slug = node.slug() != null ? node.slug() : (meta == null ? null : meta.urlId());
        String parentId = node.parentId() != null ? node.parentId() : (meta == null ? null : meta.parentDocumentId());

        OutlineDocument doc = existingRow;
        if (doc == null) {
            doc = new OutlineDocument();
            doc.setWorkspaceId(workspaceId);
            doc.setConnectionId(connectionId);
            doc.setDocumentId(node.id());
        }
        doc.setCollectionId(collection.id());
        doc.setCollectionSlug(collection.slug());
        doc.setParentDocumentId(parentId);
        doc.setTitle(title);
        doc.setSlug(slug);

        boolean unchanged =
            existingRow != null &&
            !existingRow.isDeleted() &&
            existingRow.getContentHash() != null &&
            existingRow.getOutlineUpdatedAt() != null &&
            upstreamUpdatedAt != null &&
            existingRow.getOutlineUpdatedAt().equals(upstreamUpdatedAt);
        if (unchanged) {
            // Metadata may have shifted (renamed/moved) but the body is current — do not re-export.
            documentRepository.save(doc);
            return false;
        }

        String body = outlineApiClient.exportDocument(serverUrl, token, node.id());
        doc.setBodyMarkdown(body);
        doc.setContentHash(body == null ? null : sha256Hex(body));
        doc.setOutlineUpdatedAt(upstreamUpdatedAt);
        doc.setDeletedAt(null); // revive a previously tombstoned document that reappeared upstream
        doc.setLastMaterializedAt(now);
        documentRepository.save(doc);
        return true;
    }

    /** Tombstone every mirrored row not seen this cycle: drop its body, stamp {@code deleted_at}. */
    private int tombstoneVanished(Map<String, OutlineDocument> existing, Set<String> seen, Instant now) {
        int count = 0;
        for (OutlineDocument doc : existing.values()) {
            if (seen.contains(doc.getDocumentId()) || doc.isDeleted()) {
                continue;
            }
            doc.setDeletedAt(now);
            doc.setBodyMarkdown(null);
            doc.setContentHash(null);
            documentRepository.save(doc);
            count++;
        }
        return count;
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

    /** Resolve each allow-list entry (a collection id, {@code urlId}, or name) to a concrete collection. */
    private List<ResolvedCollection> resolveCollections(String serverUrl, String token, Set<String> allowList) {
        if (allowList == null || allowList.isEmpty()) {
            return List.of();
        }
        Set<String> wanted = new HashSet<>();
        for (String entry : allowList) {
            if (entry != null && !entry.isBlank()) {
                wanted.add(entry.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<ResolvedCollection> resolved = new ArrayList<>();
        for (OutlineCollectionListResponse.Collection c : outlineApiClient.listCollections(serverUrl, token)) {
            if (c.id() == null) {
                continue;
            }
            boolean match =
                wanted.contains(c.id().toLowerCase(Locale.ROOT)) ||
                (c.urlId() != null && wanted.contains(c.urlId().toLowerCase(Locale.ROOT))) ||
                (c.name() != null && wanted.contains(c.name().toLowerCase(Locale.ROOT)));
            if (match) {
                String slug = c.urlId() != null ? c.urlId() : c.name();
                resolved.add(new ResolvedCollection(c.id(), slug));
            }
        }
        return resolved;
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

    /** A collection resolved from the allow-list: its id and a stable slug for the directory layout. */
    private record ResolvedCollection(String id, @Nullable String slug) {}

    /** One document flattened out of the collection tree, with its parent id resolved from the nesting. */
    private record FlatNode(String id, @Nullable String title, @Nullable String slug, @Nullable String parentId) {}
}
