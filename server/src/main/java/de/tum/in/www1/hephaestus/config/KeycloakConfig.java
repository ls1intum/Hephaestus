package de.tum.in.www1.hephaestus.config;

import java.time.Clock;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakConfig {

    private final KeycloakProperties keycloakProperties;

    public KeycloakConfig(KeycloakProperties keycloakProperties) {
        this.keycloakProperties = keycloakProperties;
    }

    @Bean
    public Keycloak keycloakClient() {
        return KeycloakBuilder.builder()
            .serverUrl(keycloakProperties.effectiveInternalUrl())
            .realm(keycloakProperties.realm())
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(keycloakProperties.clientId())
            .clientSecret(keycloakProperties.clientSecret())
            .build();
    }

    /**
     * Provides a system UTC clock for production use.
     * Tests can override this by defining their own Clock bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
