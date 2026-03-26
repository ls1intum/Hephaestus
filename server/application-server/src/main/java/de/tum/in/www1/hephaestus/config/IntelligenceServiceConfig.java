package de.tum.in.www1.hephaestus.config;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Intelligence Service API clients.
 *
 * <p>Provides Spring beans for the generated OpenAPI client classes that communicate
 * with the intelligence service.
 */
@Configuration
public class IntelligenceServiceConfig {

    private final IntelligenceServiceProperties intelligenceServiceProperties;

    public IntelligenceServiceConfig(IntelligenceServiceProperties intelligenceServiceProperties) {
        this.intelligenceServiceProperties = intelligenceServiceProperties;
    }

    @Bean
    public ApiClient intelligenceApiClient() {
        return new ApiClient().setBasePath(intelligenceServiceProperties.url());
    }
}
