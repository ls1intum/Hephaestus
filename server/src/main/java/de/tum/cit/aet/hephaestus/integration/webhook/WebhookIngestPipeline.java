package de.tum.cit.aet.hephaestus.integration.webhook;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.integration.webhook.JetStreamPublisher;
import de.tum.cit.aet.hephaestus.integration.webhook.PublishRequest;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectKeyDeriver;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier.VerificationResult;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier.WebhookRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Inbound webhook pipeline: verify → derive → publish.
 *
 * <p>Verification can short-circuit with {@code RespondImmediately} (Slack
 * {@code url_verification}, Asana {@code X-Hook-Secret} echo); on {@code Verified} the
 * pipeline derives the NATS subject + dedup-id via the per-kind {@link SubjectKeyDeriver}
 * and publishes through {@link JetStreamPublisher}.
 *
 * <p>Error responses are opaque ({@code "invalid"} / {@code "missing-signature"} /
 * {@code "stale-timestamp"}). The {@code Invalid.reason} from the verifier is logged
 * server-side only — echoing it would leak signature-format detail to attacker probes.
 */
@Component
public class WebhookIngestPipeline {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestPipeline.class);

    static final String NATS_MSG_ID = "Nats-Msg-Id";

    private final Map<IntegrationKind, WebhookSignatureVerifier> verifiersByKind;
    private final Map<IntegrationKind, SubjectKeyDeriver> deriversByKind;
    @Nullable
    private final JetStreamPublisher jetStreamPublisher;
    private final ObjectMapper objectMapper;

    public WebhookIngestPipeline(
        List<WebhookSignatureVerifier> verifiers,
        List<SubjectKeyDeriver> derivers,
        @Nullable JetStreamPublisher jetStreamPublisher,
        ObjectMapper objectMapper
    ) {
        this.verifiersByKind = verifiers.stream()
            .collect(Collectors.toUnmodifiableMap(
                WebhookSignatureVerifier::kind,
                Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException(
                        "Duplicate WebhookSignatureVerifier for kind=" + a.kind()
                    );
                }
            ));
        this.deriversByKind = derivers.stream()
            .collect(Collectors.toUnmodifiableMap(
                SubjectKeyDeriver::kind,
                Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException(
                        "Duplicate SubjectKeyDeriver for kind=" + a.kind()
                    );
                }
            ));
        this.jetStreamPublisher = jetStreamPublisher;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<?> handle(IntegrationKind kind, HttpServletRequest req) throws IOException {
        byte[] body = req.getInputStream().readAllBytes();
        Map<String, String> headers = readHeaders(req);
        return handle(kind, body, headers);
    }

    /**
     * Pre-read overload — useful when the body has already been consumed (tests,
     * future re-entrant call sites). The {@code /webhooks/{kind}} controller path
     * uses the {@link HttpServletRequest} overload above.
     */
    public ResponseEntity<?> handle(IntegrationKind kind, byte[] body, Map<String, String> headers) {
        WebhookSignatureVerifier verifier = verifiersByKind.get(kind);
        if (verifier == null) {
            // No verifier wired — kind is allow-listed but not yet implemented.
            log.warn("No WebhookSignatureVerifier registered for kind={}; rejecting", kind);
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", "no verifier wired for " + kind));
        }

        WebhookRequest request = new WebhookRequest(body, headers);
        VerificationResult result;
        try {
            result = verifier.verify(request);
        } catch (RuntimeException e) {
            log.warn("Verifier {} threw for kind={}: {}", verifier.getClass().getSimpleName(), kind, e.toString());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid"));
        }

        return switch (result) {
            case VerificationResult.Verified v -> publish(kind, body, headers);
            case VerificationResult.RespondImmediately r -> respondImmediately(r);
            case VerificationResult.StaleTimestamp s -> {
                log.debug("Webhook rejected for kind={}: stale timestamp drift={}s", kind, s.driftSeconds());
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "stale-timestamp"));
            }
            case VerificationResult.Invalid i -> {
                // Server-side log carries the discriminator; HTTP response stays opaque so
                // attacker probes cannot distinguish missing-secret from signature-mismatch
                // from malformed-header (side channel into the signing scheme).
                log.warn("Webhook rejected for kind={}: {}", kind, i.reason());
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid"));
            }
            case VerificationResult.MissingSignature ms -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "missing-signature"));
        };
    }

    private ResponseEntity<?> publish(IntegrationKind kind, byte[] body, Map<String, String> headers) {
        if (jetStreamPublisher == null) {
            // No publisher bean — the webhook runtime role is disabled. Vendor will retry.
            // 503 is the right surface here: the verification succeeded, but the downstream
            // pipe is intentionally not wired on this pod.
            log.warn(
                "WebhookIngestPipeline: verified {} webhook but no JetStreamPublisher bean — replying 503",
                kind
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "publisher not wired"));
        }
        SubjectKeyDeriver deriver = deriversByKind.get(kind);
        if (deriver == null) {
            // Kind has a verifier but no derivation — surface as NOT_IMPLEMENTED so the
            // operator notices the gap rather than silently dropping into a default subject.
            log.warn("WebhookIngestPipeline: no SubjectKeyDeriver wired for kind={}", kind);
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", "no subject deriver for " + kind));
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "invalid-json"));
        }

        String subject = deriver.deriveSubject(payload, headers);
        String dedupId = deriver.deriveDedupKey(body, headers);

        // Pass the vendor event header through unchanged when present, and ALWAYS
        // attach the Nats-Msg-Id for server-side dedup. JetStreamPublisher already
        // echoes the latter through its PublishOptions; including it on the headers
        // keeps the wire trace self-describing.
        Map<String, String> outboundHeaders = new LinkedHashMap<>();
        passthroughHeader(outboundHeaders, headers, "X-GitHub-Event");
        passthroughHeader(outboundHeaders, headers, "X-GitHub-Delivery");
        passthroughHeader(outboundHeaders, headers, "X-Gitlab-Event");
        passthroughHeader(outboundHeaders, headers, "X-Gitlab-Webhook-UUID");
        outboundHeaders.put(NATS_MSG_ID, dedupId);

        try {
            jetStreamPublisher.publish(new PublishRequest(subject, dedupId, outboundHeaders, body));
        } catch (JetStreamPublisher.PublishFailedException e) {
            log.error(
                "WebhookIngestPipeline: publish failed for kind={} subject={}: {}",
                kind, sanitizeForLog(subject), e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "publish-failed"));
        }
        log.info("Published {} webhook to NATS: subject={} dedupId={}", kind, sanitizeForLog(subject), sanitizeForLog(dedupId));
        return ResponseEntity.accepted().body(Map.of("status", "ok"));
    }

    private static void passthroughHeader(Map<String, String> out, Map<String, String> in, String name) {
        String value = headerCaseInsensitive(in, name);
        if (value != null && !value.isBlank()) {
            out.put(name, value);
        }
    }

    @Nullable
    private static String headerCaseInsensitive(Map<String, String> headers, String name) {
        String direct = headers.get(name);
        if (direct != null) return direct;
        for (var entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private ResponseEntity<?> respondImmediately(VerificationResult.RespondImmediately r) {
        var resp = ResponseEntity.status(r.statusCode())
            .contentType(MediaType.parseMediaType(r.contentType()));
        for (Map.Entry<String, String> h : r.headers().entrySet()) {
            resp = resp.header(h.getKey(), h.getValue());
        }
        return resp.body(r.body());
    }

    private static Map<String, String> readHeaders(HttpServletRequest req) {
        Map<String, String> headers = new LinkedHashMap<>();
        var names = req.getHeaderNames();
        if (names == null) return Collections.unmodifiableMap(headers);
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, req.getHeader(name));
        }
        return Collections.unmodifiableMap(headers);
    }

    @Nullable
    public WebhookSignatureVerifier verifierFor(IntegrationKind kind) {
        return verifiersByKind.get(kind);
    }
}
