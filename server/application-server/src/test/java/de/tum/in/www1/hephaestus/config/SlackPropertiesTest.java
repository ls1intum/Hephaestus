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
 * Unit tests for Slack configuration properties.
 *
 * @see SlackProperties
 */
@Tag("unit")
@DisplayName("SlackProperties Configuration Binding")
class SlackPropertiesTest {

    @EnableConfigurationProperties(SlackProperties.class)
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
        @DisplayName("should bind when fully configured")
        void fullConfig_boundCorrectly() {
            contextRunner()
                .withPropertyValues(
                    "hephaestus.slack.token=xoxb-test-token",
                    "hephaestus.slack.signing-secret=signing123"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SlackProperties props = context.getBean(SlackProperties.class);

                    assertThat(props.token()).isEqualTo("xoxb-test-token");
                    assertThat(props.signingSecret()).isEqualTo("signing123");
                    assertThat(props.isConfigured()).isTrue();
                });
        }

        @Test
        @DisplayName("should report not configured when token is missing")
        void missingToken_notConfigured() {
            contextRunner()
                .withPropertyValues("hephaestus.slack.signing-secret=signing123")
                .run(context -> {
                    SlackProperties props = context.getBean(SlackProperties.class);
                    assertThat(props.isConfigured()).isFalse();
                });
        }

        @Test
        @DisplayName("should report not configured when signing secret is missing")
        void missingSigningSecret_notConfigured() {
            contextRunner()
                .withPropertyValues("hephaestus.slack.token=xoxb-test-token")
                .run(context -> {
                    SlackProperties props = context.getBean(SlackProperties.class);
                    assertThat(props.isConfigured()).isFalse();
                });
        }

        @Test
        @DisplayName("should report not configured when token is blank")
        void blankToken_notConfigured() {
            contextRunner()
                .withPropertyValues("hephaestus.slack.token=   ", "hephaestus.slack.signing-secret=signing123")
                .run(context -> {
                    SlackProperties props = context.getBean(SlackProperties.class);
                    assertThat(props.isConfigured()).isFalse();
                });
        }

        @Test
        @DisplayName("should allow empty config for disabled Slack integration")
        void emptyConfig_allowed() {
            contextRunner().run(context -> {
                assertThat(context).hasNotFailed();
                SlackProperties props = context.getBean(SlackProperties.class);
                assertThat(props.isConfigured()).isFalse();
            });
        }
    }
}
