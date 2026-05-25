package de.tum.cit.aet.hephaestus.integration.spi;

import tools.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Per-kind NATS subject + dedup-key derivation on the publisher side.
 *
 * <p>Subjects stay vendor-prefixed ({@code github.*}, {@code gitlab.*}, etc.). Adding
 * a new kind = registering a new {@link SubjectKeyDeriver} bean keyed on
 * {@link #kind()}.
 */
public interface SubjectKeyDeriver {

    IntegrationKind kind();

    /** Builds the JetStream subject from payload + headers. */
    String deriveSubject(JsonNode payload, Map<String, String> headers);

    /**
     * Builds the JetStream {@code Nats-Msg-Id} from the raw body + headers.
     *
     * <p>Per-vendor strategy:
     * <ul>
     *   <li>GitHub: {@code "github-" + X-GitHub-Delivery}
     *   <li>GitLab: {@code "gitlab-" + (Idempotency-Key | X-Gitlab-Event-UUID | sha256)}
     *   <li>Slack: {@code "slack-" + X-Slack-Request-Id}
     *   <li>Outline: {@code "outline-" + <webhook id>}
     * </ul>
     */
    String deriveDedupKey(byte[] body, Map<String, String> headers);
}
