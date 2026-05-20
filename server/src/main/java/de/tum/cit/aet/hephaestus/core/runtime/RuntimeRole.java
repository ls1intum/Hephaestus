package de.tum.cit.aet.hephaestus.core.runtime;

/**
 * Runtime role names — string constants used in {@code @ConditionalOnProperty} keys to gate
 * subsystems by deployment role. Single source of truth so role gating cannot drift between
 * the property file, the ArchUnit boundary test, and the smoke test.
 *
 * <p>See ADR 0005 for the topology. Defaults: every role enabled (single-JVM monolith);
 * production deploys flip the appropriate flag to {@code false} per pod.
 */
public final class RuntimeRole {

    private RuntimeRole() {}

    /**
     * Property prefix for every runtime role flag. Concrete keys are
     * {@code hephaestus.runtime.<role>.enabled}.
     */
    public static final String PROPERTY_PREFIX = "hephaestus.runtime";

    /**
     * Reserved property key for the future server-only role gate. NOT wired in this epic —
     * the server-side bean chain (HTTP API, sync NATS, mentor SSE, scheduled tasks, agent
     * dispatch) still loads regardless of this flag. Wiring real server-only mode is a
     * follow-up epic when a concrete deploy-split need surfaces.
     */
    public static final String SERVER_PROPERTY = PROPERTY_PREFIX + ".server.enabled";

    /**
     * Wired property key for the worker-role gate. Gates {@code DockerSandboxConfiguration}
     * and {@code AgentNatsConsumerConfig}. Setting to {@code false} removes the Docker
     * sandbox runtime and the agent NATS pull consumer from this JVM; the rest of the
     * monolith continues to load.
     */
    public static final String WORKER_PROPERTY = PROPERTY_PREFIX + ".worker.enabled";

    /**
     * Capability flag (NOT a role): toggles the worker-internal LLM proxy controller +
     * security chain. Default true on the server; the BYO-runner trust model keeps LLM
     * credentials on the coordinator (see ADR 0006).
     */
    public static final String SANDBOX_LLM_PROXY_PROPERTY = "hephaestus.sandbox.llm-proxy.enabled";
}
