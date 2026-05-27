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
 * Unit tests for Slack configuration properties.
 *
 * @see SlackProperties
 */
@Tag("unit")
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
        void missingToken_notConfigured() {
            contextRunner()
                .withPropertyValues("hephaestus.slack.signing-secret=signing123")
                .run(context -> {
                    SlackProperties props = context.getBean(SlackProperties.class);
                    assertThat(props.isConfigured()).isFalse();
                });
        }

        @Test
        void missingSigningSecret_notConfigured() {
            contextRunner()
                .withPropertyValues("hephaestus.slack.token=xoxb-test-token")
                .run(context -> {
                    SlackProperties props = context.getBean(SlackProperties.class);
                    assertThat(props.isConfigured()).isFalse();
                });
        }

        @Test
        void blankToken_notConfigured() {
            contextRunner()
                .withPropertyValues("hephaestus.slack.token=   ", "hephaestus.slack.signing-secret=signing123")
                .run(context -> {
                    SlackProperties props = context.getBean(SlackProperties.class);
                    assertThat(props.isConfigured()).isFalse();
                });
        }

        @Test
        void emptyConfig_allowed() {
            contextRunner().run(context -> {
                assertThat(context).hasNotFailed();
                SlackProperties props = context.getBean(SlackProperties.class);
                assertThat(props.isConfigured()).isFalse();
            });
        }
    }
}
