package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ConnectToNetworkCmd;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.command.DisconnectFromNetworkCmd;
import com.github.dockerjava.api.command.InspectNetworkCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListNetworksCmd;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.RemoveNetworkCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Network;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("DockerClientOperations")
class DockerClientOperationsTest extends BaseUnitTest {

    @Mock
    private DockerClient dockerClient;

    private DockerClientOperations ops;

    @BeforeEach
    void setUp() {
        ops = new DockerClientOperations(dockerClient);
    }

    @Nested
    @DisplayName("ping")
    class Ping {

        @Test
        @DisplayName("should return true when daemon is reachable")
        void shouldReturnTrueOnSuccess() {
            PingCmd pingCmd = mock(PingCmd.class);
            when(dockerClient.pingCmd()).thenReturn(pingCmd);
            // PingCmd.exec() returns Void — default mock returns null, no stubbing needed

            assertThat(ops.ping()).isTrue();
        }

        @Test
        @DisplayName("should return false when daemon throws")
        void shouldReturnFalseOnException() {
            PingCmd pingCmd = mock(PingCmd.class);
            when(dockerClient.pingCmd()).thenReturn(pingCmd);
            when(pingCmd.exec()).thenThrow(new RuntimeException("Connection refused"));

            assertThat(ops.ping()).isFalse();
        }
    }

    @Nested
    @DisplayName("createNetwork")
    class CreateNetwork {

        @Test
        @DisplayName("should create internal network and return ID")
        void shouldCreateNetwork() {
            CreateNetworkCmd cmd = mock(CreateNetworkCmd.class);
            CreateNetworkResponse response = mock(CreateNetworkResponse.class);
            when(dockerClient.createNetworkCmd()).thenReturn(cmd);
            when(cmd.withName(anyString())).thenReturn(cmd);
            when(cmd.withDriver(anyString())).thenReturn(cmd);
            when(cmd.withInternal(true)).thenReturn(cmd);
            when(cmd.withCheckDuplicate(true)).thenReturn(cmd);
            when(cmd.exec()).thenReturn(response);
            when(response.getId()).thenReturn("net-abc");

            String id = ops.createNetwork("test-net", true);

            assertThat(id).isEqualTo("net-abc");
            verify(cmd).withInternal(true);
        }

        @Test
        @DisplayName("should throw SandboxException on Docker failure")
        void shouldThrowOnFailure() {
            CreateNetworkCmd cmd = mock(CreateNetworkCmd.class);
            when(dockerClient.createNetworkCmd()).thenReturn(cmd);
            when(cmd.withName(anyString())).thenReturn(cmd);
            when(cmd.withDriver(anyString())).thenReturn(cmd);
            when(cmd.withInternal(false)).thenReturn(cmd);
            when(cmd.withCheckDuplicate(true)).thenReturn(cmd);
            when(cmd.exec()).thenThrow(new DockerException("Network conflict", 409));

            assertThatThrownBy(() -> ops.createNetwork("dup-net", false))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("dup-net");
        }
    }

    @Nested
    @DisplayName("connectToNetwork")
    class ConnectToNetwork {

        @Test
        @DisplayName("should strip CIDR suffix from IP address")
        void shouldStripCidrSuffix() {
            ConnectToNetworkCmd connectCmd = mock(ConnectToNetworkCmd.class);
            when(dockerClient.connectToNetworkCmd()).thenReturn(connectCmd);
            when(connectCmd.withNetworkId("net-1")).thenReturn(connectCmd);
            when(connectCmd.withContainerId("ctr-1")).thenReturn(connectCmd);

            InspectNetworkCmd inspectCmd = mock(InspectNetworkCmd.class);
            when(dockerClient.inspectNetworkCmd()).thenReturn(inspectCmd);
            when(inspectCmd.withNetworkId("net-1")).thenReturn(inspectCmd);

            Network network = mock(Network.class);
            Network.ContainerNetworkConfig containerConfig = mock(Network.ContainerNetworkConfig.class);
            when(inspectCmd.exec()).thenReturn(network);
            when(network.getContainers()).thenReturn(Map.of("ctr-1", containerConfig));
            when(containerConfig.getIpv4Address()).thenReturn("172.18.0.2/16");

            String ip = ops.connectToNetwork("net-1", "ctr-1");

            assertThat(ip).isEqualTo("172.18.0.2");
        }

