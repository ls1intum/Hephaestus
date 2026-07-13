package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Response of Outline's {@code webhookSubscriptions.list}: the change-notification subscriptions the
 * token owns. The registrar's self-heal pass diffs the stored subscription id against this list —
 * Outline auto-disables a subscription after repeated delivery failures, so a missing or
 * {@code enabled=false} entry means the stored id is dead and must be re-registered.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlineWebhookSubscriptionListResponse(
    @Nullable List<Subscription> data,
    @Nullable OutlinePagination pagination
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Subscription(
        @Nullable String id,
        @Nullable String name,
        @Nullable String url,
        @Nullable Boolean enabled,
        @Nullable List<String> events
    ) {}
}
