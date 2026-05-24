package de.tum.cit.aet.hephaestus.integration.messaging.spi;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Sealed messaging-content shape. Lifts rich-formatting decisions into the family-lib
 * so per-vendor adapters compile the right wire payload (Slack Block Kit, MS Teams
 * Adaptive Card, Discord Component v2, or plain markdown).
 */
public sealed interface MessagingComponent
    permits MessagingComponent.PlainText,
            MessagingComponent.BlockKit,
            MessagingComponent.AdaptiveCard,
            MessagingComponent.DiscordComponentV2 {

    record PlainText(String body) implements MessagingComponent {}

    /** Slack Block Kit blocks array as JSON. */
    record BlockKit(JsonNode blocks) implements MessagingComponent {}

    /** Microsoft Teams Adaptive Card payload. */
    record AdaptiveCard(JsonNode card) implements MessagingComponent {}

    /** Discord Components v2 payload. */
    record DiscordComponentV2(JsonNode components) implements MessagingComponent {}
}
