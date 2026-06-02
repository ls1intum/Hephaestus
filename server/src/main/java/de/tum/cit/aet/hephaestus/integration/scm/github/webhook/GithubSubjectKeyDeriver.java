package de.tum.cit.aet.hephaestus.integration.scm.github.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SubjectKeyDeriver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * GitHub adapter for {@link SubjectKeyDeriver}. Produces JetStream subjects of the
 * shape {@code github.<org>.<repo>.<event>} where {@code <org>} and {@code <repo>}
 * are sourced from {@code repository.full_name} (or {@code organization.login} for
 * org-tier events) and {@code <event>} comes from the {@code X-GitHub-Event} header.
 * Missing or blank tokens collapse to the literal {@code ?} placeholder; dots inside
 * any token are rewritten to {@code ~} to preserve the four-segment dot-delimited
 * subject contract.
 *
 * <p>Dedup keys reuse GitHub's {@code X-GitHub-Delivery} UUID (prefixed with
 * {@code github-}); when the header is absent the key falls back to a SHA-256 hex
 * digest of {@code body || event} truncated to 32 hex characters — JetStream's
 * {@code Nats-Msg-Id} dedup gives us idempotency without per-request state.
 */
@Component
public class GithubSubjectKeyDeriver implements SubjectKeyDeriver {

    private static final String HEADER_EVENT = "X-GitHub-Event";
    private static final String HEADER_DELIVERY = "X-GitHub-Delivery";
    private static final String PLACEHOLDER = "?";
    private static final String SUBJECT_PREFIX = "github.";

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public String deriveSubject(JsonNode payload, Map<String, String> headers) {
        String event = headerCaseInsensitive(headers, HEADER_EVENT);
        String eventSegment = sanitize(event == null || event.isBlank() ? PLACEHOLDER : event);

        String org = PLACEHOLDER;
        String repo = PLACEHOLDER;
        if (payload != null) {
            JsonNode repository = payload.path("repository");
            JsonNode organization = payload.path("organization");
            if (!repository.isMissingNode() && !repository.isNull()) {
                JsonNode owner = repository.path("owner");
                org = sanitize(orPlaceholder(owner.path("login").asString("")));
                repo = sanitize(orPlaceholder(repository.path("name").asString("")));
            } else if (!organization.isMissingNode() && !organization.isNull()) {
                org = sanitize(orPlaceholder(organization.path("login").asString("")));
            }
        }
        return SUBJECT_PREFIX + org + "." + repo + "." + eventSegment;
    }

    @Override
    public String deriveDedupKey(byte[] body, Map<String, String> headers) {
        String deliveryId = headerCaseInsensitive(headers, HEADER_DELIVERY);
        if (deliveryId != null && !deliveryId.isBlank()) {
            return "github-" + deliveryId;
        }
        String event = headerCaseInsensitive(headers, HEADER_EVENT);
        return "github-" + sha256TruncatedHex(body, event);
    }

    private static String sanitize(String value) {
        if (value == null || value.isEmpty()) return PLACEHOLDER;
        return value.replace('.', '~');
    }

    private static String orPlaceholder(String value) {
        return value == null || value.isEmpty() ? PLACEHOLDER : value;
    }

    private static String headerCaseInsensitive(Map<String, String> headers, String name) {
        if (headers == null) return null;
        String direct = headers.get(name);
        if (direct != null) return direct;
        for (var entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String sha256TruncatedHex(byte[] body, String event) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            if (body != null) md.update(body);
            md.update((byte) '|');
            if (event != null) md.update(event.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(md.digest());
            return hex.length() <= 32 ? hex : hex.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JRE — this is unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
