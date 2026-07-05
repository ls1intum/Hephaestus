package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Response of Outline's {@code auth.info} identity probe: the calling user and their team.
 *
 * <p>A tolerant reader — unknown fields are ignored so an Outline-side addition never breaks the
 * connect flow. This is a raw wire record and stays inside the client package by design.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlineAuthInfoResponse(@Nullable Data data) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@Nullable User user, @Nullable Team team) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(@Nullable String id, @Nullable String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(@Nullable String id, @Nullable String name) {}
}
