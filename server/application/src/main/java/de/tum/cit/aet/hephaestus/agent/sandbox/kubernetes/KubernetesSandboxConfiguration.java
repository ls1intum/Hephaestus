package de.tum.cit.aet.hephaestus.agent.sandbox.kubernetes;

import de.tum.cit.aet.hephaestus.agent.gateway.SandboxGatewayProperties;
import de.tum.cit.aet.hephaestus.agent.gateway.SandboxGatewaySessions;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.cit.aet.hephaestus.agent.sandbox.SandboxRuntime;
import de.tum.cit.aet.hephaestus.agent.sandbox.docker.SandboxWorkspaceManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.HostSandbox;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWorkerRole;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Kubernetes sandbox runtime: practice reviews as Jobs, the Heph host and Heph's turn sandboxes as Pods, in the
 * sandbox namespace the chart owns. It loads on the worker role only, and only when {@link SandboxRuntime#PROPERTY} selects
 * it; it then replaces {@code DockerSandboxConfiguration} entirely and never falls back to it. The client uses the
 * worker Pod's service account, as the client's own configuration finds it.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWorkerRole
@ConditionalOnProperty(name = SandboxRuntime.PROPERTY, havingValue = "kubernetes")
@EnableConfigurationProperties({SandboxProperties.class, KubernetesSandboxProperties.class})
public class KubernetesSandboxConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KubernetesSandboxConfiguration.class);

    /** Bounds every request but a watch or an exec, which bound themselves. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(5);

    @Bean(destroyMethod = "close")
    public KubernetesClient sandboxKubernetesClient() {
        Config config = new ConfigBuilder(Config.autoConfigure(null))
                .withRequestTimeout((int) REQUEST_TIMEOUT.toMillis())
                .withConnectionTimeout((int) CONNECTION_TIMEOUT.toMillis())
                .build();
        log.info("Kubernetes sandbox client configured: requestTimeout={}", REQUEST_TIMEOUT);
        return new KubernetesClientBuilder().withConfig(config).build();
    }

    @Bean
    public KubernetesWorker kubernetesWorker(
            KubernetesSandboxProperties properties, SandboxGatewayProperties gatewayProperties) {
        return new KubernetesWorker(properties, gatewayProperties.port());
    }

    @Bean
    public KubernetesIsolationProbe kubernetesIsolationProbe(
            KubernetesClient sandboxKubernetesClient,
            KubernetesPods pods,
            KubernetesWorker worker,
            AgentImageProperties agentImageProperties,
            @Value("${server.port:8080}") int serverPort) {
        return new KubernetesIsolationProbe(
                sandboxKubernetesClient, pods, worker, agentImageProperties.reference(), serverPort);
    }

    @Bean
    public KubernetesPods kubernetesPods(
            KubernetesClient sandboxKubernetesClient,
            KubernetesWorker worker,
            SandboxProperties properties,
            AgentImageProperties agentImageProperties) {
        return new KubernetesPods(
                sandboxKubernetesClient,
                worker,
                properties.containerStopTimeoutSeconds(),
                agentImageProperties.pullPolicy());
    }

    @Bean
    public KubernetesReviewJobs kubernetesReviewJobs(
            KubernetesClient sandboxKubernetesClient, KubernetesWorker worker) {
        return new KubernetesReviewJobs(sandboxKubernetesClient, worker);
    }

    @Bean
    public SandboxWorkspaceManager sandboxWorkspaceManager() {
        return new SandboxWorkspaceManager();
    }

    @Bean
    public SandboxManager kubernetesSandboxAdapter(
            KubernetesPods pods,
            KubernetesReviewJobs reviewJobs,
            KubernetesWorker worker,
            SandboxWorkspaceManager workspaceManager,
            SandboxGatewaySessions gatewaySessions,
            MeterRegistry meterRegistry) {
        return new KubernetesSandboxAdapter(pods, reviewJobs, worker, workspaceManager, gatewaySessions, meterRegistry);
    }

    @Bean
    public HostSandbox kubernetesHostSandbox(
            KubernetesPods pods,
            KubernetesWorker worker,
            SandboxWorkspaceManager workspaceManager,
            SandboxGatewaySessions gatewaySessions,
            AgentImageProperties agentImageProperties) {
        return new KubernetesHostSandbox(
                pods, worker, workspaceManager, gatewaySessions, agentImageProperties.reference());
    }

    @Bean(destroyMethod = "close")
    public KubernetesExecSandboxes kubernetesExecSandboxes(
            KubernetesClient sandboxKubernetesClient,
            KubernetesPods pods,
            KubernetesWorker worker,
            AgentImageProperties agentImageProperties) {
        return new KubernetesExecSandboxes(sandboxKubernetesClient, pods, worker, agentImageProperties.reference());
    }

    @Bean
    public KubernetesSandboxReconciler kubernetesSandboxReconciler(
            ReviewJobRepository jobRepository,
            KubernetesPods pods,
            KubernetesReviewJobs reviewJobs,
            KubernetesWorker worker,
            MeterRegistry meterRegistry,
            Clock clock) {
        return new KubernetesSandboxReconciler(jobRepository, pods, reviewJobs, worker, meterRegistry, clock);
    }

    /** Readiness, as {@code DockerHealthIndicator}: an unreachable API stops new work, not the worker. */
    @Bean
    public HealthIndicator kubernetesSandboxHealthIndicator(
            KubernetesClient sandboxKubernetesClient, KubernetesWorker worker) {
        return () -> {
            try {
                sandboxKubernetesClient
                        .pods()
                        .inNamespace(worker.namespace())
                        .withLabels(worker.selector(KubernetesWorker.KIND_HOST))
                        .list(new ListOptionsBuilder().withLimit(1L).build());
                return Health.up().build();
            } catch (KubernetesClientException e) {
                return Health.down()
                        .withDetail("error", "Kubernetes API not reachable: HTTP " + e.getCode())
                        .build();
            }
        };
    }
}
