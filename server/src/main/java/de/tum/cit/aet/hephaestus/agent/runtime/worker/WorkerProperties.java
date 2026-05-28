package de.tum.cit.aet.hephaestus.agent.runtime.worker;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;

/**
 * Bound to {@code hephaestus.worker.*}. {@code capacity.{review,mentor}Max} accept an integer or
 * the literal {@code "auto"}; resolution lives on {@link Capacity}. {@code toString()} redacts
 * the registration token.
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
    public static final String AUTO = "auto";

    /**
     * @return the configured worker id, or the container hostname when unset (so compose/K8s
     *     replicas don't collide in {@code WorkerSessionRegistry}).
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
            "WorkerProperties[workerId=" +
            workerId +
            ", capacity=" +
            capacity +
            ", drain=" +
            drain +
            ", heartbeat=" +
            heartbeat +
            ", control=" +
            control +
            ", llm=" +
            (llm.isConfigured() ? "<configured>" : "<unset>") +
            "]"
        );
    }

    /**
     * BYO LLM credentials. When both fields are set, {@code AgentJobExecutor} overrides the
     * per-job credential mode to {@code API_KEY} so agent-pi reaches the operator's LLM
     * directly (the app-pod's bundled proxy is not reachable from a separate host).
     */
    public record Llm(@Nullable String baseUrl, @Nullable String apiKey) {
        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
        }
    }

    public record Capacity(@DefaultValue("auto") String reviewMax, @DefaultValue("auto") String mentorMax) {
        public int resolveReviewMax() {
            return resolve(reviewMax, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        }

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
                throw new IllegalArgumentException(
                    "control.handshakeTimeout must be positive, got: " + handshakeTimeout
                );
            }
        }

        @Override
        public String toString() {
            return (
                "Control[endpoint=" +
                endpoint +
                ", registrationToken=" +
                (registrationToken == null || registrationToken.isBlank() ? "<unset>" : "<redacted>") +
                ", handshakeTimeout=" +
                handshakeTimeout +
                "]"
            );
        }

        /** @return {@code true} iff the worker has enough config to attempt a WSS dial. */
        public boolean isConfigured() {
            return (endpoint != null && registrationToken != null && !registrationToken.isBlank());
        }
    }
}
