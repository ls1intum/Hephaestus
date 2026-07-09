package de.tum.cit.aet.hephaestus.core.webhook;

import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Shared webhook configuration bound to {@code hephaestus.webhook.*}. The same {@code secret} is
 * used by auto-registration ({@code workspace.GitLabWebhookService} → sent to provider) and
 * verification ({@code integration.webhook.*} → matched against the incoming HMAC/token). See
 * ADR 0008.
 *
 * <p>Incoming request size is capped at the {@code WebhookPayloadSizeFilter} before Spring
 * buffers the body — {@code server.tomcat.max-http-post-size} only enforces on form-encoded
 * payloads, so we don't rely on it for JSON webhooks.
 */
@ConfigurationProperties(prefix = "hephaestus.webhook")
public record WebhookProperties(
    @Nullable String externalUrl,
    @Nullable String secret,
    @DefaultValue TokenRotation tokenRotation,
    @DefaultValue Publish publish,
    @DefaultValue Stream stream,
    @DefaultValue Shutdown shutdown,
    @DefaultValue Http http
) {
    /** Minimum HMAC-SHA256 secret length recommended by NIST SP 800-107. */
    public static final int MIN_SECRET_LENGTH = 32;

    /** {@code true} iff auto-registration with the provider can be attempted. Pure predicate — no side effects. */
    public boolean isConfigured() {
        return (
            externalUrl != null &&
            !externalUrl.isBlank() &&
            secret != null &&
            !secret.isBlank() &&
            secret.length() >= MIN_SECRET_LENGTH
        );
    }

    /** Redacts {@code secret} so accidental {@code log.info("config: {}", props)} doesn't leak it. */
    @Override
    public String toString() {
        return (
            "WebhookProperties[externalUrl=" +
            externalUrl +
            ", secret=" +
            (secret == null || secret.isBlank() ? "<unset>" : "<redacted>") +
            ", tokenRotation=" +
            tokenRotation +
            ", publish=" +
            publish +
            ", stream=" +
            stream +
            ", shutdown=" +
            shutdown +
            ", http=" +
            http +
            "]"
        );
    }

    public record TokenRotation(@DefaultValue("7") int thresholdDays, @DefaultValue("90") int validityDays) {
        public TokenRotation {
            if (thresholdDays < 0) {
                throw new IllegalArgumentException("tokenRotation.thresholdDays must be >= 0, got: " + thresholdDays);
            }
            if (validityDays < 1) {
                throw new IllegalArgumentException("tokenRotation.validityDays must be >= 1, got: " + validityDays);
            }
        }
    }

    public record Publish(
        @DefaultValue("9s") Duration timeout,
        @DefaultValue("5") int maxRetries,
        @DefaultValue("200ms") Duration retryBaseDelay
    ) {
        public Publish {
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("publish.timeout must be positive, got: " + timeout);
            }
            if (maxRetries < 1) {
                throw new IllegalArgumentException("publish.maxRetries must be >= 1, got: " + maxRetries);
            }
            if (retryBaseDelay.isZero() || retryBaseDelay.isNegative()) {
                throw new IllegalArgumentException("publish.retryBaseDelay must be positive, got: " + retryBaseDelay);
            }
        }
    }

    public record Stream(
        // Replay-defense invariant: the JetStream dedup window MUST be >= the largest per-vendor
        // timestamp replay tolerance, otherwise a captured-but-still-timestamp-valid request can be
        // replayed once its dedup entry has expired (the 2-5 min band). The widest vendor tolerance
        // is 5m (GitLab whsec TIMESTAMP_TOLERANCE, Slack v0 MAX_DRIFT_SECONDS); we set 10m to also
        // cover GitHub — which has NO timestamp, so the dedup window keyed on X-GitHub-Delivery is
        // its ONLY replay defense — plus provider redelivery horizons. See REPLAY_TOLERANCE_FLOOR.
        @DefaultValue("10m") Duration duplicateWindow,
        @DefaultValue("180d") Duration maxAge,
        // Per-stream retention overrides keyed by stream name (e.g. slack: 72h). Streams whose payloads carry
        // personal message content should expire quickly once consumed — the SQL substrate is the system of
        // record, and GDPR erasure cannot reach inside a broker stream.
        @DefaultValue Map<String, Duration> maxAgeByStream,
        @DefaultValue("2000000") long maxMessages
    ) {
        /**
         * Lower bound for {@link #duplicateWindow}: the maximum per-vendor timestamp replay
         * tolerance across all webhook verifiers (GitLab whsec + Slack v0 both use 5 minutes).
         * The dedup window must be at least this large so a request can never outlive its dedup
         * entry while still being timestamp-valid. GitHub deliveries carry no timestamp at all,
         * so for them the dedup window is the sole replay defense and a larger value is safer.
         */
        public static final Duration REPLAY_TOLERANCE_FLOOR = Duration.ofMinutes(5);

        public Stream {
            if (duplicateWindow.isZero() || duplicateWindow.isNegative()) {
                throw new IllegalArgumentException("stream.duplicateWindow must be positive, got: " + duplicateWindow);
            }
            if (duplicateWindow.compareTo(REPLAY_TOLERANCE_FLOOR) < 0) {
                throw new IllegalArgumentException(
                    "stream.duplicateWindow (" +
                        duplicateWindow +
                        ") must be >= the max vendor replay tolerance (" +
                        REPLAY_TOLERANCE_FLOOR +
                        ") so a timestamp-valid request cannot outlive its dedup entry"
                );
            }
            if (maxAge.isZero() || maxAge.isNegative()) {
                throw new IllegalArgumentException("stream.maxAge must be positive, got: " + maxAge);
            }
            maxAgeByStream = maxAgeByStream == null ? Map.of() : Map.copyOf(maxAgeByStream);
            for (Map.Entry<String, Duration> e : maxAgeByStream.entrySet()) {
                Duration v = e.getValue();
                if (v == null || v.isZero() || v.isNegative()) {
                    throw new IllegalArgumentException(
                        "stream.maxAgeByStream." + e.getKey() + " must be positive, got: " + v
                    );
                }
                if (v.compareTo(duplicateWindow) < 0) {
                    throw new IllegalArgumentException(
                        "stream.maxAgeByStream." +
                            e.getKey() +
                            " (" +
                            v +
                            ") must be >= duplicateWindow (" +
                            duplicateWindow +
                            ") or the dedup guarantee is meaningless"
                    );
                }
            }
            if (maxMessages < 1) {
                throw new IllegalArgumentException("stream.maxMessages must be >= 1, got: " + maxMessages);
            }
        }

        /** Effective retention for one stream: the per-stream override, else the shared {@link #maxAge}. */
        public Duration maxAgeFor(String streamName) {
            return maxAgeByStream.getOrDefault(streamName, maxAge);
        }
    }

    /**
     * Graceful-shutdown drain budget for in-flight publishes after HTTP closes. The relationship
     * to Docker's {@code stop_grace_period} is:
     * {@code stop_grace_period ≥ server.shutdown=graceful timeout + drainTimeout + margin}.
     */
    public record Shutdown(@DefaultValue("15s") Duration drainTimeout) {
        public Shutdown {
            if (drainTimeout.isZero() || drainTimeout.isNegative()) {
                throw new IllegalArgumentException("shutdown.drainTimeout must be positive, got: " + drainTimeout);
            }
        }
    }

    /**
     * Enforced by {@code WebhookPayloadSizeFilter} via {@code Content-Length} header. Tomcat's
     * {@code max-http-post-size} only caps form bodies, so it can't enforce JSON request size on
     * its own.
     */
    public record Http(@DefaultValue("26214400") long maxPayloadBytes) {
        public Http {
            if (maxPayloadBytes < 1) {
                throw new IllegalArgumentException("http.maxPayloadBytes must be >= 1, got: " + maxPayloadBytes);
            }
        }
    }
}
