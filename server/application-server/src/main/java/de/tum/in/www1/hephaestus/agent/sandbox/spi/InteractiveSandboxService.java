package de.tum.in.www1.hephaestus.agent.sandbox.spi;

/**
 * Service Provider Interface for long-lived attached sandbox containers with streamable JSONL IO.
 *
 * <p>Sibling to {@link SandboxManager}: where {@code SandboxManager.execute} is a one-shot blocking
 * call that runs an agent to completion, {@link #attach} returns an open channel to a runner
 * process and the caller drives the conversation via {@link AttachedSandbox#send} and
 * {@link AttachedSandbox#subscribe}.
 *
 * <h2>Architectural boundary</h2>
 *
 * <p>This SPI is the only way for callers outside {@code agent.sandbox.docker} to attach a runner.
 * Two architectural rules enforce the separation (see
 * {@code de.tum.in.www1.hephaestus.architecture.SandboxArchitectureTest}):
 *
 * <ul>
 *   <li>{@code agent.mentor.**} must not invoke {@link SandboxManager#execute} — the mentor flow
 *       is interactive by definition; the sync path is for one-shot agents.
 *   <li>{@code agent.practice.**} must not invoke {@link #attach} — practice review is a one-shot
 *       agent; interactive is reserved for tutoring.
 * </ul>
 *
 * <h2>Concurrency</h2>
 *
 * <p>Implementations must be thread-safe. Concurrent {@link #attach} calls with the same
 * {@code (userId, workspaceId)} key share the returned {@link AttachedSandbox} — second caller
 * does not get a new session.
 *
 * <h2>Multi-replica caveat (#1077)</h2>
 *
 * <p>The default Docker implementation keeps state in an in-process registry. In a multi-replica
 * deployment, a user reconnecting via SSE may land on a replica that does not hold their session.
 * Session affinity is the responsibility of the routing layer and is tracked in #1077; the
 * registry emits a startup WARN when {@code hephaestus.deployment.replica-count > 1}.
 */
public interface InteractiveSandboxService {
    /**
     * Attach to a runner inside a sandbox container.
     *
     * <p>If a session for {@code (spec.userId(), spec.workspaceId())} already exists and is alive,
     * the existing {@link AttachedSandbox} is returned. Otherwise a new container is created, the
     * runner is execed inside, and a fresh handle is registered.
     *
     * <p>The method blocks until the runner emits its first stdout frame, or
     * {@code hephaestus.mentor.attach-first-frame-timeout-seconds} elapses — bounding the
     * latency-of-discovery for "runner failed to start". Implementations must time and record
     * spawn → first-frame in the {@code mentor_attach_duration_seconds} timer.
     *
     * @param spec the session specification
     * @return a live handle to the session
     * @throws InteractiveSandboxException if container creation, exec, or first-frame wait fails;
     *     if the per-user or per-replica session cap is exceeded; or if the daemon is unreachable
     */
    AttachedSandbox attach(InteractiveSandboxSpec spec) throws InteractiveSandboxException;

    /** @return {@code true} if the Docker daemon is reachable (mirrors {@link SandboxManager#isHealthy}). */
    boolean isHealthy();
}
