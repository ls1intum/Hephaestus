/**
 * Docker implementation of {@link de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxService}.
 *
 * <p>Two design choices a reader can't derive from the code alone:
 *
 * <ul>
 *   <li><b>Subprocess {@code docker exec -i}, not docker-java's {@code ExecStartCmd}.</b>
 *       docker-java's long-lived streaming path has open bugs (FrameReader hang, stdout/stdin
 *       not closed on exec, idle-stream thread leak, no clean cancel) and pins virtual carriers
 *       via Apache HttpClient5's {@code synchronized} internals on JDK 21. Subprocess gives
 *       native pipes and {@link Process#destroyForcibly()} as a real cancel.
 *   <li><b>Tar over {@code docker cp}, not bind mounts.</b> Preserves remote-daemon
 *       compatibility and works without {@code CAP_CHOWN} (which the security policy drops).
 * </ul>
 */
package de.tum.cit.aet.hephaestus.agent.sandbox.docker.interactive;
