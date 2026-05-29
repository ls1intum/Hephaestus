package de.tum.cit.aet.hephaestus.agent.runtime.worker;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link WorkerProperties} whenever the worker role is enabled — independent of whether the WSS
 * control channel is configured. Worker identity ({@code resolvedWorkerId()}) is a job-execution
 * concern (job ownership + orphan recovery, #1138), not a WSS concern; gating it on the WSS endpoint
 * (as {@link WorkerConfiguration} does for the control-channel beans) would silently disable orphan
 * recovery on a NATS-only worker. This keeps the two concerns separate so identity is always present
 * wherever {@code AgentJobExecutor} / {@code WorkerLivenessReporter} run.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerPropertiesConfiguration {}