        @Test
        @DisplayName("should return IP without CIDR when no suffix")
        void shouldReturnIpWithoutCidr() {
            ConnectToNetworkCmd connectCmd = mock(ConnectToNetworkCmd.class);
            when(dockerClient.connectToNetworkCmd()).thenReturn(connectCmd);
            when(connectCmd.withNetworkId("net-1")).thenReturn(connectCmd);
            when(connectCmd.withContainerId("ctr-1")).thenReturn(connectCmd);

            InspectNetworkCmd inspectCmd = mock(InspectNetworkCmd.class);
            when(dockerClient.inspectNetworkCmd()).thenReturn(inspectCmd);
            when(inspectCmd.withNetworkId("net-1")).thenReturn(inspectCmd);

            Network network = mock(Network.class);
            Network.ContainerNetworkConfig containerConfig = mock(Network.ContainerNetworkConfig.class);
            when(inspectCmd.exec()).thenReturn(network);
            when(network.getContainers()).thenReturn(Map.of("ctr-1", containerConfig));
            when(containerConfig.getIpv4Address()).thenReturn("172.18.0.5");

            String ip = ops.connectToNetwork("net-1", "ctr-1");

            assertThat(ip).isEqualTo("172.18.0.5");
        }

        @Test
        @DisplayName("should throw when container not found in network after connect")
        void shouldThrowWhenContainerNotInNetwork() {
            ConnectToNetworkCmd connectCmd = mock(ConnectToNetworkCmd.class);
            when(dockerClient.connectToNetworkCmd()).thenReturn(connectCmd);
            when(connectCmd.withNetworkId("net-1")).thenReturn(connectCmd);
            when(connectCmd.withContainerId("ctr-1")).thenReturn(connectCmd);

            InspectNetworkCmd inspectCmd = mock(InspectNetworkCmd.class);
            when(dockerClient.inspectNetworkCmd()).thenReturn(inspectCmd);
            when(inspectCmd.withNetworkId("net-1")).thenReturn(inspectCmd);

            Network network = mock(Network.class);
            when(inspectCmd.exec()).thenReturn(network);
            when(network.getContainers()).thenReturn(Map.of());

            assertThatThrownBy(() -> ops.connectToNetwork("net-1", "ctr-1"))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("not found in network");
        }
    }

    @Nested
    @DisplayName("disconnectFromNetwork")
    class DisconnectFromNetwork {

        @Test
        @DisplayName("should be idempotent on NotFoundException")
        void shouldBeIdempotentOnNotFound() {
            DisconnectFromNetworkCmd cmd = mock(DisconnectFromNetworkCmd.class);
            when(dockerClient.disconnectFromNetworkCmd()).thenReturn(cmd);
            when(cmd.withNetworkId("net-1")).thenReturn(cmd);
            when(cmd.withContainerId("ctr-1")).thenReturn(cmd);
            when(cmd.withForce(true)).thenReturn(cmd);
            when(cmd.exec()).thenThrow(new NotFoundException("not found"));

            // Should not throw
            ops.disconnectFromNetwork("net-1", "ctr-1");
        }

        @Test
        @DisplayName("should be idempotent on NotModifiedException")
        void shouldBeIdempotentOnNotModified() {
            DisconnectFromNetworkCmd cmd = mock(DisconnectFromNetworkCmd.class);
            when(dockerClient.disconnectFromNetworkCmd()).thenReturn(cmd);
            when(cmd.withNetworkId("net-1")).thenReturn(cmd);
            when(cmd.withContainerId("ctr-1")).thenReturn(cmd);
            when(cmd.withForce(true)).thenReturn(cmd);
            when(cmd.exec()).thenThrow(new NotModifiedException("already disconnected"));

            // Should not throw
            ops.disconnectFromNetwork("net-1", "ctr-1");
        }
    }

