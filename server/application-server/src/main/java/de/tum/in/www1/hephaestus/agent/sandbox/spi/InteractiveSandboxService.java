package de.tum.in.www1.hephaestus.agent.sandbox.spi;

/**
 * SPI for long-lived attached sandbox containers with streamable JSONL I/O — sibling to
 * {@link SandboxManager}, which is one-shot. Concurrent {@link #attach} calls with the same
 * {@code (userId, workspaceId)} share the returned handle. {@code SandboxArchitectureTest}
 * enforces that {@code agent.mentor.**} uses this SPI exclusively and {@code agent.practice.**}
 * uses {@code SandboxManager} exclusively.
 */
public interface InteractiveSandboxService {
    /**
     * Spawn (or join) a runner inside a sandbox container. Blocks until the runner emits its
     * first stdout frame or the configured first-frame timeout elapses.
     *
     * @throws InteractiveSandboxException on container/exec/first-frame failure or capacity exhaustion
     */
    AttachedSandbox attach(InteractiveSandboxSpec spec) throws InteractiveSandboxException;

    /** @return {@code true} if the Docker daemon is reachable. */
    boolean isHealthy();
}
