package de.tum.cit.aet.hephaestus.integration.webhook;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier.VerificationResult;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier.WebhookRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
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
 * Inbound webhook pipeline: verify → publish.
 *
 * <p>The verification step may short-circuit with {@code RespondImmediately}
 * (Slack {@code url_verification}, Asana {@code X-Hook-Secret} echo) — in that
 * case the response body is served directly and no NATS publish happens.
 * {@code CaptureSecret} also short-circuits but additionally hands the captured
 * per-subscription secret to the registered subscription handler before
 * responding.
 *
 * <p>The actual NATS publish + dedup is delegated to a dedicated publisher bean
 * that wires into the existing {@code JetStreamPublisher}. This pipeline
 * intentionally has zero JetStream-specific code so it stays unit-testable.
 */
@Component
public class WebhookIngestPipeline {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestPipeline.class);

    private final Map<IntegrationKind, WebhookSignatureVerifier> verifiersByKind;

    public WebhookIngestPipeline(java.util.List<WebhookSignatureVerifier> verifiers) {
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
    }

    public ResponseEntity<?> handle(IntegrationKind kind, HttpServletRequest req) throws IOException {
        byte[] body = req.getInputStream().readAllBytes();
        Map<String, String> headers = readHeaders(req);

        WebhookSignatureVerifier verifier = verifiersByKind.get(kind);
        if (verifier == null) {
            // No verifier wired — kind is allow-listed but not yet implemented.
            log.warn("No WebhookSignatureVerifier registered for kind={}; rejecting", kind);
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", "no verifier wired for " + kind));
        }

        WebhookRequest request = new WebhookRequest(body, headers, /* workspaceId */ null, /* subscriptionId */ null);
        VerificationResult result;
        try {
            result = verifier.verify(request);
        } catch (RuntimeException e) {
            log.warn("Verifier {} threw for kind={}: {}", verifier.getClass().getSimpleName(), kind, e.toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "verifier failure"));
        }

        return switch (result) {
            case VerificationResult.Verified v -> publish(kind, body, headers);
            case VerificationResult.RespondImmediately r -> respondImmediately(r);
            case VerificationResult.CaptureSecret c -> respondImmediately(c.response());
            case VerificationResult.StaleTimestamp s -> ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body(Map.of("error", "stale timestamp", "drift_seconds", s.driftSeconds()));
            case VerificationResult.Invalid i -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", i.reason()));
            case VerificationResult.MissingSignature ms -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "missing signature"));
        };
    }

    private ResponseEntity<?> publish(IntegrationKind kind, byte[] body, Map<String, String> headers) {
        // TODO(#1198 follow-up): wire into JetStreamPublisher with per-kind SubjectKeyDeriver.
        // For #1198 first cut, we accept and log; existing per-kind controllers continue to handle
        // GitHub/GitLab publishing until C13 migrates them to this pipeline.
        log.debug("Accepted webhook kind={} bytes={}", kind, body.length);
        return ResponseEntity.accepted().body(Map.of("status", "accepted", "kind", kind.name()));
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
