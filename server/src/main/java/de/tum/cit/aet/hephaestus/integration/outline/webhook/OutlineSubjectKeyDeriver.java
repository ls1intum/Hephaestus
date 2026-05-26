package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectKeyDeriver;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Outline {@link SubjectKeyDeriver}.
 *
 * <p>Subject layout: {@code outline.<workspace_external_id>.<collection_id_or_question_mark>.<event_type>}.
 *
 * <p>Outline events nest the workspace + collection ids inside the {@code payload} object,
 * but the {@code event} discriminator sits at the top level (e.g.
 * {@code "documents.create"}, {@code "collections.update"}). When the event is not
 * collection-scoped (workspace settings changes, user invites), the middle slot
 * collapses to {@code ?}.
 *
 * <p>Dedup key uses the top-level {@code webhookId} (Outline guarantees one per delivery),
 * falling back to a body hash for shapes without an id field.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = true)
public class OutlineSubjectKeyDeriver implements SubjectKeyDeriver {

    private static final String UNKNOWN = "?";

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    @Override
    public String deriveSubject(JsonNode payload, Map<String, String> headers) {
        String workspaceExternalId = pickFirstText(payload, "teamId", "workspaceId");
        String collectionId = UNKNOWN;
        if (payload != null) {
            JsonNode inner = payload.get("payload");
            if (inner != null && inner.isObject()) {
                JsonNode coll = inner.get("collectionId");
                if (coll != null && coll.isTextual()) {
                    collectionId = sanitizeToken(coll.asText());
                }
            }
        }
        String event = pickFirstText(payload, "event");
        return "outline." + sanitizeToken(workspaceExternalId) + "." + collectionId + "." + event;
    }

    @Override
    public String deriveDedupKey(byte[] body, Map<String, String> headers) {
        // Try to read webhookId straight off the (parsed) body without re-parsing —
        // SubjectKeyDeriver is invoked with the raw bytes, so we re-parse cheaply.
        // Falling back to a body hash keeps dedup safe even if Outline ever ships an
        // envelope without webhookId.
        String webhookId = quickStringField(body, "webhookId");
        if (webhookId != null && !webhookId.isBlank()) {
            return "outline-" + webhookId.trim();
        }
        return "outline-" + sha256Truncated(body);
    }

    private static String pickFirstText(JsonNode root, String... fields) {
        if (root == null) return UNKNOWN;
        for (String field : fields) {
            JsonNode n = root.get(field);
            if (n != null && n.isTextual() && !n.asText().isBlank()) {
                return sanitizeToken(n.asText());
            }
        }
        return UNKNOWN;
    }

    private static String sanitizeToken(String value) {
        if (value == null) return UNKNOWN;
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

    /**
     * Very small string-field extractor for the dedup path so we don't pull in
     * ObjectMapper for one optional lookup. Returns {@code null} on any failure.
     */
    private static String quickStringField(byte[] body, String field) {
        if (body == null || body.length == 0) return null;
        String text;
        try {
            text = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
        String needle = "\"" + field + "\"";
        int idx = text.indexOf(needle);
        if (idx < 0) return null;
        int colon = text.indexOf(':', idx + needle.length());
        if (colon < 0) return null;
        int firstQuote = text.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;
        int closeQuote = text.indexOf('"', firstQuote + 1);
        if (closeQuote < 0) return null;
        return text.substring(firstQuote + 1, closeQuote);
    }

    private static String sha256Truncated(byte[] body) {
        if (body == null) body = new byte[0];
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(body)).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
