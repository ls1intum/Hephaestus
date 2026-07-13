package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Outline's list-endpoint pagination block ({@code collections.list}, {@code documents.list}). A present
 * {@code nextPath} signals another page; the client also stops when a page returns fewer rows than the
 * requested limit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlinePagination(@Nullable Integer offset, @Nullable Integer limit, @Nullable String nextPath) {}
