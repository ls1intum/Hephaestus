package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Outline's list-endpoint pagination block ({@code collections.list}, {@code documents.list}). A tolerant
 * reader — a present {@code nextPath} signals another page is available; the client also stops when a page
 * returns fewer rows than the requested limit. Raw wire record; stays inside the client package.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlinePagination(@Nullable Integer offset, @Nullable Integer limit, @Nullable String nextPath) {}
