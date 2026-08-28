package de.tum.cit.aet.hephaestus.agent.runtime.worker;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link WorkerProperties} whenever the worker role is enabled — deliberately NOT also gated on
 * the WSS endpoint the way {@link WorkerConfiguration} is. Worker identity drives job ownership and
 * orphan recovery, which a worker without a control channel still needs.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerPropertiesConfiguration {}
