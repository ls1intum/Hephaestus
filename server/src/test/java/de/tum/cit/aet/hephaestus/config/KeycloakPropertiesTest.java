package de.tum.cit.aet.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/**
 * Unit tests for Keycloak configuration properties validation.
 *
 * @see KeycloakProperties
 */
@Tag("unit")
class KeycloakPropertiesTest {

    @EnableConfigurationProperties(KeycloakProperties.class)
    static class TestConfiguration {}

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner().withUserConfiguration(
            TestConfiguration.class,
            ValidationAutoConfiguration.class
        );
    }

    @Nested
    class ValidConfiguration {

        @Test
        @DisplayName("should bind all properties when fully configured")
        void fullConfig_boundCorrectly() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.keycloak.url=https://auth.example.com",
                    "hephaestus.keycloak.realm=my-realm",
                    "hephaestus.keycloak.client-id=my-client",
                    "hephaestus.keycloak.client-secret=secret123",
                    "hephaestus.keycloak.internal-url=http://keycloak:8080",
                    "hephaestus.keycloak.jwk-set-uri=http://keycloak:8080/realms/my-realm/protocol/openid-connect/certs"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    KeycloakProperties props = context.getBean(KeycloakProperties.class);

                    assertThat(props.url()).isEqualTo("https://auth.example.com");
                    assertThat(props.realm()).isEqualTo("my-realm");
                    assertThat(props.clientId()).isEqualTo("my-client");
                    assertThat(props.clientSecret()).isEqualTo("secret123");
                    assertThat(props.internalUrl()).isEqualTo("http://keycloak:8080");
                });
        }

        @Test
        void effectiveInternalUrl_fallsBackToPublicUrl() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.keycloak.url=https://auth.example.com",
                    "hephaestus.keycloak.realm=test",
                    "hephaestus.keycloak.client-id=client"
                )
                .run(context -> {
                    KeycloakProperties props = context.getBean(KeycloakProperties.class);
                    assertThat(props.effectiveInternalUrl()).isEqualTo("https://auth.example.com");
                });
        }

        @Test
        void effectiveJwkSetUri_computedFromInternalUrl() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.keycloak.url=https://auth.example.com",
                    "hephaestus.keycloak.realm=my-realm",
                    "hephaestus.keycloak.client-id=client",
                    "hephaestus.keycloak.internal-url=http://keycloak:8080"
                )
                .run(context -> {
                    KeycloakProperties props = context.getBean(KeycloakProperties.class);
                    assertThat(props.effectiveJwkSetUri()).isEqualTo(
                        "http://keycloak:8080/realms/my-realm/protocol/openid-connect/certs"
                    );
                });
        }

        @Test
        void validateOnStartup_defaultsToFalse() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.keycloak.url=https://auth.example.com",
                    "hephaestus.keycloak.realm=test",
                    "hephaestus.keycloak.client-id=client"
                )
                .run(context -> {
                    KeycloakProperties props = context.getBean(KeycloakProperties.class);
                    assertThat(props.validateOnStartup()).isNull();
                    assertThat(props.shouldValidateOnStartup()).isFalse();
                });
        }

        @Test
        void validateOnStartup_whenTrue_bindCorrectly() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.keycloak.url=https://auth.example.com",
                    "hephaestus.keycloak.realm=test",
                    "hephaestus.keycloak.client-id=client",
                    "hephaestus.keycloak.validate-on-startup=true"
                )
                .run(context -> {
                    KeycloakProperties props = context.getBean(KeycloakProperties.class);
                    assertThat(props.validateOnStartup()).isTrue();
                    assertThat(props.shouldValidateOnStartup()).isTrue();
                });
        }

        @Test
        void validateOnStartup_whenFalse_bindCorrectly() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.keycloak.url=https://auth.example.com",
                    "hephaestus.keycloak.realm=test",
                    "hephaestus.keycloak.client-id=client",
                    "hephaestus.keycloak.validate-on-startup=false"
                )
                .run(context -> {
                    KeycloakProperties props = context.getBean(KeycloakProperties.class);
                    assertThat(props.validateOnStartup()).isFalse();
                    assertThat(props.shouldValidateOnStartup()).isFalse();
                });
        }
    }

    @Nested
    class IsConfiguredContract {

        @Test
        void blankUrl_isConfiguredFalse() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.keycloak.url=",
                    "hephaestus.keycloak.realm=test",
                    "hephaestus.keycloak.client-id=client"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KeycloakProperties.class).isConfigured()).isFalse();
                });
        }

        @Test
        void blankRealm_isConfiguredFalse() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.keycloak.url=https://auth.example.com",
                    "hephaestus.keycloak.realm=   ",
                    "hephaestus.keycloak.client-id=client"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KeycloakProperties.class).isConfigured()).isFalse();
                });
        }

        @Test
        void blankClientId_isConfiguredFalse() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.keycloak.url=https://auth.example.com",
                    "hephaestus.keycloak.realm=test",
                    "hephaestus.keycloak.client-id="
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KeycloakProperties.class).isConfigured()).isFalse();
                });
        }

        @Test
        void nullClientSecret_allowed() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.keycloak.url=https://auth.example.com",
                    "hephaestus.keycloak.realm=test",
                    "hephaestus.keycloak.client-id=public-client"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    KeycloakProperties props = context.getBean(KeycloakProperties.class);
                    assertThat(props.clientSecret()).isNull();
                });
        }
    }
}
