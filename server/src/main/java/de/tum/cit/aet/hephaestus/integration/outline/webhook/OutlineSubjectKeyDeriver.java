package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SubjectKeyDeriver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds NATS subjects + dedup keys for Outline webhook deliveries.
 *
 * <p>Subject format: {@code outline.<subscriptionId>.<event>}. The subscription id is a UUID (no
 * dots) and scopes the delivery to a single workspace's registered subscription; the event
 * ({@code documents.update}, …) is lowercased via {@link Locale#ROOT} with dots replaced by
 * {@code ~} so it never collides with the NATS token boundary ({@code documents~update}).
 *
 * <p>Dedup key: {@code "outline-" + sha256(subscriptionId|event|payload.id)} when the payload id is
 * present (Outline retries carry the same document id + event), else a SHA-256 of the raw body
 * truncated to 32 hex chars. The {@code "outline-"} vendor prefix guarantees cross-vendor
 * uniqueness on the shared JetStream dedup window.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineSubjectKeyDeriver implements SubjectKeyDeriver {

    static final String SUBJECT_PREFIX = "outline.";
    static final String UNKNOWN_TOKEN = "unknown";

    private final ObjectMapper objectMapper;

    public OutlineSubjectKeyDeriver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    @Override
    public String deriveSubject(JsonNode payload, Map<String, String> headers) {
        String subscriptionId = sanitize(textOrEmpty(payload, "webhookSubscriptionId"));
        if (subscriptionId.isEmpty()) {
            subscriptionId = UNKNOWN_TOKEN;
        }
        String event = sanitize(textOrEmpty(payload, "event"));
        if (event.isEmpty()) {
            event = UNKNOWN_TOKEN;
        }
        return SUBJECT_PREFIX + subscriptionId + "." + event;
    }

    @Override
    public String deriveDedupKey(byte[] body, Map<String, String> headers) {
        JsonNode payload = tryParse(body);
        if (payload != null) {
            String subscriptionId = textOrEmpty(payload, "webhookSubscriptionId");
            String event = textOrEmpty(payload, "event");
            String documentId = textOrEmpty(payload.path("payload"), "id");
            if (!subscriptionId.isBlank() && !event.isBlank() && !documentId.isBlank()) {
                byte[] digest = sha256(
                    (subscriptionId + "|" + event + "|" + documentId).getBytes(StandardCharsets.UTF_8)
                );
                return "outline-" + HexFormat.of().formatHex(digest);
            }
        }
        String hex = HexFormat.of().formatHex(sha256(body == null ? new byte[0] : body));
        return "outline-" + hex.substring(0, Math.min(32, hex.length()));
    }

    private static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('.', '~');
    }

    private JsonNode tryParse(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asString("");
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
