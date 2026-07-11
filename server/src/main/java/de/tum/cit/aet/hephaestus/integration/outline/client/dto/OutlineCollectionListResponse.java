package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Response of Outline's {@code collections.list}: the collections the token can see. The sync's catalog
 * pass refreshes each mirrored collection's human-facing fields (name, urlId, color, icon) from it and
 * uses membership in the list as the visibility signal for mirrored collections.
 *
 * <p>A tolerant reader — unknown fields are ignored. Raw wire record; stays inside the client package.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlineCollectionListResponse(@Nullable List<Collection> data, @Nullable OutlinePagination pagination) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Collection(
        @Nullable String id,
        @Nullable String name,
        @Nullable String urlId,
        @Nullable String color,
        @Nullable String icon,
        @Nullable String description
    ) {}
}
