package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Response of Outline's {@code documents.list}: per-document metadata for a collection. {@code updatedAt} is
 * the incremental cursor the sync diffs against so an unchanged document is never re-exported;
 * {@code createdAt} and {@code collaboratorIds} feed the mirror's up-to-dateness and middle-editor columns.
 * {@code url} (e.g. {@code /doc/<title-slug>-<urlId>}) is the same field the document tree exposes — the sync
 * derives the mirrored slug from it so the webhook targeted-refresh path and the full-reconcile path store
 * the identical, full slug for a document.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlineDocumentListResponse(@Nullable List<Meta> data, @Nullable OutlinePagination pagination) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
        @Nullable String id,
        @Nullable String url,
        @Nullable String title,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt,
        @Nullable String urlId,
        @Nullable String parentDocumentId,
        @Nullable String collectionId,
        @Nullable OutlineUser createdBy,
        @Nullable OutlineUser updatedBy,
        @Nullable List<String> collaboratorIds,
        /**
         * When the document was archived upstream, or {@code null} when live — Outline's default
         * {@code documents.list}/{@code collections.documents} exclude archived documents entirely, so a
         * {@code null} here from those endpoints means "not archived", never "unknown".
         */
        @Nullable Instant archivedAt
    ) {}

    /** Document author reference — id and display name only; email is deliberately never captured. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutlineUser(@Nullable String id, @Nullable String name) {}
}
