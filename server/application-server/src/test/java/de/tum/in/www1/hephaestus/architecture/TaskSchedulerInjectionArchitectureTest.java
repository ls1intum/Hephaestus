package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Bans injection of the concrete {@link ThreadPoolTaskScheduler} in production code.
 *
 * <p>{@code spring.threads.virtual.enabled=true} swaps the auto-configured task scheduler bean
 * from {@code ThreadPoolTaskScheduler} to {@code SimpleAsyncTaskScheduler}. Anything that depends
 * on the concrete type — fields, constructor params, return types — breaks bean wiring on the
 * virtual-thread path. Inject the {@link org.springframework.scheduling.TaskScheduler} interface.
 */
@DisplayName("Task scheduler injection hygiene")
class TaskSchedulerInjectionArchitectureTest extends HephaestusArchitectureTest {

    @Test
    void productionCodeMustNotDependOnConcreteThreadPoolTaskScheduler() {
        rule().check(classes);
    }

    @Test
    void negativeFixtureTriggersTheRule() {
        // Imports a deliberate violation under this test package so the rule has a documented
        // failure path. If a future refactor breaks the rule's matcher (e.g., upstream package
        // rename, accidental @Disabled), this assertion goes red instead of the production rule
        // passing vacuously.
        JavaClasses fixture = new ClassFileImporter().importClasses(ViolatingFixture.class);
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

    /** Negative fixture — exists only so {@link #negativeFixtureTriggersTheRule} can prove the rule fires. */
    @SuppressWarnings("unused")
    private static final class ViolatingFixture {

        private final ThreadPoolTaskScheduler scheduler;

        ViolatingFixture(ThreadPoolTaskScheduler scheduler) {
            this.scheduler = scheduler;
        }
    }
}
