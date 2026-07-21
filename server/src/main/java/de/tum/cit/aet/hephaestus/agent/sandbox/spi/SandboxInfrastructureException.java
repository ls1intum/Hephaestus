package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

/**
 * Narrow subtype of {@link SandboxException} reserved for failures PROVABLY caused by transient
 * sandbox/container infrastructure — a Docker daemon call that failed, timed out, or was interrupted
 * (network create/connect, container create/start/wait/stop/remove, file injection/collection I/O) —
 * as opposed to validation, configuration, or unexpected-defect failures (#1368 fix wave, finding #7).
 *
 * <p>{@code AgentJobExecutor#isRetryableInfraFailure} checks specifically for this subtype (not the
 * broader {@link SandboxException}) when deciding whether a failed job is safe to requeue for retry.
 * The distinction matters: a plain {@link SandboxException} — e.g. a path-traversal rejection, an
 * input-size-limit violation, a misconfigured {@code allow_internet}/network policy, or {@code
 * DockerSandboxAdapter}'s catch-all wrap of an unexpected exception — is deterministic across retries
 * (it will fail identically every time) or an unknown defect that should not be assumed safe to retry.
 * Retrying those anyway would let a genuinely broken job silently burn its whole retry budget on a
 * failure that was never going to resolve itself, instead of failing fast as it did before this
 * hardening existed. Only failures thrown from an ACTUAL {@code DockerException}/I/O-wrapping call site
 * — the kind that self-heals when the daemon recovers, a transient network blip clears, or contention
 * eases — use this subtype.
 */
public class SandboxInfrastructureException extends SandboxException {

    public SandboxInfrastructureException(String message) {
        super(message);
    }

    public SandboxInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
