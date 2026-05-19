package de.tum.in.www1.hephaestus.architecture.fixtures;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Deliberate violation of {@code TaskSchedulerInjectionArchitectureTest}. Referenced via
 * {@code .class} from the test's negative-fixture path so the rule has a documented failure case.
 */
@SuppressWarnings("unused")
public final class ViolatingTaskSchedulerInjection {

    private final ThreadPoolTaskScheduler scheduler;

    public ViolatingTaskSchedulerInjection(ThreadPoolTaskScheduler scheduler) {
        this.scheduler = scheduler;
    }
}
