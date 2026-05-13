/**
 * Docker-backed implementation of the {@link de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxService}
 * SPI.
 *
 * <p>Architecturally interesting choices (called out so future maintainers don't have to re-derive
 * them):
 *
 * <ul>
 *   <li><b>Subprocess {@code docker exec -i}, not docker-java's {@code ExecStartCmd}.</b>
 *       docker-java's long-lived streaming path has open bugs that bite the exact shape of this
 *       workload — see {@link PiProcessHandle} for the rationale.
 *   <li><b>Tar-via-{@code docker cp}, not bind mounts.</b> Remote-daemon-compatible (no host-fs
 *       coupling) and works without {@code CAP_CHOWN} on the app-server process.
 *   <li><b>Per-subscriber bounded queue + virtual-thread dispatcher.</b> See
 *       {@link FrameSubscription} — protects the pump from any single slow listener.
 *   <li><b>Watchdog-driven write timeout.</b> See {@link StdinWriteWatchdog} — the only escape
 *       from a Java thread parked inside {@code OutputStream.write()} on a kernel pipe is to
 *       close the FD.
 * </ul>
 */
package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;
