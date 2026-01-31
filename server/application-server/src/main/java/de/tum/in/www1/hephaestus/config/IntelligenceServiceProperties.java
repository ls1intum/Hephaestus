package de.tum.in.www1.hephaestus.config;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for the intelligence service connection.
 *
 * <p>Binds to the {@code hephaestus.intelligence-service} prefix in application configuration.
 * Used to configure communication with the intelligence service for mentor and
 * bad practice detection features.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   intelligence-service:
 *     url: http://localhost:8081
 * }</pre>
 *
 * @param url the base URL of the intelligence service (required, must be a valid URL)
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.intelligence-service")
public record IntelligenceServiceProperties(
    @NotBlank(message = "Intelligence service URL must not be blank")
    @URL(message = "Intelligence service URL must be a valid URL")
    String url
) {}
