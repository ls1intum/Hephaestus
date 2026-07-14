package de.tum.cit.aet.hephaestus.integration.outline.sync;

import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionDocumentsResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineDocumentListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Pure wire→mirror mapping for {@link OutlineDocumentSyncService}: tree flattening, slug derivation,
 * authorship transfer, hashing, clamping. No state, no I/O, no decision — every method is a function of
 * its arguments, which is exactly why it lives beside the service rather than inside it: none of it is
 * business logic, and folding it in only inflates the class the reconcile's real decisions live in.
 */
final class OutlineSyncMapping {

    private OutlineSyncMapping() {}

    /** One document flattened out of the collection tree, with its parent id resolved from the nesting. */
    record FlatNode(String id, @Nullable String title, @Nullable String slug, @Nullable String parentId) {}

    /** Depth-first flatten of the document tree, carrying each node's parent id down the recursion. */
    static void flatten(
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
    static @Nullable String resolveSlug(@Nullable FlatNode node, OutlineDocumentListResponse.@Nullable Meta meta) {
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
    static @Nullable String slugFromUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        int lastSlash = url.lastIndexOf('/');
        String candidate = lastSlash >= 0 ? url.substring(lastSlash + 1) : url;
        return candidate.isBlank() ? null : candidate;
    }

    /** Directory-layout slug for a collection: its Outline {@code urlId}, falling back to the name. */
    static @Nullable String collectionSlug(OutlineCollection collection) {
        String urlId = collection.getUrlId();
        return urlId != null && !urlId.isBlank() ? urlId : collection.getName();
    }

    /**
     * The authorship substrate a fresh export carries: creator, last editor, and the middle collaborators
     * the creator/last-editor pair misses. Only ever applied alongside a body — a metadata-only refresh
     * must not rewrite these columns.
     */
    static void applyAuthorship(OutlineDocument doc, OutlineDocumentListResponse.Meta meta) {
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

    /** Truncates a value to at most {@code maxLen} characters; {@code null} passes through unchanged. */
    static @Nullable String truncate(@Nullable String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandated JDK algorithm; its absence is a broken runtime, not a recoverable state.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
