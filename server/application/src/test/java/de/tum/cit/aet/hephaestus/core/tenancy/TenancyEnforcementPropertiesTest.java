package de.tum.cit.aet.hephaestus.core.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TenancyEnforcementPropertiesTest extends BaseUnitTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

    @Test
    void defaultsToThrow() {
        contextRunner.run(context -> assertThat(
                        context.getBean(TenancyEnforcementProperties.class).enforcement())
                .isEqualTo(TenancyEnforcement.THROW));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TenancyEnforcementProperties.class)
    static class TestConfiguration {}
}
