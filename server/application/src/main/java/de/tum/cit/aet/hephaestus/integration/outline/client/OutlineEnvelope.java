package de.tum.cit.aet.hephaestus.integration.outline.client;

import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlinePagination;
import org.jspecify.annotations.Nullable;

/**
 * Outline's uniform response wrapper. Every Outline RPC answers with {@code {data, pagination, policies}}
 * (and, on some calls, {@code ok}/{@code status}); the vendor models generated from Outline's OpenAPI spec
 * describe the {@code data} payload, never this envelope. The envelope is transport shape, so it is the one
 * record the Outline client hand-writes: the generated {@link OutlinePagination} carries the list cursor,
 * and unknown envelope siblings ({@code policies}, {@code ok}, {@code status}) are dropped by the client
 * WebClient's tolerant Jackson decoder ({@code FAIL_ON_UNKNOWN_PROPERTIES} disabled), not by an annotation.
 *
 * @param <T> the {@code data} payload — a single vendor model (e.g. {@code OutlineDocument}) or a
 *            {@code List} of them, selected per call via a {@code ParameterizedTypeReference}.
 */
public record OutlineEnvelope<T>(@Nullable T data, @Nullable OutlinePagination pagination) {}
