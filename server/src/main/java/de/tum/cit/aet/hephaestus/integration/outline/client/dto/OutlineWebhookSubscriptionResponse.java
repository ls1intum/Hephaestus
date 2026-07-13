package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Response of Outline's {@code webhookSubscriptions.create}: the id of the registered change-notification
 * subscription in {@code data.id}. That id is stored on the Connection so the subscription can later be
 * matched (as an untrusted routing key on an inbound event) and deleted on revoke.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlineWebhookSubscriptionResponse(@Nullable Data data) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@Nullable String id) {}
}
