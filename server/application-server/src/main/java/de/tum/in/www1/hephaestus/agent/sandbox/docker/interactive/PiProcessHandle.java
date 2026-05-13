package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a {@code docker exec -i <container> <runner...>} subprocess.
 *
 * <p>Owns the {@link Process} returned by {@link ProcessBuilder#start()}. Exposes a UTF-8
 * {@link Reader} over stdout (raw read — {@link JsonlStdoutPump} does the framing because
 * {@code readLine()} would mis-split on bare {@code \r}), a raw stdin {@link OutputStream}, and
 * the destruction hooks the session needs. Stderr is drained on a separate virtual thread so the
 * kernel-side pipe (≈64 KB on Linux) never fills and back-pressures the runner.
 *
 * <p>Termination uses {@link Process#destroyForcibly()} for grace-deadline expiry. The graceful
 * path goes through {@code docker stop} on the container in
 * {@code DockerAttachedSandboxAdapter.close()} — destroying the exec subprocess alone does not
 * stop the container.
 */
final class PiProcessHandle {

    private static final Logger log = LoggerFactory.getLogger(PiProcessHandle.class);

    private static final int STDERR_LINE_CAP = 4096;

    private final Process process;
    private final Reader stdout;
    private final OutputStream stdin;
    private final Thread stderrDrainer;
    private final String containerId;

    private PiProcessHandle(Process process, String containerId) {
        this.process = process;
        this.containerId = containerId;
        this.stdout = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
        this.stdin = process.getOutputStream();
        this.stderrDrainer = Thread.ofVirtual()
            .name("mentor-stderr-" + containerId)
            .uncaughtExceptionHandler((t, ex) -> log.debug("Stderr drainer died", ex))
            .start(this::drainStderr);
    }

    /**
     * Spawn {@code docker exec -i -u 1000:1000 <containerId> <command...>}.
     *
     * <p>Explicit {@code -u} guards against the container's {@code USER} setting being overridden
     * by a later refactor — the runner must never have host-equivalent privileges inside its
     * namespace. Env keys are validated by {@link de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec},
     * so we materialise them as {@code -e KEY=VALUE} without escaping; {@code docker exec}'s
     * argv parser treats each token after {@code -e} as opaque.
     */
    static PiProcessHandle spawn(
        String dockerCli,
        String containerId,
        String containerUser,
        List<String> command,
        Map<String, String> environment
    ) {
        List<String> argv = new ArrayList<>();
        argv.add(dockerCli);
        argv.add("exec");
        argv.add("-i");
        // -i (interactive) but never -t (no TTY): TIOCSTI injection is a kernel-level concern
        // that doesn't apply when there is no controlling terminal.
        argv.add("-u");
        argv.add(containerUser);
        for (Map.Entry<String, String> e : environment.entrySet()) {
            argv.add("-e");
            argv.add(e.getKey() + "=" + e.getValue());
        }
        argv.add(containerId);
        argv.addAll(command);

        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(false);
        try {
            Process p = pb.start();
            log.debug("Spawned docker exec subprocess: containerId={}, pid={}", containerId, p.pid());
            return new PiProcessHandle(p, containerId);
        } catch (IOException e) {
            throw new InteractiveSandboxException(
                "Failed to spawn docker exec for container " + containerId + ": " + e.getMessage(),
                e
            );
        }
    }

    /** Character reader over the runner's stdout in UTF-8. Framing is the caller's responsibility. */
    Reader stdout() {
        return stdout;
    }

    OutputStream stdin() {
        return stdin;
    }

    long pid() {
        return process.pid();
    }

    boolean isAlive() {
        return process.isAlive();
    }

    /**
     * Forcefully terminate the exec subprocess. Closes the underlying FDs, which is the only
     * reliable way to unblock a Java thread parked in {@code OutputStream.write()} on a kernel
     * pipe (interrupt() does not return from that syscall on Linux). The container is unaffected.
     */
    void destroyForcibly() {
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    boolean waitFor(Duration timeout) {
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** @return exit value if exited, {@code -1} if still alive (no overlap with real exit codes 0-255). */
    int exitValueOrAlive() {
        if (process.isAlive()) {
            return -1;
        }
        return process.exitValue();
    }

    /**
     * Wait up to {@code timeout} for the subprocess to exit, then close stdin/stdout and stop
     * draining stderr. Must be bounded — an unbounded wait risks blocking the close virtual
     * thread indefinitely against a hung exec subprocess.
     */
    void awaitExitAndClose(Duration timeout) {
        if (!waitFor(timeout)) {
            log.warn("Exec subprocess did not exit within {}ms; closing FDs anyway", timeout.toMillis());
        }
        stderrDrainer.interrupt();
        try {
            stdout.close();
        } catch (IOException ignored) {}
        try {
            stdin.close();
        } catch (IOException ignored) {}
    }

    private void drainStderr() {
        try (
            BufferedReader err = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)
            )
        ) {
            String line;
            while ((line = err.readLine()) != null) {
                if (line.isEmpty()) continue;
                String truncated = line.length() > STDERR_LINE_CAP ? line.substring(0, STDERR_LINE_CAP) + "…" : line;
                log.debug("[runner-stderr containerId={}] {}", containerId, truncated);
            }
        } catch (IOException e) {
            if (process.isAlive()) {
                log.debug("Stderr drain ended with IOException while process still alive: {}", e.getMessage());
            }
        }
    }
}
