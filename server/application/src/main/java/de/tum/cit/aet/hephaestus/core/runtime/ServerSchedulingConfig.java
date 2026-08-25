package de.tum.cit.aet.hephaestus.core.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates Spring {@link EnableScheduling @EnableScheduling} only on the server-role pod.
 *
 * <p>This single switch silences every {@code @Scheduled} method on the {@code webhook-server}
 * container (which sets {@code hephaestus.runtime.server.enabled=false}) — preventing double-run
 * pathologies for sync schedulers (GitHub/GitLab data sync, historical backfill), agent zombie
 * sweepers, mentor in-flight reaper, contributor cache eviction, rate-limit eviction, and the
 * GitLab webhook health check.
 *
 * <p>{@code matchIfMissing=true} preserves ADR 0005's DX invariant: zero env vars → full monolith
 * boots with scheduling enabled.
 *
 * <p>Worker-side scheduled tasks (sandbox reconciler tick, interactive sandbox reaper, stdin
 * watchdog) are owned by the worker role: their hosting beans are wired only when
 * {@code DockerSandboxConfiguration} loads, which is itself gated by
 * {@code RuntimeRole.WORKER_PROPERTY}. No additional gating is needed there.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class ServerSchedulingConfig {}
