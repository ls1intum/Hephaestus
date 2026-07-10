package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Response of Outline's {@code documents.info}: one document's metadata, mirroring the
 * {@link OutlineDocumentListResponse.Meta} fields. The webhook targeted-refresh path uses it to
 * decide whether an event's document still exists and which collection it lives in.
 *
 * <p>A tolerant reader — unknown fields are ignored. Raw wire record; stays inside the client package.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlineDocumentInfoResponse(@Nullable Data data) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
        @Nullable String id,
        @Nullable String title,
        @Nullable Instant updatedAt,
        @Nullable String urlId,
        @Nullable String parentDocumentId,
        @Nullable String collectionId,
        OutlineDocumentListResponse.@Nullable OutlineUser createdBy,
        OutlineDocumentListResponse.@Nullable OutlineUser updatedBy
    ) {}
}
