package de.tum.in.www1.hephaestus.practices;

import jakarta.validation.Valid;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for detection tracing.
 *
 * <p>Binds to the {@code hephaestus.detection} prefix in application configuration.
 * Controls Langfuse tracing integration for bad practice feedback.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   detection:
 *     tracing:
 *       enabled: true
 *       host: https://cloud.langfuse.com
 *       public-key: pk-xxx
 *       secret-key: sk-xxx
 * }</pre>
 *
 * @param tracing Langfuse tracing configuration for LLM observability
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.detection")
public record DetectionProperties(@Valid Tracing tracing) {
    /**
     * Compact constructor ensuring nested records are never null.
     */
    public DetectionProperties {
        if (tracing == null) {
            tracing = new Tracing(false, null, null, null);
        }
    }

    /**
     * Langfuse tracing configuration for LLM observability.
     *
     * <p>When enabled, feedback on bad practice detections is sent to Langfuse
     * for model improvement and observability.
     *
     * @param enabled   whether Langfuse tracing is enabled
     * @param host      Langfuse API host URL (e.g., https://cloud.langfuse.com)
     * @param publicKey Langfuse public API key
     * @param secretKey Langfuse secret API key
     */
    public record Tracing(
        @DefaultValue("false") boolean enabled,
        @Nullable @URL(message = "Tracing host must be a valid URL if provided") String host,
        @Nullable String publicKey,
        @Nullable String secretKey
    ) {}
}
