package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("SandboxNetworkManager")
class SandboxNetworkManagerTest extends BaseUnitTest {

  @Mock private DockerNetworkOperations networkOps;

  private SandboxNetworkManager manager;

  private static final UUID JOB_ID = UUID.randomUUID();
  private static final String NETWORK_ID = "net-abc123";

  @BeforeEach
  void setUp() {
    SandboxProperties properties =
        new SandboxProperties(
            true,
            "unix:///var/run/docker.sock",
            false,
            null,
            5,
            10,
            60,
            null,
            8080,
            "app-server-id",
            null);
    manager = new SandboxNetworkManager(networkOps, properties);
  }

  @Nested
  @DisplayName("createJobNetwork")
  class CreateJobNetwork {

    @Test
    @DisplayName("should create internal network when internet disabled")
    void shouldCreateInternalNetwork() {
      when(networkOps.createNetwork(anyString(), eq(true))).thenReturn(NETWORK_ID);

      String networkId = manager.createJobNetwork(JOB_ID, false);

      assertThat(networkId).isEqualTo(NETWORK_ID);
      verify(networkOps).createNetwork("agent-net-" + JOB_ID, true);
    }

    @Test
    @DisplayName("should create bridge network when internet enabled")
    void shouldCreateBridgeNetwork() {
      when(networkOps.createNetwork(anyString(), eq(false))).thenReturn(NETWORK_ID);

      String networkId = manager.createJobNetwork(JOB_ID, true);

      assertThat(networkId).isEqualTo(NETWORK_ID);
      verify(networkOps).createNetwork("agent-net-" + JOB_ID, false);
    }
  }

  @Nested
  @DisplayName("connectAppServer")
  class ConnectAppServer {

    @Test
    @DisplayName("should connect app-server and return IP")
    void shouldConnectAndReturnIp() {
      when(networkOps.connectToNetwork(NETWORK_ID, "app-server-id")).thenReturn("172.18.0.2");

      String ip = manager.connectAppServer(NETWORK_ID);

      assertThat(ip).isEqualTo("172.18.0.2");
    }

    @Test
    @DisplayName("should fall back to HOSTNAME when config has no container ID")
    void shouldFallBackToHostname() {
      SandboxProperties propsNoId =
          new SandboxProperties(
              true, "unix:///var/run/docker.sock", false, null, 5, 10, 60, null, 8080, null, null);
      SandboxNetworkManager mgr =
          new SandboxNetworkManager(networkOps, propsNoId, () -> "hostname-container-id");

      when(networkOps.connectToNetwork(NETWORK_ID, "hostname-container-id"))
          .thenReturn("172.18.0.3");

      String ip = mgr.connectAppServer(NETWORK_ID);

      assertThat(ip).isEqualTo("172.18.0.3");
      verify(networkOps).connectToNetwork(NETWORK_ID, "hostname-container-id");
    }

    @Test
    @DisplayName("should throw when container ID cannot be resolved")
    void shouldThrowWhenNoContainerId() {
      SandboxProperties propsNoId =
          new SandboxProperties(
              true, "unix:///var/run/docker.sock", false, null, 5, 10, 60, null, 8080, null, null);
      SandboxNetworkManager mgr = new SandboxNetworkManager(networkOps, propsNoId, () -> null);

      assertThatThrownBy(() -> mgr.connectAppServer(NETWORK_ID))
          .isInstanceOf(SandboxException.class)
          .hasMessageContaining("Cannot determine app-server container ID");
    }
  }

  @Nested
  @DisplayName("disconnectAppServer")
  class DisconnectAppServer {

    @Test
    @DisplayName("should disconnect app-server from network")
    void shouldDisconnect() {
      manager.disconnectAppServer(NETWORK_ID);

      verify(networkOps).disconnectFromNetwork(NETWORK_ID, "app-server-id");
    }
  }

  @Nested
  @DisplayName("removeNetwork")
  class RemoveNetwork {

    @Test
    @DisplayName("should remove network by ID")
    void shouldRemoveNetwork() {
      manager.removeNetwork(NETWORK_ID);

      verify(networkOps).removeNetwork(NETWORK_ID);
    }
  }

  @Nested
  @DisplayName("listOrphanedNetworks")
  class ListOrphanedNetworks {

    @Test
    @DisplayName("should list networks with agent-net- prefix")
    void shouldListByPrefix() {
      when(networkOps.listNetworksByName("agent-net-"))
          .thenReturn(List.of(new DockerOperations.NetworkInfo("n1", "agent-net-" + JOB_ID)));

      var networks = manager.listOrphanedNetworks();

      assertThat(networks).hasSize(1);
    }
  }
}