    @Nested
    @DisplayName("removeNetwork")
    class RemoveNetwork {

        @Test
        @DisplayName("should be idempotent on NotFoundException")
        void shouldBeIdempotentOnNotFound() {
            RemoveNetworkCmd cmd = mock(RemoveNetworkCmd.class);
            when(dockerClient.removeNetworkCmd("net-gone")).thenReturn(cmd);
            when(cmd.exec()).thenThrow(new NotFoundException("not found"));

            // Should not throw
            ops.removeNetwork("net-gone");
        }
    }

    @Nested
    @DisplayName("stopContainer")
    class StopContainer {

        @Test
        @DisplayName("should be idempotent on NotModifiedException")
        void shouldBeIdempotentOnAlreadyStopped() {
            StopContainerCmd cmd = mock(StopContainerCmd.class);
            when(dockerClient.stopContainerCmd("ctr-1")).thenReturn(cmd);
            when(cmd.withTimeout(10)).thenReturn(cmd);
            when(cmd.exec()).thenThrow(new NotModifiedException("already stopped"));

            // Should not throw
            ops.stopContainer("ctr-1", 10);
        }

        @Test
        @DisplayName("should throw SandboxException on Docker failure")
        void shouldThrowOnFailure() {
            StopContainerCmd cmd = mock(StopContainerCmd.class);
            when(dockerClient.stopContainerCmd("ctr-1")).thenReturn(cmd);
            when(cmd.withTimeout(10)).thenReturn(cmd);
            when(cmd.exec()).thenThrow(new DockerException("server error", 500));

            assertThatThrownBy(() -> ops.stopContainer("ctr-1", 10))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("ctr-1");
        }
    }

    @Nested
    @DisplayName("removeContainer")
    class RemoveContainer {

        @Test
        @DisplayName("should be idempotent on NotFoundException")
        void shouldBeIdempotentOnNotFound() {
            RemoveContainerCmd cmd = mock(RemoveContainerCmd.class);
            when(dockerClient.removeContainerCmd("ctr-gone")).thenReturn(cmd);
            when(cmd.withForce(true)).thenReturn(cmd);
            when(cmd.withRemoveVolumes(true)).thenReturn(cmd);
            when(cmd.exec()).thenThrow(new NotFoundException("not found"));

            // Should not throw
            ops.removeContainer("ctr-gone", true);
        }
    }

    @Nested
    @DisplayName("listContainersByLabel")
    class ListContainersByLabel {

        @Test
        @DisplayName("should map container fields correctly")
        void shouldMapContainerFields() {
            ListContainersCmd cmd = mock(ListContainersCmd.class);
            when(dockerClient.listContainersCmd()).thenReturn(cmd);
            when(cmd.withLabelFilter(any(Map.class))).thenReturn(cmd);
            when(cmd.withShowAll(true)).thenReturn(cmd);

            Container container = mock(Container.class);
            when(container.getId()).thenReturn("ctr-1");
            when(container.getNames()).thenReturn(new String[] { "/my-container" });
            when(container.getLabels()).thenReturn(Map.of("key", "value"));
            when(container.getState()).thenReturn("running");

            when(cmd.exec()).thenReturn(List.of(container));

            var results = ops.listContainersByLabel("hephaestus.managed", "true");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo("ctr-1");
            assertThat(results.get(0).name()).isEqualTo("/my-container");
            assertThat(results.get(0).labels()).containsEntry("key", "value");
            assertThat(results.get(0).state()).isEqualTo("running");
        }

