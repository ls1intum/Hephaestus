package de.tum.cit.aet.hephaestus.core.webhook;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;

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
        @DefaultValue("2m") Duration duplicateWindow,
        @DefaultValue("180d") Duration maxAge,
        @DefaultValue("2000000") long maxMessages
    ) {
        public Stream {
            if (duplicateWindow.isZero() || duplicateWindow.isNegative()) {
                throw new IllegalArgumentException("stream.duplicateWindow must be positive, got: " + duplicateWindow);
            }
            if (maxAge.isZero() || maxAge.isNegative()) {
                throw new IllegalArgumentException("stream.maxAge must be positive, got: " + maxAge);
            }
            if (maxMessages < 1) {
                throw new IllegalArgumentException("stream.maxMessages must be >= 1, got: " + maxMessages);
            }
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
