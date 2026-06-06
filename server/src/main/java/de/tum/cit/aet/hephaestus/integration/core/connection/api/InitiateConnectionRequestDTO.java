package de.tum.cit.aet.hephaestus.integration.core.connection.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.util.Map;

/**
 * Inbound payload for {@code POST /workspaces/{workspaceSlug}/connections}.
 *
 * <p>{@code userInput} is intentionally a free-form map so per-kind ConnectionStrategy
 * implementations can dictate their own field schema (e.g. GitLab needs {@code pat} +
 * {@code group_id}; GitHub needs nothing because the install URL is server-configured).
 * Validation is the strategy's responsibility — invalid input surfaces as a 400 via
 * {@code IllegalArgumentException}.
 */
public record InitiateConnectionRequestDTO(IntegrationKind kind, Map<String, String> userInput) {}
