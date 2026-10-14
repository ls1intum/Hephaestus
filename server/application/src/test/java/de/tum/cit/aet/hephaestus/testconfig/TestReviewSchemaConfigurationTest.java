package de.tum.cit.aet.hephaestus.testconfig;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TestReviewSchemaConfigurationTest extends BaseUnitTest {
    @Test
    void shouldLeaveMigratedFunctionsToTheProductionChangelog() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestReviewSchemaConfiguration.class)
                .withPropertyValues("spring.profiles.active=test", "spring.liquibase.enabled=true")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("feedbackDigestSchema"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "specs", "cds-training"})
    void shouldNeverInitializeTheHibernateFixtureOutsideTheTestProfile(String profile) {
        new ApplicationContextRunner()
                .withUserConfiguration(TestReviewSchemaConfiguration.class)
                .withPropertyValues("spring.profiles.active=" + profile, "spring.liquibase.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("feedbackDigestSchema"));
    }
}
