package de.tum.cit.aet.hephaestus.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a Jackson 2 {@link ObjectMapper} bean.
 *
 * <p>Spring Boot 4 auto-configures {@code tools.jackson.databind.ObjectMapper} (Jackson 3)
 * as the primary mapper. Several #1198 integration framework classes
 * ({@code WebhookIngestPipeline}, {@code SyncOrchestrator}, the registry converters,
 * Outline/Slack signature verifiers) still depend on Jackson 2's
 * {@code com.fasterxml.jackson.databind.ObjectMapper} for {@code @JsonTypeInfo} polymorphic
 * sealed-type serialisation of {@code ConnectionConfig} / {@code CredentialBundle}.
 * Until a coordinated Jackson 2→3 migration of the integration module lands, this bean
 * provides a wired instance with the same configuration as the production Jackson 3 setup
 * (JSR-310, JDK8, ignore-unknown).
 */
@Configuration
public class Jackson2ObjectMapperConfig {

    @Bean
    public ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
