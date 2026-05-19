package de.tum.in.www1.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.slack.api.bolt.App;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClient;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class BootstrapApplicationContextIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void slackAppBeanIsAbsentWhenHephaestusSlackTokenUnset() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> context.getBean(App.class));
    }

    @Test
    void posthogClientBeanIsAbsentWhenHephaestusPosthogEnabledUnset() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
            .isThrownBy(() -> context.getBean(PosthogClient.class));
    }
}
