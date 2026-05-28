package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static de.tum.cit.aet.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@code spring.threads.virtual.enabled=true} swaps the auto-configured task scheduler bean to
 * {@code SimpleAsyncTaskScheduler}; injecting concrete {@link ThreadPoolTaskScheduler} breaks
 * wiring on that path. Use {@link org.springframework.scheduling.TaskScheduler} instead.
 */
class TaskSchedulerInjectionArchitectureTest extends HephaestusArchitectureTest {

    @Test
    void productionCodeMustNotDependOnConcreteThreadPoolTaskScheduler() {
        noClasses()
            .that()
            .resideInAPackage(BASE_PACKAGE + "..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(ThreadPoolTaskScheduler.class)
            .check(classes);
    }
}
