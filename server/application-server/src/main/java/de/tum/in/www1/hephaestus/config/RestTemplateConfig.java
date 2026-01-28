package de.tum.in.www1.hephaestus.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate beans used by various services.
 * <p>
 * Using named beans allows for proper mocking in integration tests,
 * preventing any live HTTP calls during CI.
 */
@Configuration
public class RestTemplateConfig {

    /**
     * RestTemplate for fetching raw content from GitHub (e.g., PR templates).
     * <p>
     * In tests, this bean should be mocked using {@code @MockitoBean} to prevent
     * actual HTTP calls to raw.githubusercontent.com.
     */
    @Bean("gitHubRawRestTemplate")
    public RestTemplate gitHubRawRestTemplate(RestTemplateBuilder builder) {
        return builder.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(10)).build();
    }
}
