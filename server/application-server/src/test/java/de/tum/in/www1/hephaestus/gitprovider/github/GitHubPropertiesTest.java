package de.tum.in.www1.hephaestus.gitprovider.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Unit tests for GitHub configuration properties validation.
 *
 * @see GitHubProperties
 */
@Tag("unit")
@DisplayName("GitHubProperties Configuration Binding")
class GitHubPropertiesTest {

    @EnableConfigurationProperties(GitHubProperties.class)
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
        @DisplayName("should bind default properties when no configuration provided")
        void defaultConfig_contextLoads() {
            contextRunner().run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(GitHubProperties.class);

                GitHubProperties props = context.getBean(GitHubProperties.class);
                // Verify defaults
                assertThat(props.app()).isNotNull();
                assertThat(props.app().id()).isEqualTo(0L); // disabled by default
                assertThat(props.meta()).isNotNull();
            });
        }

        @Test
        @DisplayName("should bind GitHub App configuration when provided")
        void appConfig_boundCorrectly() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.github.app.id=123456",
                    "hephaestus.github.app.private-key=-----BEGIN RSA PRIVATE KEY-----"
                )
                .run(context -> {
                    GitHubProperties props = context.getBean(GitHubProperties.class);

                    assertThat(props.app().id()).isEqualTo(123456L);
                    assertThat(props.app().privateKey()).isEqualTo("-----BEGIN RSA PRIVATE KEY-----");
                });
        }

        @Test
        @DisplayName("should bind meta auth token when provided")
        void metaConfig_boundCorrectly() {
            contextRunner()
                .withPropertyValues("hephaestus.github.meta.auth-token=ghp_test_token_12345")
                .run(context -> {
                    GitHubProperties props = context.getBean(GitHubProperties.class);

                    assertThat(props.meta().authToken()).isEqualTo("ghp_test_token_12345");
                });
        }

        @Test
        @DisplayName("should allow null private key for PAT mode")
        void nullPrivateKey_allowed() {
            contextRunner()
                .withPropertyValues("hephaestus.github.app.id=0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    GitHubProperties props = context.getBean(GitHubProperties.class);
                    assertThat(props.app().privateKey()).isNull();
                    assertThat(props.app().privateKeyLocation()).isNull();
                });
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest
        @ValueSource(longs = { 0, 1, 12345, 999999999 })
        @DisplayName("should accept non-negative app ID values")
        void validAppId_passes(long appId) {
            contextRunner()
                .withPropertyValues("hephaestus.github.app.id=" + appId)
                .run(context -> assertThat(context).hasNotFailed());
        }

        @ParameterizedTest
        @ValueSource(longs = { -1, -100, -999999 })
        @DisplayName("should fail when app ID is negative")
        void negativeAppId_validationFails(long appId) {
            contextRunner()
                .withPropertyValues("hephaestus.github.app.id=" + appId)
                .run(context -> assertThat(context).hasFailed());
        }
    }

    @Nested
    @DisplayName("Nested Record Defaults")
    class NestedRecordDefaults {

        @Test
        @DisplayName("should provide non-null nested app record when not configured")
        void nestedApp_neverNull() {
            contextRunner().run(context -> {
                GitHubProperties props = context.getBean(GitHubProperties.class);
                assertThat(props.app()).isNotNull();
            });
        }

        @Test
        @DisplayName("should provide non-null nested meta record when not configured")
        void nestedMeta_neverNull() {
            contextRunner().run(context -> {
                GitHubProperties props = context.getBean(GitHubProperties.class);
                assertThat(props.meta()).isNotNull();
            });
        }
    }
}
