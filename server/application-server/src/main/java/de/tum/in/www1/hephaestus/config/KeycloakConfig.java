package de.tum.in.www1.hephaestus.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakConfig {

    private final String authServerUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakConfig(
        @Value("${keycloak.url}") String authServerUrl,
        @Value("${keycloak.realm}") String realm,
        @Value("${keycloak.client-id}") String clientId,
        @Value("${keycloak.client-secret}") String clientSecret
    ) {
        this.authServerUrl = authServerUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Bean
    public Keycloak keycloakClient() {
        return KeycloakBuilder.builder()
            .serverUrl(authServerUrl)
            .realm(realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build();
    }
}
