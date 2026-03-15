package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.Ulimit;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production implementation of Docker operations using docker-java.
 *
 * <p>Implements all three focused operation interfaces. Each method is a thin delegate to {@link
 * DockerClient}, translating {@link DockerException} to {@link SandboxException}.
 */
public class DockerClientOperations
    implements DockerContainerOperations, DockerNetworkOperations, DockerFileOperations {

  private static final Logger log = LoggerFactory.getLogger(DockerClientOperations.class);
  private static final int LOG_COLLECTION_TIMEOUT_SECONDS = 30;

  /** Maximum log output size to prevent OOM from runaway containers. */
  private static final int MAX_LOG_BYTES = 1024 * 1024; // 1 MB

  private final DockerClient dockerClient;

  public DockerClientOperations(DockerClient dockerClient) {
    this.dockerClient = dockerClient;
  }

  @Override
  public boolean ping() {
    try {
      dockerClient.pingCmd().exec();
      return true;
    } catch (Exception e) {
      log.debug("Docker ping failed: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public String createNetwork(String name, boolean internal) {
    try {
      CreateNetworkResponse response =
          dockerClient
              .createNetworkCmd()
              .withName(name)
              .withDriver("bridge")
              .withInternal(internal)
              .withCheckDuplicate(true)
              .exec();
      log.debug(
          "Created Docker network: name={}, id={}, internal={}", name, response.getId(), internal);
      return response.getId();
    } catch (DockerException e) {
      throw new SandboxException("Failed to create network: " + name, e);
    }
  }

  @Override
  public String connectToNetwork(String networkId, String containerId) {
    try {
      dockerClient
          .connectToNetworkCmd()
          .withNetworkId(networkId)
          .withContainerId(containerId)
          .exec();

      // Read back the assigned IP
      Network network = dockerClient.inspectNetworkCmd().withNetworkId(networkId).exec();
      Network.ContainerNetworkConfig containerConfig = network.getContainers().get(containerId);
      if (containerConfig == null) {
        throw new SandboxException(
            "Container " + containerId + " not found in network " + networkId + " after connect");
      }
      String ipAddress = containerConfig.getIpv4Address();
      // Remove CIDR suffix if present (e.g. "172.18.0.2/16" -> "172.18.0.2")
      if (ipAddress != null && ipAddress.contains("/")) {
        ipAddress = ipAddress.substring(0, ipAddress.indexOf('/'));
      }
      log.debug("Connected container {} to network {}: ip={}", containerId, networkId, ipAddress);
      return ipAddress;
    } catch (DockerException e) {
      throw new SandboxException(
          "Failed to connect container " + containerId + " to network " + networkId, e);
    }
  }

  @Override
  public void disconnectFromNetwork(String networkId, String containerId) {
    try {
      dockerClient
          .disconnectFromNetworkCmd()
          .withNetworkId(networkId)
          .withContainerId(containerId)
          .withForce(true)
          .exec();
      log.debug("Disconnected container {} from network {}", containerId, networkId);
    } catch (NotFoundException | NotModifiedException e) {
      log.debug("Container {} already disconnected from network {}", containerId, networkId);
    } catch (DockerException e) {
      throw new SandboxException(
          "Failed to disconnect container " + containerId + " from network " + networkId, e);
    }
  }

  @Override
  public void removeNetwork(String networkId) {
    try {
      dockerClient.removeNetworkCmd(networkId).exec();
      log.debug("Removed network: {}", networkId);
    } catch (NotFoundException e) {
      log.debug("Network {} already removed", networkId);
    } catch (DockerException e) {
      throw new SandboxException("Failed to remove network: " + networkId, e);
    }
  }

  @Override
  public String createContainer(DockerOperations.ContainerSpec spec) {
    try {
      HostConfig hostConfig = buildHostConfig(spec.hostConfig());

      CreateContainerCmd cmd =
          dockerClient
              .createContainerCmd(spec.image())
              .withHostConfig(hostConfig)
              .withNetworkMode(spec.networkId())
              .withLabels(spec.labels());

      if (spec.command() != null && !spec.command().isEmpty()) {
        cmd.withCmd(spec.command());
      }
      if (spec.environment() != null && !spec.environment().isEmpty()) {
        cmd.withEnv(
            spec.environment().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList());
      }
      if (spec.hostname() != null) {
        cmd.withHostName(spec.hostname());
      }
      if (spec.user() != null) {
        cmd.withUser(spec.user());
      }

      String containerId = cmd.exec().getId();
      log.debug("Created container: image={}, id={}", spec.image(), containerId);
      return containerId;
    } catch (DockerException e) {
      throw new SandboxException("Failed to create container from image: " + spec.image(), e);
    }
  }

  @Override
  public void startContainer(String containerId) {
    try {
      dockerClient.startContainerCmd(containerId).exec();
      log.debug("Started container: {}", containerId);
    } catch (DockerException e) {
      throw new SandboxException("Failed to start container: " + containerId, e);
    }
  }

  @Override
  public DockerOperations.WaitResult waitContainer(String containerId) {
    try {
      var callback = dockerClient.waitContainerCmd(containerId).start();
      try {
        int exitCode = callback.awaitStatusCode();
        log.debug("Container {} exited with code {}", containerId, exitCode);
        return new DockerOperations.WaitResult(exitCode);
      } finally {
        try {
          callback.close();
        } catch (Exception closeEx) {
          log.debug(
              "Failed to close wait callback for container {}: {}",
              containerId,
              closeEx.getMessage());
        }
      }
    } catch (DockerException e) {
      throw new SandboxException("Failed to wait for container: " + containerId, e);
    } catch (Exception e) {
      throw new SandboxException("Failed waiting for container: " + containerId, e);
    }
  }

  @Override
  public String getLogs(String containerId, int tailLines) {
    // Best-effort log collection: timeout and errors return partial/empty data.
    try {
      // StringBuffer is thread-safe: onNext is called from a Docker callback thread
      StringBuffer logs = new StringBuffer();
      var callback =
          new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame frame) {
              if (frame != null && frame.getPayload() != null && logs.length() < MAX_LOG_BYTES) {
                logs.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
              }
            }
          };

      try {
        dockerClient
            .logContainerCmd(containerId)
            .withStdOut(true)
            .withStdErr(true)
            .withFollowStream(false)
            .withTail(tailLines > 0 ? tailLines : Integer.MAX_VALUE)
            .exec(callback)
            .awaitCompletion(LOG_COLLECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } finally {
        callback.close();
      }

      return logs.toString();
    } catch (Exception e) {
      log.warn("Failed to collect logs for container {}: {}", containerId, e.getMessage());
      return "";
    }
  }

  @Override
  public void copyArchiveToContainer(String containerId, String remotePath, InputStream tarStream) {
    try {
      dockerClient
          .copyArchiveToContainerCmd(containerId)
          .withRemotePath(remotePath)
          .withTarInputStream(tarStream)
          .exec();
      log.debug("Copied archive to container {} at {}", containerId, remotePath);
    } catch (DockerException e) {
      throw new SandboxException(
          "Failed to copy archive to container " + containerId + " at " + remotePath, e);
    }
  }

  @Override
  public InputStream copyArchiveFromContainer(String containerId, String remotePath) {
    try {
      return dockerClient.copyArchiveFromContainerCmd(containerId, remotePath).exec();
    } catch (DockerException e) {
      throw new SandboxException(
          "Failed to copy archive from container " + containerId + " at " + remotePath, e);
    }
  }

  @Override
  public void stopContainer(String containerId, int timeoutSeconds) {
    try {
      dockerClient.stopContainerCmd(containerId).withTimeout(timeoutSeconds).exec();
      log.debug("Stopped container {}: timeout={}s", containerId, timeoutSeconds);
    } catch (NotModifiedException e) {
      log.debug("Container {} already stopped", containerId);
    } catch (DockerException e) {
      throw new SandboxException("Failed to stop container: " + containerId, e);
    }
  }

  @Override
  public void removeContainer(String containerId, boolean force) {
    try {
      dockerClient.removeContainerCmd(containerId).withForce(force).withRemoveVolumes(true).exec();
      log.debug("Removed container: id={}, force={}", containerId, force);
    } catch (NotFoundException e) {
      log.debug("Container {} already removed", containerId);
    } catch (DockerException e) {
      throw new SandboxException("Failed to remove container: " + containerId, e);
    }
  }

  @Override
  public List<DockerOperations.ContainerInfo> listContainersByLabel(
      String labelKey, String labelValue) {
    try {
      List<Container> containers =
          dockerClient
              .listContainersCmd()
              .withLabelFilter(Map.of(labelKey, labelValue))
              .withShowAll(true)
              .exec();
      return containers.stream()
          .map(
              c ->
                  new DockerOperations.ContainerInfo(
                      c.getId(),
                      c.getNames() != null && c.getNames().length > 0 ? c.getNames()[0] : "",
                      c.getLabels() != null ? c.getLabels() : Map.of(),
                      c.getState() != null ? c.getState() : "unknown"))
          .toList();
    } catch (DockerException e) {
      throw new SandboxException(
          "Failed to list containers by label: " + labelKey + "=" + labelValue, e);
    }
  }

  @Override
  public List<DockerOperations.NetworkInfo> listNetworksByName(String namePrefix) {
    try {
      List<Network> networks = dockerClient.listNetworksCmd().withNameFilter(namePrefix).exec();
      return networks.stream()
          .map(n -> new DockerOperations.NetworkInfo(n.getId(), n.getName()))
          .toList();
    } catch (DockerException e) {
      throw new SandboxException("Failed to list networks with prefix: " + namePrefix, e);
    }
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  private HostConfig buildHostConfig(DockerOperations.HostConfigSpec spec) {
    HostConfig hostConfig =
        HostConfig.newHostConfig()
            .withMemory(spec.memoryBytes())
            .withMemorySwap(spec.memorySwapBytes())
            .withNanoCPUs(spec.nanoCpus())
            .withPidsLimit((long) spec.pidsLimit())
            .withReadonlyRootfs(spec.readonlyRootfs())
            .withPrivileged(spec.privileged());

    if (spec.capDrop() != null && !spec.capDrop().isEmpty()) {
      hostConfig.withCapDrop(
          spec.capDrop().stream().map(Capability::valueOf).toArray(Capability[]::new));
    }

    if (spec.securityOpts() != null && !spec.securityOpts().isEmpty()) {
      hostConfig.withSecurityOpts(spec.securityOpts());
    }

    if (spec.tmpfsMounts() != null && !spec.tmpfsMounts().isEmpty()) {
      hostConfig.withTmpFs(spec.tmpfsMounts());
    }

    if (spec.dns() != null && !spec.dns().isEmpty()) {
      hostConfig.withDns(spec.dns().toArray(String[]::new));
    }

    if (spec.cgroupnsMode() != null) {
      hostConfig.withCgroupnsMode(spec.cgroupnsMode());
    }

    if (spec.ipcMode() != null) {
      hostConfig.withIpcMode(spec.ipcMode());
    }

    if (spec.runtime() != null && !spec.runtime().isBlank()) {
      hostConfig.withRuntime(spec.runtime());
    }

    if (spec.ulimits() != null && !spec.ulimits().isEmpty()) {
      hostConfig.withUlimits(
          spec.ulimits().entrySet().stream()
              .map(e -> new Ulimit(e.getKey(), e.getValue().soft(), e.getValue().hard()))
              .toArray(Ulimit[]::new));
    }

    return hostConfig;
  }
}
