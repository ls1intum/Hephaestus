package de.tum.cit.aet.hephaestus.integration.gitlab.webhook;

import tools.jackson.databind.JsonNode;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectKeyDeriver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds NATS subjects + dedup keys for GitLab webhook payloads under the unified
 * integration framework.
 *
 * <p>Subject format: {@code gitlab.<namespace~with~tildes>.<project>.<event>}
 * <ul>
 *   <li>{@code namespace} and {@code project} from {@code project.path_with_namespace}
 *       split on {@code /} (last segment is project; everything before joined with
 *       {@code ~}, since {@code /} is the NATS hierarchy separator and groups can be
 *       nested arbitrarily deep).
 *   <li>{@code event} from payload {@code object_kind} ({@code merge_request},
 *       {@code push}, {@code issue}, {@code note}, {@code pipeline}, {@code build},
 *       {@code wiki_page}, …). Lowercased via {@link Locale#ROOT} to dodge the
 *       Turkish-i pitfall.
 * </ul>
 *
 * <p>Dedup key priority (per ADR 0008): {@code Idempotency-Key} (GitLab 17.4+) →
 * {@code X-Gitlab-Event-UUID} (16.2+) → SHA-256 of {@code body + event}, truncated to
 * 32 hex chars. The vendor prefix {@code "gitlab-"} ensures cross-vendor uniqueness
 * even if two providers happen to issue the same UUID.
 */
@Component
public class GitlabSubjectKeyDeriver implements SubjectKeyDeriver {

    static final String SUBJECT_PREFIX = "gitlab.";
    static final String UNKNOWN_TOKEN = "unknown";
    static final String PLACEHOLDER = "?";

    private static final String HEADER_EVENT_UUID = "x-gitlab-event-uuid";
    private static final String HEADER_IDEMPOTENCY_KEY = "idempotency-key";

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public String deriveSubject(JsonNode payload, Map<String, String> headers) {
        String event = sanitizeEvent(textOrEmpty(payload, "object_kind"));
        if (event.isEmpty()) {
            event = sanitizeEvent(textOrEmpty(payload, "event_name"));
        }
        if (event.isEmpty()) {
            event = UNKNOWN_TOKEN;
        }

        String pathWithNs = firstNonBlank(
            textOrEmpty(payload.path("project"), "path_with_namespace"),
            textOrEmpty(payload, "path_with_namespace")
        );

        String namespace = PLACEHOLDER;
        String project = PLACEHOLDER;
        if (pathWithNs != null) {
            List<String> parts = sanitizeParts(pathWithNs);
            if (parts.size() >= 2) {
                namespace = String.join("~", parts.subList(0, parts.size() - 1));
                project = parts.get(parts.size() - 1);
            } else if (parts.size() == 1) {
                namespace = parts.get(0);
            }
        }

        return SUBJECT_PREFIX + namespace + "." + project + "." + event;
    }

    @Override
    public String deriveDedupKey(byte[] body, Map<String, String> headers) {
        Map<String, String> lower = lowercaseKeys(headers);
        String idempotency = lower.get(HEADER_IDEMPOTENCY_KEY);
        if (idempotency != null && !idempotency.isBlank()) {
            return "gitlab-" + idempotency.trim();
        }
        String eventUuid = lower.get(HEADER_EVENT_UUID);
        if (eventUuid != null && !eventUuid.isBlank()) {
            return "gitlab-" + eventUuid.trim();
        }
        // Fall back: SHA-256 over body + a vendor event marker. The event marker
        // comes from headers (X-Gitlab-Event) when present; otherwise empty. Truncate
        // the hex digest to 32 chars — the full 64 still fits NATS but bloats logs.
        String event = lower.getOrDefault("x-gitlab-event", "");
        byte[] digest = sha256(body, event.getBytes(StandardCharsets.UTF_8));
        String hex = HexFormat.of().formatHex(digest);
        return "gitlab-" + hex.substring(0, Math.min(32, hex.length()));
    }

    private static byte[] sha256(byte[] body, byte[] suffix) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(body);
            md.update((byte) '|');
            md.update(suffix);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JRE; if it's missing, fail loudly.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String sanitizeEvent(String value) {
        if (value == null || value.isEmpty()) return "";
        // Lowercase + replace dots so the event component never collides with NATS
        // token boundaries.
        return value.toLowerCase(Locale.ROOT).replace('.', '~');
    }

    private static List<String> sanitizeParts(String path) {
        List<String> out = new ArrayList<>();
        for (String segment : path.split("/", -1)) {
            if (!segment.isEmpty()) {
                out.add(segment.toLowerCase(Locale.ROOT).replace('.', '~'));
            }
        }
        return out;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static Map<String, String> lowercaseKeys(Map<String, String> raw) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>(raw.size());
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() != null) {
                out.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
            }
        }
        return out;
    }
}
