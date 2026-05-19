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
 * Pins the SlackAppConfig factory: with the token set it constructs an {@link App}, with the
 * token unset both gated beans drop out together (proves the two gates haven't been re-tangled).
 */
class OptionalIntegrationsContextTest extends BaseUnitTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(
            org.springframework.boot.autoconfigure.AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class)
        )
        .withUserConfiguration(GatedConfig.class);

    @Test
    void gatedBeansAreAbsentWhenPropertiesUnset() {
        runner.run(context -> assertThat(context).doesNotHaveBean(App.class).doesNotHaveBean(PosthogClient.class));
    }

    @Test
    void slackAppBeanIsRegisteredWhenHephaestusSlackTokenSet() {
        runner
            .withPropertyValues("hephaestus.slack.token=xoxb-test", "hephaestus.slack.signing-secret=signing-secret")
            .run(context -> assertThat(context).hasSingleBean(App.class));
    }

    /** Pins {@code @ConditionalOnExpression("!isBlank()")} on {@link SlackAppConfig}: a token
     *  defaulted to the empty string (e.g. {@code SLACK_BOT_TOKEN} unset, the yml default fires)
     *  must NOT register the bean — plain {@code @ConditionalOnProperty(name="token")} would. */
    @Test
    void slackAppBeanIsAbsentWhenHephaestusSlackTokenIsEmptyString() {
        runner
            .withPropertyValues("hephaestus.slack.token=", "hephaestus.slack.signing-secret=")
            .run(context -> assertThat(context).doesNotHaveBean(App.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ SlackProperties.class, PosthogProperties.class })
    @Import({ SlackAppConfig.class, PosthogClient.class })
    static class GatedConfig {}
}
