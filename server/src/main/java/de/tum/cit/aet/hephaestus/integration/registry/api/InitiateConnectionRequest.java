package de.tum.cit.aet.hephaestus.integration.registry.api;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.net.URI;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Inbound payload for {@code POST /api/v1/workspaces/{workspaceId}/connections}.
 *
 * <p>{@code userInput} is intentionally a free-form map so per-kind ConnectionStrategy
 * implementations can dictate their own field schema (e.g. GitLab needs {@code pat} +
 * {@code group_id}; GitHub needs nothing because the install URL is server-configured).
 * Validation is the strategy's responsibility — invalid input surfaces as a 400 via
 * {@code IllegalArgumentException}.
 */
public record InitiateConnectionRequest(
    IntegrationKind kind,
    Map<String, String> userInput,
    @Nullable URI redirectAfter
) {
}
