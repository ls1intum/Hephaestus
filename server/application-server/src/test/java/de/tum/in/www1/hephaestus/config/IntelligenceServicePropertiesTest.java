package de.tum.in.www1.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Unit tests for intelligence service configuration properties validation.
 *
 * @see IntelligenceServiceProperties
 */
@Tag("unit")
@DisplayName("IntelligenceServiceProperties Configuration Binding")
class IntelligenceServicePropertiesTest {

    @EnableConfigurationProperties(IntelligenceServiceProperties.class)
    static class TestConfiguration {}

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner().withUserConfiguration(
            TestConfiguration.class,
            ValidationAutoConfiguration.class
        );
    }

    @Nested
    @DisplayName("Valid Configuration")
    class ValidConfiguration {

        @Test
        @DisplayName("should bind URL correctly")
        void validUrl_boundCorrectly() {
            contextRunner()
                .withPropertyValues("hephaestus.intelligence-service.url=http://localhost:8081")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    IntelligenceServiceProperties props = context.getBean(IntelligenceServiceProperties.class);

                    assertThat(props.url()).isEqualTo("http://localhost:8081");
                });
        }

        @Test
        @DisplayName("should bind HTTPS URL correctly")
        void httpsUrl_boundCorrectly() {
            contextRunner()
                .withPropertyValues("hephaestus.intelligence-service.url=https://intelligence.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    IntelligenceServiceProperties props = context.getBean(IntelligenceServiceProperties.class);

                    assertThat(props.url()).isEqualTo("https://intelligence.example.com");
                });
        }

        @Test
        @DisplayName("should handle URL with port correctly")
        void urlWithPort_boundCorrectly() {
            contextRunner()
                .withPropertyValues("hephaestus.intelligence-service.url=http://intelligence-service:3000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    IntelligenceServiceProperties props = context.getBean(IntelligenceServiceProperties.class);

                    assertThat(props.url()).isEqualTo("http://intelligence-service:3000");
                });
        }

        @Test
        @DisplayName("should handle environment variable placeholder correctly")
        void environmentVariablePlaceholder_resolvedCorrectly() {
            contextRunner()
                .withSystemProperties("INTELLIGENCE_SERVICE_URL=http://resolved.example.com:8081")
                .withPropertyValues("hephaestus.intelligence-service.url=${INTELLIGENCE_SERVICE_URL}")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    IntelligenceServiceProperties props = context.getBean(IntelligenceServiceProperties.class);

                    assertThat(props.url()).isEqualTo("http://resolved.example.com:8081");
                });
        }
    }

    @Nested
    @DisplayName("Validation Failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail when URL is missing")
        void missingUrl_validationFails() {
            contextRunner().run(context -> assertThat(context).hasFailed());
        }

        @Test
        @DisplayName("should fail when URL is blank")
        void blankUrl_validationFails() {
            contextRunner()
                .withPropertyValues("hephaestus.intelligence-service.url=")
                .run(context -> assertThat(context).hasFailed());
        }

        @Test
        @DisplayName("should fail when URL is whitespace only")
        void whitespaceUrl_validationFails() {
            contextRunner()
                .withPropertyValues("hephaestus.intelligence-service.url=   ")
                .run(context -> assertThat(context).hasFailed());
        }

        @Test
        @DisplayName("should fail when URL is not a valid URL format")
        void invalidUrlFormat_validationFails() {
            contextRunner()
                .withPropertyValues("hephaestus.intelligence-service.url=not-a-valid-url")
                .run(context -> assertThat(context).hasFailed());
        }
    }
}
