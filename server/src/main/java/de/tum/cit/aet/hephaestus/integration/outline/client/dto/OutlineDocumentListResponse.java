package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Response of Outline's {@code documents.list}: per-document metadata for a collection. {@code updatedAt} is
 * the incremental cursor the sync diffs against so an unchanged document is never re-exported;
 * {@code createdAt} and {@code collaboratorIds} feed the mirror's up-to-dateness and middle-editor columns.
 *
 * <p>A tolerant reader — unknown fields are ignored. Raw wire record; stays inside the client package.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlineDocumentListResponse(@Nullable List<Meta> data, @Nullable OutlinePagination pagination) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
        @Nullable String id,
        @Nullable String title,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt,
        @Nullable String urlId,
        @Nullable String parentDocumentId,
        @Nullable String collectionId,
        @Nullable OutlineUser createdBy,
        @Nullable OutlineUser updatedBy,
        @Nullable List<String> collaboratorIds
    ) {}

    /** Document author reference — id and display name only; email is deliberately never captured. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutlineUser(@Nullable String id, @Nullable String name) {}
}
