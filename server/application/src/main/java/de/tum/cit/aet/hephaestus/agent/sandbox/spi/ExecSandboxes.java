package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Sandboxes that stay up and run one command at a time on request, as Heph's turn sandboxes do. A sandbox has the
 * hardened configuration of a practice review, no network, no credential and no environment from this worker. Its
 * {@code /workspace} and {@code /tmp} are memory file systems of the sandbox itself, so removing the sandbox removes
 * every file in it. Commands run as the image's unprivileged user unless they run as root to stage files.
 */
public interface ExecSandboxes {

    /** The unprivileged user of the image, which runs the model's commands. */
    String SANDBOX_USER = "1000";

    /**
     * Creates and starts an empty sandbox.
     *
     * @param sandboxId the caller's id of the sandbox, recorded on it so that a cleanup can find it later
     * @return the runtime's id of the sandbox
     * @throws SandboxException when the sandbox cannot start
     */
    String create(String sandboxId, ResourceLimits limits);

    /**
     * Runs one command in the sandbox and waits for it, at most for {@code timeout}. A command that runs longer is left
     * running: the caller kills it or removes the sandbox.
     *
     * @throws SandboxException when the sandbox is gone or the runtime fails
     */
    Execution exec(String sandbox, Command command);

    /** Removes the sandbox and every file in it. Best effort: a sandbox that is already gone is no error. */
    void remove(String sandbox);

    /** The sandboxes of this installation, of every worker. */
    List<Listed> list();

    /**
     * The id this worker records on the sandboxes it creates. It stays the same when the worker restarts, so its next
     * start removes what a crash left. Null when the worker cannot name itself: it then starts no sandbox.
     */
    @Nullable
    String workerInstance();

    /**
     * @param argv the command, run without a shell
     * @param root whether it runs as root, to stage files; otherwise as {@link #SANDBOX_USER}
     * @param stdin what the command reads, or null for nothing
     * @param outputLimit how many bytes of each of stdout and stderr are kept; the rest is dropped
     */
    record Command(
            List<String> argv, boolean root, @Nullable InputStream stdin, Duration timeout, int outputLimit) {
        public Command {
            argv = List.copyOf(argv);
        }
    }

    /** What one command did. Each output keeps at most the command's limit. */
    final class Execution {
        private final @Nullable Integer exitCode;
        private final byte[] stdout;
        private final byte[] stderr;
        private final boolean truncated;

        /**
         * @param exitCode null when the command still ran at the timeout
         * @param truncated whether stdout or stderr was longer than the limit
         */
        public Execution(@Nullable Integer exitCode, byte[] stdout, byte[] stderr, boolean truncated) {
            this.exitCode = exitCode;
            this.stdout = stdout.clone();
            this.stderr = stderr.clone();
            this.truncated = truncated;
        }

        public @Nullable Integer exitCode() {
            return exitCode;
        }

        public byte[] stdout() {
            return stdout.clone();
        }

        public byte[] stderr() {
            return stderr.clone();
        }

        public boolean truncated() {
            return truncated;
        }

        public boolean timedOut() {
            return exitCode == null;
        }
    }

    /**
     * @param workerGone whether the worker that created the sandbox is positively gone; an unknown state is not gone
     */
    record Listed(String id, String sandboxId, String workerInstance, boolean workerGone) {}
}
