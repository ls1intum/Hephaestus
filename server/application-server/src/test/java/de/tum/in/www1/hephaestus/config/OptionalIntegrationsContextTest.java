package de.tum.in.www1.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.slack.api.bolt.App;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClient;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogProperties;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Locks the {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty}
 * contract on the Slack and PostHog beans without spinning a full Spring Boot context: when their
 * gating properties are unset, neither bean is registered.
 */
class OptionalIntegrationsContextTest extends BaseUnitTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(
            org.springframework.boot.autoconfigure.AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class)
        )
        .withUserConfiguration(GatedConfig.class);

    @Test
    void slackAppBeanIsAbsentWhenHephaestusSlackTokenUnset() {
        runner.run(context -> assertThat(context).doesNotHaveBean(App.class));
    }

    @Test
    void posthogClientBeanIsAbsentWhenHephaestusPosthogEnabledUnset() {
        runner.run(context -> assertThat(context).doesNotHaveBean(PosthogClient.class));
    }

    @Test
    void slackAppBeanIsRegisteredWhenHephaestusSlackTokenSet() {
        runner
            .withPropertyValues("hephaestus.slack.token=xoxb-test", "hephaestus.slack.signing-secret=signing-secret")
            .run(context -> assertThat(context).hasSingleBean(App.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ SlackProperties.class, PosthogProperties.class })
    @Import({ SlackAppConfig.class, PosthogClient.class })
    static class GatedConfig {}
}
