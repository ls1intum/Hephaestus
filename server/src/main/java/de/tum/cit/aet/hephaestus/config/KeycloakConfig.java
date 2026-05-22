package de.tum.cit.aet.hephaestus.config;

import java.time.Clock;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakConfig {

    private static final Logger log = LoggerFactory.getLogger(KeycloakConfig.class);

    private final KeycloakProperties keycloakProperties;

    public KeycloakConfig(KeycloakProperties keycloakProperties) {
        this.keycloakProperties = keycloakProperties;
    }

    /**
     * Builds the Keycloak admin client. Tolerates unset config (worker / webhook profiles boot
     * without Keycloak); when unconfigured, returns a dynamic proxy whose first invocation
     * throws — accidental server-role traffic against Keycloak surfaces loudly.
     */
    @Bean
    public Keycloak keycloakClient() {
        if (!keycloakProperties.isConfigured()) {
            log.info("Keycloak is not configured (url/realm/clientId unset); using throwing proxy.");
            return (Keycloak) java.lang.reflect.Proxy.newProxyInstance(
                Keycloak.class.getClassLoader(),
                new Class<?>[] { Keycloak.class },
                (proxy, method, args) -> {
                    // close() is benign — it's called during shutdown DisposableBeanAdapter.
                    if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
                        return null;
                    }
                    throw new IllegalStateException(
                        "Keycloak admin client not configured (hephaestus.keycloak.{url,realm,clientId}); " +
                            "this role does not own user identity. Called: " + method.getName()
                    );
                }
            );
        }
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
