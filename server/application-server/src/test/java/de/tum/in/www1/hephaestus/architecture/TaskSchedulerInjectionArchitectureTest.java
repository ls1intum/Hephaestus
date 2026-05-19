package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;

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
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage(BASE_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(ThreadPoolTaskScheduler.class)
            .because(
                "spring.threads.virtual.enabled=true swaps the auto-configured TaskScheduler to " +
                    "SimpleAsyncTaskScheduler; concrete-type injection breaks bean wiring"
            );

        rule.check(classes);
    }
}
