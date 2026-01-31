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
 * Unit tests for CORS configuration properties validation.
 *
 * @see CorsProperties
 */
@Tag("unit")
@DisplayName("CorsProperties Configuration Binding")
class CorsPropertiesTest {

    @EnableConfigurationProperties(CorsProperties.class)
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
        @DisplayName("should bind single origin correctly")
        void singleOrigin_boundCorrectly() {
            contextRunner()
                .withPropertyValues("hephaestus.cors.allowed-origins[0]=https://example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CorsProperties props = context.getBean(CorsProperties.class);

                    assertThat(props.allowedOrigins()).containsExactly("https://example.com");
                });
        }

        @Test
        @DisplayName("should bind multiple origins correctly")
        void multipleOrigins_boundCorrectly() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.cors.allowed-origins[0]=https://app.example.com",
                    "hephaestus.cors.allowed-origins[1]=https://admin.example.com",
                    "hephaestus.cors.allowed-origins[2]=http://localhost:4200"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CorsProperties props = context.getBean(CorsProperties.class);

                    assertThat(props.allowedOrigins()).containsExactly(
                        "https://app.example.com",
                        "https://admin.example.com",
                        "http://localhost:4200"
                    );
                });
        }

        @Test
        @DisplayName("should handle environment variable placeholders in origins")
        void environmentVariablePlaceholder_resolvedCorrectly() {
            contextRunner()
                .withSystemProperties("TEST_ORIGIN=https://resolved.example.com")
                .withPropertyValues("hephaestus.cors.allowed-origins[0]=${TEST_ORIGIN}")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CorsProperties props = context.getBean(CorsProperties.class);

                    assertThat(props.allowedOrigins()).containsExactly("https://resolved.example.com");
                });
        }
    }

    @Nested
    @DisplayName("Validation Failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail when allowed-origins is empty")
        void emptyOrigins_validationFails() {
            contextRunner().run(context -> assertThat(context).hasFailed());
        }

        @Test
        @DisplayName("should fail when allowed-origins list is explicitly empty")
        void explicitlyEmptyList_validationFails() {
            contextRunner()
                .withPropertyValues("hephaestus.cors.allowed-origins=")
                .run(context -> assertThat(context).hasFailed());
        }
    }
}
