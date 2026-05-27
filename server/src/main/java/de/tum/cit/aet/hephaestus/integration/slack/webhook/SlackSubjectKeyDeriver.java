package de.tum.cit.aet.hephaestus.integration.slack.webhook;

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

/**
 * Slack {@link SubjectKeyDeriver}.
 *
 * <p>Subject layout: {@code slack.<team_id>.<channel_id_or_question_mark>.<event_type>}.
 * The middle slot collapses to {@code ?} for events without a channel (team-level
 * lifecycle events like {@code app_uninstalled}). Event type comes from
 * {@code event.type} on Events API envelopes, falling back to top-level {@code type}
 * for shapes that don't wrap an inner event (slash commands, interactivity payloads
 * would land here too once we add their controllers).
 *
 * <p>Dedup key uses {@code X-Slack-Request-Id} when present; otherwise we fall back to
 * a body hash so retried deliveries dedupe deterministically without writing two NATS
 * messages.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackSubjectKeyDeriver implements SubjectKeyDeriver {

    private static final String HEADER_REQUEST_ID = "X-Slack-Request-Id";
    private static final String UNKNOWN = "?";

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public String deriveSubject(JsonNode payload, Map<String, String> headers) {
        String teamId = textOrUnknown(payload, "team_id");
        JsonNode event = payload == null ? null : payload.get("event");
        String channelId = UNKNOWN;
        String eventType;
        if (event != null && event.isObject()) {
            JsonNode chan = event.get("channel");
            if (chan != null && chan.isTextual()) {
                channelId = sanitizeToken(chan.asText());
            }
            JsonNode evType = event.get("type");
            eventType =
                evType != null && evType.isTextual() ? sanitizeToken(evType.asText()) : textOrUnknown(payload, "type");
        } else {
            eventType = textOrUnknown(payload, "type");
        }
        return "slack." + sanitizeToken(teamId) + "." + channelId + "." + eventType;
    }

    @Override
    public String deriveDedupKey(byte[] body, Map<String, String> headers) {
        String requestId = headerIgnoreCase(headers, HEADER_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            return "slack-" + requestId.trim();
        }
        return "slack-" + sha256Truncated(body);
    }

    private static String textOrUnknown(JsonNode root, String field) {
        if (root == null) return UNKNOWN;
        JsonNode n = root.get(field);
        if (n == null || !n.isTextual() || n.asText().isBlank()) return UNKNOWN;
        return sanitizeToken(n.asText());
    }

    private static String sanitizeToken(String value) {
        if (value == null) return UNKNOWN;
        // NATS subject tokens reject '.', '*', '>' and whitespace; collapse anything unsafe.
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return UNKNOWN;
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '.' || c == '*' || c == '>' || Character.isWhitespace(c)) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String headerIgnoreCase(Map<String, String> headers, String name) {
        if (headers == null) return null;
        String direct = headers.get(name);
        if (direct != null) return direct;
        String lower = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String sha256Truncated(byte[] body) {
        if (body == null) body = new byte[0];
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(body);
            // 16 hex chars (64 bits) is plenty for dedup-window collision resistance.
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in the JRE; cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
