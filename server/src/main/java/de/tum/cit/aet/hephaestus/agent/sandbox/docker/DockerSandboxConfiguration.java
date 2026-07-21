package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.proxy.MentorProxyCredentialRegistry;
import de.tum.cit.aet.hephaestus.agent.sandbox.InteractiveSandboxProperties;
import de.tum.cit.aet.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.cit.aet.hephaestus.agent.sandbox.docker.interactive.DockerInteractiveSandboxAdapter;
import de.tum.cit.aet.hephaestus.agent.sandbox.docker.interactive.InteractiveSandboxMetrics;
import de.tum.cit.aet.hephaestus.agent.sandbox.docker.interactive.InteractiveSandboxRegistry;
import de.tum.cit.aet.hephaestus.agent.sandbox.docker.interactive.StdinWriteWatchdog;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring configuration for the Docker sandbox subsystem.
 *
 * <p>The Docker sandbox <em>is</em> the worker capability (ADR 0005), so it is gated solely on the
 * worker role ({@link RuntimeRole#WORKER_PROPERTY}, {@code matchIfMissing=true} → present in the
 * single-JVM monolith). A non-worker pod (e.g. a webhook-only container) leaves the gate {@code
 * false} and these beans never wire. All sandbox beans are declared as {@code @Bean} methods so
 * they are only created when the role is active — no component-scanning surprises.
 */
@Configuration
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(DockerClient.class)
@EnableConfigurationProperties({ SandboxProperties.class, InteractiveSandboxProperties.class })
public class DockerSandboxConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxConfiguration.class);

    /** Connections per container: create/start, wait, logs/copy. */
    private static final int CONNECTIONS_PER_CONTAINER = 3;

    private static final Duration HTTP_CONNECTION_TIMEOUT = Duration.ofSeconds(5);

    /** docker wait/logs can block for the full container lifetime. */
    private static final Duration HTTP_RESPONSE_TIMEOUT = Duration.ofMinutes(30);

    @Bean(destroyMethod = "close")
    public DockerClient dockerClient(SandboxProperties properties) {
        var configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(properties.dockerHost())
            .withDockerTlsVerify(properties.tlsVerify());

        if (properties.certPath() != null) {
            configBuilder.withDockerCertPath(properties.certPath());
        }

        var config = configBuilder.build();

        var httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig())
            .maxConnections(properties.maxConcurrentContainers() * CONNECTIONS_PER_CONTAINER)
            .connectionTimeout(HTTP_CONNECTION_TIMEOUT)
            .responseTimeout(HTTP_RESPONSE_TIMEOUT)
            .build();

        DockerClient client = DockerClientImpl.getInstance(config, httpClient);
        log.info(
            "Docker sandbox client configured: host={}, tlsVerify={}",
            properties.dockerHost(),
            properties.tlsVerify()
        );

        return client;
    }

    @Bean
    public DockerClientOperations dockerClientOperations(DockerClient dockerClient) {
        return new DockerClientOperations(dockerClient);
    }

    @Bean
    public ContainerSecurityPolicy containerSecurityPolicy(SandboxProperties properties) {
        String seccompJson = loadSeccompProfile("sandbox/agent-seccomp-profile.json");
        return new ContainerSecurityPolicy(properties, seccompJson);
    }

    @Bean
    public SandboxNetworkManager sandboxNetworkManager(DockerClientOperations ops, SandboxProperties properties) {
        return new SandboxNetworkManager(ops, properties);
    }

    @Bean
    public SandboxWorkspaceManager sandboxWorkspaceManager(DockerClientOperations ops, SandboxProperties properties) {
        return new SandboxWorkspaceManager(
            ops,
            SandboxWorkspaceManager.MAX_OUTPUT_BYTES,
            SandboxWorkspaceManager.MAX_SINGLE_FILE_BYTES,
            properties.maxDirectoryBytes(),
            properties.maxDirectoryEntries()
        );
    }

    /**
     * Dedicated platform thread pool for Docker blocking wait operations.
     *
     * <p>docker-java's Apache HttpClient5 has {@code synchronized} blocks that pin virtual threads in
     * Java 21, causing cascading failures. A dedicated bounded pool of platform threads avoids this.
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService dockerWaitExecutor(SandboxProperties properties) {
        return Executors.newFixedThreadPool(
            properties.maxConcurrentContainers(),
            Thread.ofPlatform().name("docker-wait-", 0).daemon(true).factory()
        );
    }

    @Bean
    public SandboxContainerManager sandboxContainerManager(
        DockerClientOperations ops,
        SandboxProperties properties,
        ExecutorService dockerWaitExecutor
    ) {
        return new SandboxContainerManager(ops, properties, dockerWaitExecutor);
    }

    @Bean
    public SandboxManager dockerSandboxAdapter(
        SandboxNetworkManager networkManager,
        SandboxWorkspaceManager workspaceManager,
        SandboxContainerManager containerManager,
        ContainerSecurityPolicy securityPolicy,
        SandboxProperties properties,
        @Value("${server.port:8080}") int serverPort,
        MeterRegistry meterRegistry
    ) {
        return new DockerSandboxAdapter(
            networkManager,
            workspaceManager,
            containerManager,
            securityPolicy,
            properties,
            serverPort,
            meterRegistry
        );
    }

    @Bean
    public SandboxReconciler sandboxReconciler(
        AgentJobRepository jobRepository,
        SandboxContainerManager containerManager,
        SandboxNetworkManager networkManager,
        MeterRegistry meterRegistry
    ) {
        return new SandboxReconciler(jobRepository, containerManager, networkManager, meterRegistry);
    }

    @Bean
    public DockerHealthIndicator dockerHealthIndicator(
        SandboxContainerManager containerManager,
        SandboxProperties properties
    ) {
        return new DockerHealthIndicator(containerManager, properties);
    }

    // Interactive (mentor) sandbox — sibling of SandboxManager.

    @Bean
    public InteractiveSandboxMetrics interactiveSandboxMetrics(MeterRegistry meterRegistry) {
        return new InteractiveSandboxMetrics(meterRegistry);
    }

    @Bean
    public StdinWriteWatchdog stdinWriteWatchdog() {
        return new StdinWriteWatchdog();
    }

    @Bean
    public InteractiveSandboxRegistry interactiveSandboxRegistry(
        InteractiveSandboxProperties properties,
        SandboxContainerManager containerManager,
        InteractiveSandboxMetrics metrics,
        StdinWriteWatchdog watchdog,
        MeterRegistry meterRegistry
    ) {
        return new InteractiveSandboxRegistry(properties, containerManager, metrics, watchdog, meterRegistry);
    }

    @Bean
    public InteractiveSandboxService dockerInteractiveSandboxAdapter(
        InteractiveSandboxProperties interactiveProperties,
        SandboxProperties sandboxProperties,
        SandboxNetworkManager networkManager,
        SandboxWorkspaceManager workspaceManager,
        SandboxContainerManager containerManager,
        ContainerSecurityPolicy securityPolicy,
        InteractiveSandboxRegistry registry,
        InteractiveSandboxMetrics metrics,
        ObjectMapper mapper,
        // Shared platform-thread executor (declared above for the sync sandbox); the interactive
        // adapter runs runClose() here too — docker-java sync calls pin virtual carriers on JDK 21.
        ExecutorService dockerWaitExecutor,
        @Value("${hephaestus.mentor.docker-cli:docker}") String dockerCli,
        @Value("${server.port:8080}") int serverPort,
        MentorProxyCredentialRegistry mentorProxyCredentialRegistry
    ) {
        return new DockerInteractiveSandboxAdapter(
            interactiveProperties,
            sandboxProperties,
            networkManager,
            workspaceManager,
            containerManager,
            securityPolicy,
            registry,
            metrics,
            mapper,
            dockerWaitExecutor,
            dockerCli,
            serverPort,
            mentorProxyCredentialRegistry
        );
    }

    /**
     * Bounded platform thread pool for sandbox Docker operations.
     *
     * <p>Uses direct handoff ({@code queueCapacity=0}) so that when all threads are occupied,
     * submissions are rejected immediately — clean backpressure to the NATS consumer, which will
     * redeliver the message later.
     */
    @Bean(name = "sandboxExecutor")
    public AsyncTaskExecutor sandboxExecutor(SandboxProperties properties) {
        var executor = new ThreadPoolTaskExecutor();
        int maxPool = properties.maxConcurrentContainers();
        executor.setCorePoolSize(Math.min(2, maxPool));
        executor.setMaxPoolSize(maxPool);
        executor.setQueueCapacity(0);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("sandbox-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        log.info("Sandbox executor configured: maxConcurrent={}", properties.maxConcurrentContainers());
        return executor;
    }

    // Internal helpers

    /**
     * Load a seccomp profile from the classpath once at startup.
     *
     * <p>Fails hard if the resource is missing — a missing seccomp profile would silently degrade
     * container security, which is unacceptable.
     *
     * @param resourcePath classpath resource path
     * @return the JSON string
     * @throws SandboxException if the resource is missing or unreadable
     */
    private String loadSeccompProfile(String resourcePath) {
        try {
            var resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                throw new SandboxException(
                    "Required seccomp profile not found on classpath: " +
                        resourcePath +
                        ". Sandbox cannot start without a seccomp profile."
                );
            }
            try (var is = resource.getInputStream()) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                log.info("Loaded seccomp profile: {} ({} bytes)", resourcePath, json.length());
                return json;
            }
        } catch (SandboxException e) {
            throw e;
        } catch (IOException e) {
            throw new SandboxException("Failed to load seccomp profile: " + resourcePath, e);
        }
    }
}
