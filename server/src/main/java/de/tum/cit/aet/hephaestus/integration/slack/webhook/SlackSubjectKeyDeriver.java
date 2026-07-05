package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SubjectKeyDeriver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Slack adapter for {@link SubjectKeyDeriver}. Produces JetStream subjects of the shape
 * {@code slack.<team>.<channel>.message} from the {@code event_callback} envelope
 * ({@code team_id} at the root, {@code event.channel} inside), mirroring the
 * {@code owner/repo/event} grammar of the SCM derivers. Dots inside a token are rewritten to
 * {@code ~} and a blank token collapses to the literal {@code ?} placeholder, exactly as the
 * SCM derivers do, so the four-segment dot-delimited subject contract that
 * {@link de.tum.cit.aet.hephaestus.integration.core.consumer.ConsumerSubjectMath#buildSubjectPrefix}
 * subscribes to always holds. The {@link SubjectGrammarRoundTripTest round-trip test} pins the
 * producer↔consumer agreement.
 *
 * <p>The {@code Nats-Msg-Id} dedup key is {@code slack-<event_id>} — Slack stamps a globally
 * unique {@code event_id} on every {@code event_callback} and redelivers the SAME id when a
 * delivery is not acked, so JetStream's server-side {@code Nats-Msg-Id} window collapses
 * duplicate deliveries with zero per-request state — Slack ingest needs no bespoke dedup table.
 * When {@code event_id} is absent the key falls back to a SHA-256 digest of {@code team|channel|ts}.
 *
 * <p>Slack ingest does NOT flow through {@code /webhooks/{kind}} (the events endpoint verifies
 * its own v0 HMAC and must fast-classify DM vs channel), so the {@code SubjectKeyDeriver} SPI
 * methods here are exercised by {@code SlackChannelEventPublisher} via the {@code JsonNode}
 * overloads; the {@code byte[]} SPI method is provided for contract-completeness.
 */
@Component
public class SlackSubjectKeyDeriver implements SubjectKeyDeriver {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final String PLACEHOLDER = "?";
    private static final String SUBJECT_PREFIX = "slack.";
    private static final String EVENT_SEGMENT = "message";

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public String deriveSubject(JsonNode payload, Map<String, String> headers) {
        return subjectFor(payload);
    }

    @Override
    public String deriveDedupKey(byte[] body, Map<String, String> headers) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (RuntimeException e) {
            return "slack-" + sha256TruncatedHex(body);
        }
        return dedupIdFor(root);
    }

    /**
     * Pure subject builder from the parsed {@code event_callback} envelope:
     * {@code slack.<team>.<channel>.message}. Used directly by the producer.
     */
    public String subjectFor(JsonNode root) {
        String team = sanitize(root.path("team_id").asString(""));
        String channel = sanitize(root.path("event").path("channel").asString(""));
        return SUBJECT_PREFIX + team + "." + channel + "." + EVENT_SEGMENT;
    }

    /**
     * Pure {@code Nats-Msg-Id} builder: {@code slack-<event_id>}, or a stable hash of
     * {@code team|channel|ts} when {@code event_id} is absent. Used directly by the producer.
     */
    public String dedupIdFor(JsonNode root) {
        String eventId = root.path("event_id").asString("");
        if (!eventId.isBlank()) {
            return "slack-" + eventId;
        }
        String team = root.path("team_id").asString("");
        JsonNode event = root.path("event");
        String composite = team + "|" + event.path("channel").asString("") + "|" + event.path("ts").asString("");
        return "slack-" + sha256TruncatedHex(composite.getBytes(StandardCharsets.UTF_8));
    }

    private static String sanitize(String value) {
        if (value == null || value.isEmpty()) return PLACEHOLDER;
        return value.replace('.', '~');
    }

    private static String sha256TruncatedHex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            if (data != null) md.update(data);
            String hex = HexFormat.of().formatHex(md.digest());
            return hex.length() <= 32 ? hex : hex.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JRE — this is unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
