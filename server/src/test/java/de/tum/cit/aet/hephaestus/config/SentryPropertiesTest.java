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
 * Unit tests for Sentry configuration properties.
 *
 * @see SentryProperties
 */
@Tag("unit")
class SentryPropertiesTest {

    @EnableConfigurationProperties(SentryProperties.class)
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
        @DisplayName("should bind DSN when configured")
        void dsnConfig_boundCorrectly() {
            contextRunner()
                .withPropertyValues("hephaestus.sentry.dsn=https://key@sentry.io/123456")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SentryProperties props = context.getBean(SentryProperties.class);

                    assertThat(props.dsn()).isEqualTo("https://key@sentry.io/123456");
                    assertThat(props.isConfigured()).isTrue();
                });
        }

        @Test
        void missingDsn_notConfigured() {
            contextRunner().run(context -> {
                SentryProperties props = context.getBean(SentryProperties.class);
                assertThat(props.isConfigured()).isFalse();
            });
        }

        @Test
        void blankDsn_notConfigured() {
            contextRunner()
                .withPropertyValues("hephaestus.sentry.dsn=   ")
                .run(context -> {
                    SentryProperties props = context.getBean(SentryProperties.class);
                    assertThat(props.isConfigured()).isFalse();
                });
        }

        @Test
        void emptyDsn_allowed() {
            contextRunner()
                .withPropertyValues("hephaestus.sentry.dsn=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SentryProperties props = context.getBean(SentryProperties.class);
                    assertThat(props.isConfigured()).isFalse();
                });
        }
    }
}
