package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Response of Outline's {@code collections.list}: the collections the token can see. Used to resolve an
 * allow-list entry (a collection id, {@code urlId}, or name) to a concrete collection id and a stable slug
 * for the mirrored document's directory layout.
 *
 * <p>A tolerant reader — unknown fields are ignored. Raw wire record; stays inside the client package.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlineCollectionListResponse(@Nullable List<Collection> data, @Nullable OutlinePagination pagination) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Collection(@Nullable String id, @Nullable String name, @Nullable String urlId) {}
}
