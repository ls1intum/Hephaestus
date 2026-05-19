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

    /** HTTP API, sync NATS consumer, mentor SSE, scheduled tasks, agent dispatch chain. */
    public static final String SERVER_PROPERTY = PROPERTY_PREFIX + ".server.enabled";

    /** Agent NATS pull consumer + Docker sandbox runtime + worker reconcilers + sweepers. */
    public static final String WORKER_PROPERTY = PROPERTY_PREFIX + ".worker.enabled";

    /**
     * Capability flag (NOT a role): toggles the worker-internal LLM proxy controller +
     * security chain. Default true on the server; the BYO-runner trust model keeps LLM
     * credentials on the coordinator (see ADR 0006).
     */
    public static final String SANDBOX_LLM_PROXY_PROPERTY = "hephaestus.sandbox.llm-proxy.enabled";
}
