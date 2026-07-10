package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Map;
import tools.jackson.databind.JsonNode;

public interface WebhookPublishGate {
    IntegrationKind kind();

    Decision evaluate(JsonNode payload, Map<String, String> headers);

    record Decision(boolean publish, String reason) {
        public static Decision allow() {
            return new Decision(true, "allowed");
        }

        public static Decision drop(String reason) {
            return new Decision(false, reason == null || reason.isBlank() ? "dropped" : reason);
        }
    }
}
