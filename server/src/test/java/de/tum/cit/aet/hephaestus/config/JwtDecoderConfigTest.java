package de.tum.cit.aet.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Tag("unit")
class JwtDecoderConfigTest {

    @EnableConfigurationProperties(KeycloakProperties.class)
    static class TestConfiguration {}

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class, JwtDecoderConfig.class)
            .withPropertyValues(
                "spring.profiles.active=prod",
                "hephaestus.keycloak.url=https://auth.example.com/keycloak",
                "hephaestus.keycloak.realm=hephaestus",
                "hephaestus.keycloak.client-id=hephaestus-confidential",
                "hephaestus.keycloak.internal-url=http://keycloak:8080",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.com/keycloak/realms/hephaestus"
            );
    }

    @Test
    void createsDecoderWhenJwkSetUriConfigured() {
        contextRunner()
            .withPropertyValues(
                "hephaestus.keycloak.jwk-set-uri=http://keycloak:8080/realms/hephaestus/protocol/openid-connect/certs"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(JwtDecoder.class);
                assertThat(context.getBean(JwtDecoder.class)).isInstanceOf(NimbusJwtDecoder.class);
            });
    }

    @Test
    void doesNotCreateDecoderWhenJwkSetUriBlank() {
        contextRunner()
            .withPropertyValues("hephaestus.keycloak.jwk-set-uri=")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(JwtDecoder.class);
            });
    }
}
