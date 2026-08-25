package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.TestAsyncConfiguration;
import de.tum.cit.aet.hephaestus.testconfig.TestSecurityConfig;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.context.annotation.Import;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Guards the application-controlled portion of Spring's startup path against slow bean initialization. */
@SpringBootTest(useMainMethod = UseMainMethod.ALWAYS)
@ActiveProfiles("test")
@Import({ TestSecurityConfig.class, TestAsyncConfiguration.class })
@Tag("architecture")
class StartupBudgetIntegrationTest {

    private static final String BEAN_INSTANTIATE = "spring.beans.instantiate";

    // Database connection and schema export time are external to application bean initialization.
    private static final String JPA_WARM_UP_BEAN = "&entityManagerFactory";

    private static final Duration PER_BEAN_CEILING = Duration.ofSeconds(6);

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        var postgres = PostgreSQLTestContainer.getInstance();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ApplicationStartup applicationStartup;

    @Test
    void shouldKeepBeanInstantiationWithinBudgetWhenApplicationStarts() {
        var events = ((BufferingApplicationStartup) applicationStartup).getBufferedTimeline().getEvents();

        var beans = events
            .stream()
            .filter(e -> BEAN_INSTANTIATE.equals(e.getStartupStep().getName()))
            .filter(e -> e.getEndTime() != null)
            .toList();

        assertThat(beans.stream().map(StartupBudgetIntegrationTest::beanNameOf))
            .as(
                "%s is exempt below, so it has to be a bean that is really built — a renamed one would widen the exemption to nothing in silence",
                JPA_WARM_UP_BEAN
            )
            .contains(JPA_WARM_UP_BEAN);

        var slowest = beans
            .stream()
            .filter(e -> !JPA_WARM_UP_BEAN.equals(beanNameOf(e)))
            .max((a, b) -> a.getDuration().compareTo(b.getDuration()))
            .orElseThrow(() -> new AssertionError("no " + BEAN_INSTANTIATE + " events captured"));

        assertThat(slowest.getDuration())
            .as(
                "slowest bean instantiation %s (%s) exceeded the %s budget — that bean is doing egregious " +
                    "synchronous work on the startup path; check its constructor/@PostConstruct.",
                slowest.getDuration(),
                beanNameOf(slowest),
                PER_BEAN_CEILING
            )
            .isLessThan(PER_BEAN_CEILING);
    }

    private static String beanNameOf(StartupTimeline.TimelineEvent event) {
        for (StartupStep.Tag tag : event.getStartupStep().getTags()) {
            if ("beanName".equals(tag.getKey())) {
                return tag.getValue();
            }
        }
        return "bean name not tagged";
    }
}
