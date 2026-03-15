package de.tum.in.www1.hephaestus.agent.sandbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the Docker sandbox manager.
 *
 * <p>Controls Docker daemon connectivity, container resource limits, security defaults, and
 * reconciliation intervals. Bound from {@code hephaestus.sandbox.*} in {@code application.yml}.
 *
 * <h2>Activation</h2>
 *
 * <p>The sandbox subsystem is disabled by default ({@code enabled=false}). Set {@code
 * hephaestus.sandbox.enabled=true} to activate. Requires a reachable Docker daemon.
 *
 * <h2>Deployment Requirements</h2>
 *
 * <ul>
 *   <li>Docker Engine &ge; 25.0.5 (CVE-2024-29018 DNS exfiltration fix)
 *   <li>Cloud metadata blocking: {@code iptables -I DOCKER-USER -d 169.254.0.0/16 -j DROP}
 *   <li>Recommended: Tecnativa docker-socket-proxy with {@code EXEC=0}
 *   <li>Recommended: {@code userns-remap=default} in {@code /etc/docker/daemon.json}
 *   <li>Optional: gVisor for {@code container-runtime=runsc}
 * </ul>
 *
 * @param enabled whether the sandbox subsystem is active
 * @param dockerHost Docker daemon endpoint
 * @param tlsVerify enable TLS verification for TCP connections
 * @param certPath path to TLS certificates (required when tlsVerify=true)
 * @param maxConcurrentContainers upper bound on simultaneous sandbox containers
 * @param containerStopTimeoutSeconds SIGTERM → SIGKILL grace period
 * @param reconciliationIntervalSeconds interval between orphan cleanup sweeps
 * @param containerRuntime OCI runtime (null=runc, "runsc"=gVisor)
 * @param llmProxyPort port on which the LLM proxy listens (inside the app-server container)
 * @param appServerContainerId Docker container ID of the app-server (null=auto-detect from
 *     HOSTNAME)
 * @param defaultResourceLimits default resource constraints for containers
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.sandbox")
public record SandboxProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("unix:///var/run/docker.sock") @NotBlank String dockerHost,
    @DefaultValue("false") boolean tlsVerify,
    @Nullable String certPath,
    @DefaultValue("5") @Min(1) int maxConcurrentContainers,
    @DefaultValue("10") @Min(1) int containerStopTimeoutSeconds,
    @DefaultValue("60") @Min(10) int reconciliationIntervalSeconds,
    @Nullable String containerRuntime,
    @DefaultValue("8080") int llmProxyPort,
    @Nullable String appServerContainerId,
    @Valid DefaultResourceLimits defaultResourceLimits) {
  public SandboxProperties {
    if (defaultResourceLimits == null) {
      defaultResourceLimits = new DefaultResourceLimits(4L * 1024 * 1024 * 1024, 2.0, 256);
    }
  }

  /**
   * Returns the configured app-server container ID, or null if not explicitly set.
   *
   * <p>When null, the caller should fall back to the {@code HOSTNAME} environment variable, which
   * Docker sets to the container's short ID by default.
   *
   * @return the container ID, or null if auto-detection is needed
   */
  public String resolvedAppServerContainerId() {
    if (appServerContainerId != null && !appServerContainerId.isBlank()) {
      return appServerContainerId;
    }
    return null;
  }

  /**
   * Default resource limits for agent containers.
   *
   * @param memoryBytes maximum memory in bytes (includes tmpfs)
   * @param cpus CPU limit
   * @param pidsLimit maximum process count
   */
  public record DefaultResourceLimits(
      @DefaultValue("4294967296") long memoryBytes,
      @DefaultValue("2.0") double cpus,
      @DefaultValue("256") int pidsLimit) {}
}
