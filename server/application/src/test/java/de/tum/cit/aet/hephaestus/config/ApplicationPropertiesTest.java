package de.tum.cit.aet.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

@Tag("unit")
class ApplicationPropertiesTest {

    @EnableConfigurationProperties(ApplicationProperties.class)
    static class TestConfiguration {}

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class, ValidationAutoConfiguration.class);

    @Test
    void bindsValidWebappUrl() {
        contextRunner
                .withPropertyValues("hephaestus.webapp.url=https://hephaestus.example/app")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ApplicationProperties.class)
                                    .webapp()
                                    .url())
                            .isEqualTo("https://hephaestus.example/app");
                });
    }

    @Test
    void rejectsBlankWebappUrl() {
        contextRunner
                .withPropertyValues("hephaestus.webapp.url=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonHttpWebappUrl() {
        contextRunner
                .withPropertyValues("hephaestus.webapp.url=ftp://hephaestus.example")
                .run(context -> assertThat(context).hasFailed());
    }
}
