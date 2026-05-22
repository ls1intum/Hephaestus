package de.tum.cit.aet.hephaestus.core.runtime.worker;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;

/**
 * Bound to {@code hephaestus.worker.*}. Mirrors the nested-record shape of
 * {@link de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties}.
 *
 * <p>{@code capacity.reviewMax} / {@code capacity.mentorMax} accept either an integer string or
 * the literal {@code "auto"}, resolved at runtime against {@link Runtime#availableProcessors()}.
 * Resolution lives on {@link Capacity} so it stays a pure record-to-int derivation independent
 * of Spring lifecycle.
 *
 * <p>{@code control.registrationToken} is logged at {@code <redacted>} via the overridden
 * {@link #toString()} to keep accidental {@code log.info("config: {}", props)} from leaking it.
 */
@ConfigurationProperties(prefix = "hephaestus.worker")
public record WorkerProperties(
    @Nullable String workerId,
    @DefaultValue Capacity capacity,
    @DefaultValue Drain drain,
    @DefaultValue Heartbeat heartbeat,
    @DefaultValue Control control,
    @DefaultValue Llm llm
) {
    /** Literal that triggers auto-derivation from {@link Runtime#availableProcessors()}. */
    public static final String AUTO = "auto";

    /**
     * @return the configured worker id, or the OS hostname when unset. Hostname is the
     *     compose-/K8s-injected pod identity, so replicas don't cannibalise each other in
     *     {@code WorkerSessionRegistry} (which evicts on workerId collision).
     */
    public String resolvedWorkerId() {
        if (workerId != null && !workerId.isBlank()) {
            return workerId;
        }
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            if (hostname != null && !hostname.isBlank()) {
                return hostname;
            }
        } catch (UnknownHostException ignored) {
            // fall through
        }
        return "worker-" + Long.toHexString(System.nanoTime());
    }

    @Override
    public String toString() {
        return (
            "WorkerProperties[workerId=" + workerId +
                ", capacity=" + capacity +
                ", drain=" + drain +
                ", heartbeat=" + heartbeat +
                ", control=" + control +
                ", llm=" + (llm.isConfigured() ? "<configured>" : "<unset>") +
                "]"
        );
    }

    /**
     * BYO / worker-pod LLM credentials. When both fields are set, {@link
     * de.tum.cit.aet.hephaestus.agent.job.AgentJobExecutor} switches the per-job credential mode
     * to {@code API_KEY} and points the agent at {@link #baseUrl()} directly — bypassing the
     * app-pod's bundled LLM proxy (which doesn't exist on a worker host). This dilutes ADR 0006
     * by one secret per worker pod; mitigate with secrets-manager + short rotation TTL.
     */
    public record Llm(@Nullable String baseUrl, @Nullable String apiKey) {
        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
        }
    }

    public record Capacity(@DefaultValue("auto") String reviewMax, @DefaultValue("auto") String mentorMax) {
        /** @return resolved review-max; {@code "auto"} → {@code max(1, cpu - 1)}. */
        public int resolveReviewMax() {
            return resolve(reviewMax, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        }

        /** @return resolved mentor-max; {@code "auto"} → {@code max(1, cpu / 2)}. */
        public int resolveMentorMax() {
            return resolve(mentorMax, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        }

        private static int resolve(String configured, int autoDefault) {
            if (configured == null || configured.isBlank() || AUTO.equalsIgnoreCase(configured.trim())) {
                return autoDefault;
            }
            try {
                int parsed = Integer.parseInt(configured.trim());
                if (parsed < 0) {
                    throw new IllegalArgumentException("capacity must be >= 0 or 'auto', got: " + configured);
                }
                return parsed;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("capacity must be an integer or 'auto', got: " + configured, e);
            }
        }
    }

    public record Drain(@DefaultValue("5m") Duration timeout) {
        public Drain {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("drain.timeout must be >= 0, got: " + timeout);
            }
        }
    }

    public record Heartbeat(@DefaultValue("20s") Duration interval) {
        public Heartbeat {
            if (interval == null || interval.isZero() || interval.isNegative()) {
                throw new IllegalArgumentException("heartbeat.interval must be positive, got: " + interval);
            }
        }
    }

    public record Control(
        @Nullable URI endpoint,
        @Nullable String registrationToken,
        @DefaultValue("10s") Duration handshakeTimeout
    ) {
        public Control {
            if (handshakeTimeout == null || handshakeTimeout.isZero() || handshakeTimeout.isNegative()) {
                throw new IllegalArgumentException("control.handshakeTimeout must be positive, got: " + handshakeTimeout);
            }
        }

        @Override
        public String toString() {
            return (
                "Control[endpoint=" + endpoint +
                    ", registrationToken=" +
                    (registrationToken == null || registrationToken.isBlank() ? "<unset>" : "<redacted>") +
                    ", handshakeTimeout=" + handshakeTimeout +
                    "]"
            );
        }

        /** @return {@code true} iff the worker has enough config to attempt a WSS dial. */
        public boolean isConfigured() {
            return (
                endpoint != null &&
                    registrationToken != null &&
                    !registrationToken.isBlank()
            );
        }
    }
}