        @Test
        @DisplayName("should handle null names and labels gracefully")
        void shouldHandleNullFields() {
            ListContainersCmd cmd = mock(ListContainersCmd.class);
            when(dockerClient.listContainersCmd()).thenReturn(cmd);
            when(cmd.withLabelFilter(any(Map.class))).thenReturn(cmd);
            when(cmd.withShowAll(true)).thenReturn(cmd);

            Container container = mock(Container.class);
            when(container.getId()).thenReturn("ctr-2");
            when(container.getNames()).thenReturn(null);
            when(container.getLabels()).thenReturn(null);
            when(container.getState()).thenReturn(null);

            when(cmd.exec()).thenReturn(List.of(container));

            var results = ops.listContainersByLabel("hephaestus.managed", "true");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).name()).isEmpty();
            assertThat(results.get(0).labels()).isEmpty();
            assertThat(results.get(0).state()).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("listNetworksByName")
    class ListNetworksByName {

        @Test
        @DisplayName("should map network fields correctly")
        void shouldMapNetworkFields() {
            ListNetworksCmd cmd = mock(ListNetworksCmd.class);
            when(dockerClient.listNetworksCmd()).thenReturn(cmd);
            when(cmd.withNameFilter(anyString())).thenReturn(cmd);

            Network network = mock(Network.class);
            when(network.getId()).thenReturn("net-1");
            when(network.getName()).thenReturn("agent-net-abc");

            when(cmd.exec()).thenReturn(List.of(network));

            var results = ops.listNetworksByName("agent-net-");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo("net-1");
            assertThat(results.get(0).name()).isEqualTo("agent-net-abc");
        }
    }

    @Nested
    @DisplayName("copyArchive")
    class CopyArchive {

        @Test
        @DisplayName("should delegate copyArchiveToContainer to docker client")
        void shouldCopyToContainer() {
            CopyArchiveToContainerCmd cmd = mock(CopyArchiveToContainerCmd.class);
            when(dockerClient.copyArchiveToContainerCmd("ctr-1")).thenReturn(cmd);
            when(cmd.withRemotePath("/workspace")).thenReturn(cmd);
            when(cmd.withTarInputStream(any())).thenReturn(cmd);

            InputStream tarStream = new ByteArrayInputStream(new byte[0]);
            ops.copyArchiveToContainer("ctr-1", "/workspace", tarStream);

            verify(cmd).exec();
        }

        @Test
        @DisplayName("should throw SandboxException on copyArchiveToContainer failure")
        void shouldThrowOnCopyToFailure() {
            CopyArchiveToContainerCmd cmd = mock(CopyArchiveToContainerCmd.class);
            when(dockerClient.copyArchiveToContainerCmd("ctr-1")).thenReturn(cmd);
            when(cmd.withRemotePath("/workspace")).thenReturn(cmd);
            when(cmd.withTarInputStream(any())).thenReturn(cmd);
            when(cmd.exec()).thenThrow(new DockerException("container not running", 409));

            InputStream tarStream = new ByteArrayInputStream(new byte[0]);
            assertThatThrownBy(() -> ops.copyArchiveToContainer("ctr-1", "/workspace", tarStream))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("ctr-1");
        }

        @Test
        @DisplayName("should throw SandboxException on copyArchiveFromContainer failure")
        void shouldThrowOnCopyFromFailure() {
            CopyArchiveFromContainerCmd cmd = mock(CopyArchiveFromContainerCmd.class);
            when(dockerClient.copyArchiveFromContainerCmd("ctr-1", "/output")).thenReturn(cmd);
            when(cmd.exec()).thenThrow(new DockerException("No such path", 404));

            assertThatThrownBy(() -> ops.copyArchiveFromContainer("ctr-1", "/output"))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("/output");
        }
    }

    @Nested
    @DisplayName("createContainer")
    class CreateContainer {

