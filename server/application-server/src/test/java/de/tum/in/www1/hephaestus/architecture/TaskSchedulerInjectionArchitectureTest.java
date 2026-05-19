package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import de.tum.in.www1.hephaestus.architecture.fixtures.ViolatingTaskSchedulerInjection;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Bans injection of the concrete {@link ThreadPoolTaskScheduler} in production code.
 * {@code spring.threads.virtual.enabled=true} swaps the auto-configured task scheduler bean to
 * {@code SimpleAsyncTaskScheduler}; concrete-type injection breaks bean wiring on that path.
 * Use the {@link org.springframework.scheduling.TaskScheduler} interface instead.
 */
class TaskSchedulerInjectionArchitectureTest extends HephaestusArchitectureTest {

    @Test
    void productionCodeMustNotDependOnConcreteThreadPoolTaskScheduler() {
        rule().check(classes);
    }

    @Test
    void negativeFixtureTriggersTheRule() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ViolatingTaskSchedulerInjection.class);
        assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> rule().check(fixture));
    }

    private static ArchRule rule() {
        return noClasses()
            .that()
            .resideInAPackage(BASE_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(ThreadPoolTaskScheduler.class)
            .because(
                "spring.threads.virtual.enabled=true swaps the auto-configured TaskScheduler to " +
                    "SimpleAsyncTaskScheduler; concrete-type injection breaks bean wiring"
            );
    }
}
