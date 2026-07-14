package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Response of Outline's {@code apiKeys.list}. Outline never returns a key's plaintext value after
 * creation — only its last four characters — so a caller identifies its own key by matching
 * {@code last4} against the suffix of the token it holds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlineApiKeyListResponse(@Nullable List<ApiKey> data) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiKey(
        @Nullable String id,
        @Nullable String name,
        @Nullable String last4,
        @Nullable Instant expiresAt,
        @Nullable Instant lastActiveAt
    ) {}
}
