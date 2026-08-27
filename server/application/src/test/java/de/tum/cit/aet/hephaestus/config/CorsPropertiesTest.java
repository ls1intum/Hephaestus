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
 * Unit tests for CORS configuration properties validation.
 *
 * @see CorsProperties
 */
@Tag("unit")
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
    class ValidConfiguration {

        @Test
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
    class ValidationFailures {

        @Test
        void emptyOrigins_validationFails() {
            contextRunner().run(context -> assertThat(context).hasFailed());
        }

        @Test
        void explicitlyEmptyList_validationFails() {
            contextRunner()
                .withPropertyValues("hephaestus.cors.allowed-origins=")
                .run(context -> assertThat(context).hasFailed());
        }
    }
}
