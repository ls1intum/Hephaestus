package de.tum.cit.aet.hephaestus.agent.sandbox.kubernetes;

import de.tum.cit.aet.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.cit.aet.hephaestus.agent.sandbox.SandboxRuntime;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWorkerRole;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * The worker's start on Kubernetes and the maintenance of the sandbox Pods, as {@code SandboxMaintenanceConfiguration}
 * is for Docker. The isolation probe runs first, once the gateway listens; a probe that fails aborts the start.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!specs & !cds-training")
@ConditionalOnWorkerRole
@ConditionalOnProperty(name = SandboxRuntime.PROPERTY, havingValue = "kubernetes")
public class KubernetesSandboxMaintenanceConfiguration {

    @Bean
    ScheduledTaskRegistrar kubernetesSandboxMaintenanceTasks(
            KubernetesSandboxReconciler reconciler,
            SandboxProperties sandboxProperties,
            @Value("${hephaestus.sandbox.reconciliation-initial-delay-seconds:10}") long initialDelaySeconds) {
        var tasks = new ScheduledTaskRegistrar();
        tasks.addFixedDelayTask(new FixedDelayTask(
                reconciler::sweep,
                Duration.ofSeconds(sandboxProperties.reconciliationIntervalSeconds()),
                Duration.ofSeconds(initialDelaySeconds)));
        return tasks;
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> kubernetesSandboxStartupMaintenance(
            KubernetesIsolationProbe probe, KubernetesSandboxReconciler reconciler) {
        return event -> {
            probe.prove();
            reconciler.sweep();
        };
    }
}
