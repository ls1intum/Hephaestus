package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.gateway.SandboxGatewaySessions;
import de.tum.cit.aet.hephaestus.agent.sandbox.SandboxHosts;
import de.tum.cit.aet.hephaestus.agent.sandbox.SandboxWorkspaceManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.HostSandbox;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

/** {@code SandboxHosts} on the Docker backend: what a Heph host gets from Docker, and what its failure leaves. */
class DockerSandboxHostsTest extends BaseUnitTest {

    private static final HostSandbox.HostSpec SPEC = new HostSandbox.HostSpec(
            Map.of(),
            Map.of(),
            List.of("node"),
            new ResourceLimits(256L * 1024 * 1024, 1.0, 64, Duration.ofMinutes(1)));

    @Mock
    private SandboxNetworkManager networks;

    @Mock
    private SandboxWorkspaceManager workspaces;

    @Mock
    private SandboxContainerManager containers;

    @Mock
    private ContainerSecurityPolicy securityPolicy;

    @Mock
    private SandboxCreator creator;

    @Mock
    private SandboxGatewaySessions gatewaySessions;

    @Mock
    private DockerVolumeOperations volumes;

    @TempDir
    Path temp;

    private SandboxGatewaySessions.Session session;
    private SandboxHosts sandbox;

    @BeforeEach
    void setUp() throws IOException {
        sandbox = new SandboxHosts(
                new DockerSandboxBackend(
                        networks,
                        containers,
                        securityPolicy,
                        volumes,
                        creator,
                        "worker-1",
                        8081,
                        new SimpleMeterRegistry()),
                workspaces,
                gatewaySessions,
                "heph-host:test");
        when(workspaces.createInputTar(any(), any())).thenReturn(Files.createFile(temp.resolve("input.tar")));
        session = mock(SandboxGatewaySessions.Session.class);
        when(session.id()).thenReturn(UUID.randomUUID());
        when(gatewaySessions.registerHost(any(), any(), any(), any())).thenReturn(session);
    }

    @Test
    void shouldStartNoHostWhenTheWorkerRunsInAContainerThatItCannotIdentify() throws IOException {
        when(creator.labels()).thenReturn(Map.of());
        when(creator.outsideContainer()).thenReturn(false);

        assertThatThrownBy(() -> sandbox.launch(SPEC)).isInstanceOf(IOException.class);

        verify(networks, never()).createJobNetwork(any(), anyBoolean(), any());
        verify(session).close();
    }

    @Test
    void shouldGiveTheHostANetworkWithARouteOutOnlyWhenTheWorkerRunsOutsideAnyContainer() {
        when(creator.labels()).thenReturn(Map.of());
        when(creator.outsideContainer()).thenReturn(true);
        when(networks.createJobNetwork(any(), anyBoolean(), any())).thenThrow(new IllegalStateException("stop here"));

        assertThatThrownBy(() -> sandbox.launch(SPEC)).hasMessage("stop here");

        verify(networks).createJobNetwork(any(), eq(true), any());
    }

    @Test
    void shouldGiveTheHostAnInternalNetworkWhenTheWorkerKnowsItsContainer() {
        when(creator.labels())
                .thenReturn(Map.of(
                        SandboxLabels.CREATOR_CONTAINER, "3f2a9c1b7d4e", SandboxLabels.CREATOR_STARTED_AT, "t1"));
        when(networks.createJobNetwork(any(), anyBoolean(), any())).thenThrow(new IllegalStateException("stop here"));

        assertThatThrownBy(() -> sandbox.launch(SPEC)).hasMessage("stop here");

        verify(networks).createJobNetwork(any(), eq(false), any());
    }

    /**
     * The host's output can hold anything Pi, Node or the host wrote, so its removal reads none of it. Everything the
     * host ran on is still removed.
     */
    @Test
    void shouldRemoveEverythingTheHostRanOnAndReadNoneOfItsOutputWhenItFailsToStart() throws Exception {
        when(creator.labels())
                .thenReturn(Map.of(
                        SandboxLabels.CREATOR_CONTAINER, "3f2a9c1b7d4e", SandboxLabels.CREATOR_STARTED_AT, "t1"));
        when(networks.createJobNetwork(any(), anyBoolean(), any())).thenReturn("net-1");
        when(networks.connectAppServer("net-1")).thenReturn("10.0.0.2");
        when(securityPolicy.buildHostConfig(any(), any(), anyBoolean()))
                .thenReturn(new DockerOperations.HostConfigSpec(
                        0, 0, 0, 0, true, false, List.of(), List.of(), Map.of(), List.of(), null, null, null, Map.of(),
                        List.of()));
        when(containers.createContainer(any())).thenReturn("initializer-1", "host-1");
        when(containers.waitForCompletion(eq("initializer-1"), any()))
                .thenReturn(new SandboxContainerManager.WaitOutcome(0, false));
        doNothing().when(containers).startContainer("initializer-1");
        doThrow(new IllegalStateException("stop here")).when(containers).startContainer("host-1");

        assertThatThrownBy(() -> sandbox.launch(SPEC)).hasMessage("stop here");

        verify(containers).forceRemove("host-1");
        verify(volumes, atLeastOnce()).removeVolume(any());
        verify(session).close();
        verify(networks).removeNetwork("net-1");
        verify(containers, never()).getLogs(any(), anyInt());
    }
}