        @Test
        @DisplayName("should create container with full spec")
        void shouldCreateWithFullSpec() {
            CreateContainerCmd cmd = mock(CreateContainerCmd.class);
            CreateContainerResponse response = mock(CreateContainerResponse.class);
            when(dockerClient.createContainerCmd("alpine:latest")).thenReturn(cmd);
            when(cmd.withHostConfig(any())).thenReturn(cmd);
            when(cmd.withNetworkMode(anyString())).thenReturn(cmd);
            when(cmd.withLabels(any())).thenReturn(cmd);
            when(cmd.withCmd(any(List.class))).thenReturn(cmd);
            when(cmd.withEnv(any(List.class))).thenReturn(cmd);
            when(cmd.withHostName("agent")).thenReturn(cmd);
            when(cmd.withUser("1000:1000")).thenReturn(cmd);
            when(cmd.exec()).thenReturn(response);
            when(response.getId()).thenReturn("new-ctr");

            DockerOperations.ContainerSpec spec = new DockerOperations.ContainerSpec(
                "alpine:latest",
                List.of("echo", "hello"),
                Map.of("FOO", "bar"),
                "net-123",
                "agent",
                "1000:1000",
                Map.of("label", "value"),
                new DockerOperations.HostConfigSpec(
                    4L * 1024 * 1024 * 1024,
                    4L * 1024 * 1024 * 1024,
                    2_000_000_000L,
                    256,
                    true,
                    false,
                    List.of("ALL"),
                    List.of("no-new-privileges"),
                    Map.of("/tmp", "rw,noexec"),
                    List.of("0.0.0.0"),
                    "private",
                    "none",
                    null,
                    Map.of()
                )
            );

            String id = ops.createContainer(spec);

            assertThat(id).isEqualTo("new-ctr");
            verify(cmd).withCmd(List.of("echo", "hello"));
            verify(cmd).withHostName("agent");
            verify(cmd).withUser("1000:1000");
        }

        @Test
        @DisplayName("should skip optional fields when null/empty")
        void shouldSkipOptionalFields() {
            CreateContainerCmd cmd = mock(CreateContainerCmd.class);
            CreateContainerResponse response = mock(CreateContainerResponse.class);
            when(dockerClient.createContainerCmd("alpine:latest")).thenReturn(cmd);
            when(cmd.withHostConfig(any())).thenReturn(cmd);
            when(cmd.withNetworkMode(anyString())).thenReturn(cmd);
            when(cmd.withLabels(any())).thenReturn(cmd);
            when(cmd.exec()).thenReturn(response);
            when(response.getId()).thenReturn("new-ctr");

            DockerOperations.ContainerSpec spec = new DockerOperations.ContainerSpec(
                "alpine:latest",
                List.of(), // empty command
                Map.of(), // empty env
                "net-123",
                null, // no hostname
                null, // no user
                Map.of(),
                new DockerOperations.HostConfigSpec(
                    1024L,
                    1024L,
                    1_000_000_000L,
                    100,
                    false,
                    false,
                    List.of(),
                    List.of(),
                    Map.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    Map.of()
                )
            );

            ops.createContainer(spec);

            // cmd, env, hostname, user should NOT be called
            verify(cmd, never()).withCmd(any(List.class));
            verify(cmd, never()).withEnv(any(List.class));
            verify(cmd, never()).withHostName(anyString());
            verify(cmd, never()).withUser(anyString());
        }
    }

    @Nested
    @DisplayName("waitContainer")
    class WaitContainer {

        @Test
        @DisplayName("should return exit code from wait")
        void shouldReturnExitCode() {
            WaitContainerCmd cmd = mock(WaitContainerCmd.class);
            WaitContainerResultCallback callback = mock(WaitContainerResultCallback.class);
            when(dockerClient.waitContainerCmd("ctr-1")).thenReturn(cmd);
            when(cmd.start()).thenReturn(callback);
            when(callback.awaitStatusCode()).thenReturn(42);

            DockerOperations.WaitResult result = ops.waitContainer("ctr-1");

            assertThat(result.exitCode()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("startContainer")
    class StartContainer {

        @Test
        @DisplayName("should throw SandboxException on failure")
        void shouldThrowOnStartFailure() {
            StartContainerCmd cmd = mock(StartContainerCmd.class);
            when(dockerClient.startContainerCmd("ctr-bad")).thenReturn(cmd);
            when(cmd.exec()).thenThrow(new DockerException("no such container", 404));

            assertThatThrownBy(() -> ops.startContainer("ctr-bad"))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("ctr-bad");
        }
    }
}
