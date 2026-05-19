package de.tum.in.www1.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.slack.api.bolt.App;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClient;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Locks in the {@code @ConditionalOnProperty} contract for the Slack and PostHog beans: when their
 * properties are absent (the test profile's default), neither bean is in the application context.
 */
@DisplayName("Optional integration beans are absent when their properties are unset")
class BootstrapApplicationContextIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void slackAppBeanIsAbsent() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> context.getBean(App.class));
    }

    @Test
    void posthogClientBeanIsAbsent() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
            .isThrownBy(() -> context.getBean(PosthogClient.class));
    }

    @Test
    void slackMessageServiceIsAlsoAbsentBecauseNotificationsDisabled() {
        // Sanity: the SlackMessageService is gated by hephaestus.leaderboard.notification.enabled.
        // Confirm we don't accidentally pick it up via some default property.
        String[] beanNames = context.getBeanNamesForType(
            de.tum.in.www1.hephaestus.leaderboard.SlackMessageService.class
        );
        assertThat(beanNames).isEmpty();
    }
}
