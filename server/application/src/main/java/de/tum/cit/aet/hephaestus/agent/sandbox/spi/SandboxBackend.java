package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Runs sandboxes on one container runtime. The attempt protocol (the gateway session, the environment, the deadlines,
 * cancellation, the result and its metrics) is the runner's, once for every runtime; a backend only starts, observes,
 * stops and removes what it runs.
 *
 * <p>A sandbox runs two processes of one image, in order. The initializer fills {@code /workspace} from the worker's
 * gateway and may write it. The runtime then runs with {@code /workspace} read-only. Each backend guarantees, for both:
 * the user {@link ExecSandboxes#SANDBOX_USER}, a read-only root file system, no capability, no privilege escalation,
 * the memory, CPU and process limits of the launch, no credential and no environment but the launch's, and no network
 * destination but the worker's gateway. Only a Heph host whose worker runs outside any container may get a route out
 * ({@code DockerSandboxBackend}).
 *
 * <p>Every method may be called from any thread; {@link #kill} in particular while another thread observes.
 */
public interface SandboxBackend {

    /**
     * Creates the sandbox's isolation and storage and starts its initializer. It returns once the backend accepted the
     * sandbox, before the initializer ended.
     *
     * @throws SandboxException when the backend refuses the sandbox
     * @throws SandboxInfrastructureException when the runtime fails in a way that can heal
     */
    Handle launch(Launch launch);

    /**
     * Waits until the sandbox reached the stage, or until the deadline. The runtime starts only after the initializer
     * succeeded, and never after {@link #kill}; a backend may start it when it is first observed.
     *
     * <ul>
     *   <li>{@link Stage#INITIALIZED}: the initializer ended, with its exit code.
     *   <li>{@link Stage#STARTED}: the runtime runs; an exit code when it already ended.
     *   <li>{@link Stage#ENDED}: the runtime ended, with its exit code; none when it never ran.
     * </ul>
     *
     * When the deadline passes first, the backend stops the stage's process (as {@link #kill}) and reports it timed out,
     * with the exit code that the stop left, if any.
     */
    Observation observe(Handle handle, Stage stage, Instant deadline);

    /** Whether the sandbox's runtime runs now. */
    boolean running(Handle handle);

    /**
     * The container's output, read once it ended: whole, or past the backend's bound its beginning and its end with a
     * line between them that says how much is missing, never silently cut. Empty when it cannot be read. A failed
     * review's transcript is its only diagnostic, so the runner keeps it in the result.
     */
    String transcript(Handle handle, Container container);

    /**
     * Ends the sandbox's processes, with their grace period; the sandbox keeps its transcripts until it is removed. A
     * sandbox that already ended is no error.
     */
    void kill(Handle handle);

    /** Removes the sandbox and everything it holds. A sandbox that is gone is no error. */
    void remove(Handle handle);

    enum Kind {
        /** A practice review attempt: it runs to its own end within its deadlines. */
        REVIEW,
        /** A Heph host: it runs until it is removed. */
        HOST
    }

    enum Stage {
        INITIALIZED,
        STARTED,
        ENDED
    }

    enum Container {
        INITIALIZER,
        RUNTIME
    }

    /**
     * @param id the review job, or the Heph host, the sandbox serves
     * @param attempt the claimed job's attempt; null for a host, or a caller that holds no claim
     * @param command the runtime's command; the initializer's is the image's
     * @param environment the environment of both processes, built for the gateway address the backend gives the sandbox
     */
    record Launch(
            Kind kind,
            UUID id,
            @Nullable Integer attempt,
            String image,
            List<String> command,
            ResourceLimits limits,
            Environment environment) {
        public Launch {
            command = List.copyOf(command);
        }
    }

    /** The sandbox's environment for its gateway, such as {@code http://10.0.0.2:8081}. */
    @FunctionalInterface
    interface Environment {
        Map<String, String> at(String gateway);
    }

    /** A launched sandbox, as its backend names it. */
    record Handle(String id) {}

    /**
     * @param timedOut whether the deadline passed before the stage
     * @param exitCode the exit code of the stage's process, when it ended
     */
    record Observation(boolean timedOut, @Nullable Integer exitCode) {}
}
