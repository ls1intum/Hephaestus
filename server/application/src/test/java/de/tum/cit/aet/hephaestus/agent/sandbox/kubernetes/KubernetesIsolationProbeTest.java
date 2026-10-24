package de.tum.cit.aet.hephaestus.agent.sandbox.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.sandbox.ImagePullPolicy;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The isolation probe's verdicts. Its Pods run only on a live cluster, so here the probe reads the lines a probe Pod
 * prints and the answers an API server gives to the dry run; the live qualification runs the Pods.
 */
@Tag("unit")
@EnableKubernetesMockClient
class KubernetesIsolationProbeTest {

    private static final String SANDBOXES = "hephaestus-sandboxes";
    private static final String PODS = "/api/v1/namespaces/" + SANDBOXES + "/pods?dryRun=All";

    private static final List<KubernetesIsolationProbe.Target> TARGETS = List.of(
            new KubernetesIsolationProbe.Target("gateway", "10.42.0.7", 8081, true),
            new KubernetesIsolationProbe.Target("Kubernetes API", "10.43.0.1", 443, false));

    private @Nullable KubernetesMockServer server;
    private @Nullable KubernetesClient client;

    @Test
    void shouldPassWhenOnlyTheGatewayAnswersAndTheNodesBoundTheProcesses() {
        String line =
                "{\"reached\":{\"gateway\":true,\"Kubernetes API\":false},\"spawned\":243,\"spawnError\":\"EAGAIN\"}";

        assertThat(KubernetesIsolationProbe.judge("review", TARGETS, 256, line)).isEmpty();
    }

    @Test
    void shouldFailWhenADeniedTargetAnswersOrTheGatewayDoesNot() {
        String line =
                "{\"reached\":{\"gateway\":false,\"Kubernetes API\":true},\"spawned\":10,\"spawnError\":\"EAGAIN\"}";

        assertThat(KubernetesIsolationProbe.judge("review", TARGETS, 256, line))
                .containsExactly(
                        "a review Pod does not reach the gateway (10.42.0.7:8081)",
                        "a review Pod reaches the Kubernetes API (10.43.0.1:443)");
    }

    @Test
    void shouldFailWhenThePodStartsAsManyProcessesAsItWasAskedFor() {
        String unbounded =
                "{\"reached\":{\"gateway\":true,\"Kubernetes API\":false},\"spawned\":256,\"spawnError\":null}";
        String otherError =
                "{\"reached\":{\"gateway\":true,\"Kubernetes API\":false},\"spawned\":3,\"spawnError\":\"ENOENT\"}";

        assertThat(KubernetesIsolationProbe.judge("review", TARGETS, 256, unbounded))
                .hasSize(1);
        assertThat(KubernetesIsolationProbe.judge("review", TARGETS, 256, otherError))
                .as("a process that could not start for another reason proves no limit")
                .hasSize(1);
    }

    @Test
    void shouldFailWhenThePodPrintsNoResultOrSkipsATarget() {
        assertThat(KubernetesIsolationProbe.judge("review", TARGETS, 0, "node: not found"))
                .containsExactly("the review probe Pod printed no result");
        assertThat(KubernetesIsolationProbe.judge("review", TARGETS, 0, "{\"reached\":{\"gateway\":true}}"))
                .containsExactly("the review probe did not try the Kubernetes API");
    }

    @Test
    void shouldAcceptOnlyARestrictedPodSecurityRefusalOfTheDryRun() {
        KubernetesMockServer api = Objects.requireNonNull(server);
        api.expect()
                .post()
                .withPath(PODS)
                .andReturn(
                        403,
                        new StatusBuilder()
                                .withCode(403)
                                .withReason("Forbidden")
                                .withMessage("pods \"probe\" is forbidden: violates PodSecurity \"restricted:latest\":"
                                        + " allowPrivilegeEscalation != false")
                                .build())
                .once();
        api.expect()
                .post()
                .withPath(PODS)
                .andReturn(
                        403,
                        new StatusBuilder()
                                .withCode(403)
                                .withReason("Forbidden")
                                .withMessage("pods is forbidden: User cannot create resource")
                                .build())
                .once();
        api.expect().post().withPath(PODS).andReturn(201, "{}").once();
        KubernetesIsolationProbe probe = probe();

        assertThat(probe.refusesUnrestrictedPods()).isEmpty();
        assertThat(probe.refusesUnrestrictedPods())
                .as("an RBAC denial is no Pod Security refusal")
                .isPresent();
        assertThat(probe.refusesUnrestrictedPods())
                .hasValueSatisfying(failure -> assertThat(failure).contains("admits a Pod"));
    }

    private KubernetesIsolationProbe probe() {
        KubernetesClient api = Objects.requireNonNull(client);
        var worker = new KubernetesWorker(
                new KubernetesSandboxProperties(SANDBOXES, "hephaestus-worker-0", "10.42.0.7", 256, "", ""), 8081);
        return new KubernetesIsolationProbe(
                api, new KubernetesPods(api, worker, 10, ImagePullPolicy.IF_NOT_PRESENT), worker, "agent", 8080);
    }
}
