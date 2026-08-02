package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boot-matrix proof for a split-pod, worker-only deployment: which BEANS wire under
 * {@code runtime.server.enabled=false, runtime.worker.enabled=true, agent.enabled=true}, mirroring the
 * {@code prod,worker} production profile (application-worker.yml) plus {@code AGENT_ENABLED=true}. The
 * values in {@code application-worker.yml} itself are read by
 * {@code de.tum.cit.aet.hephaestus.core.runtime.WorkerProfileOverlayTest}.
 */
@DisplayName("Worker-only role gating")
class AgentRoleGatingIntegrationTest extends BaseIntegrationTest {

    @DynamicPropertySource
    static void roleProperties(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.agent.enabled", () -> "true");
        registry.add("hephaestus.agent.poll-interval", () -> "1h");
        registry.add("hephaestus.runtime.server.enabled", () -> "false");
        registry.add("hephaestus.runtime.worker.enabled", () -> "true");
        registry.add("hephaestus.runtime.webhook.enabled", () -> "false");
        registry.add("hephaestus.sandbox.docker-host", () -> "unix:///nonexistent/hephaestus-test-role-gating.sock");
        registry.add("hephaestus.agent.image.pull-policy", () -> "NEVER");
        // Hermetic against ambient dev-machine `.env` overrides (spring.config.import: optional:file:.env
        // in application.yml): some Slack-role-agnostic beans (e.g. SlackIntegrationSyncRunner) hard-require
        // a server-role-gated collaborator (SlackDataSyncScheduler) when Slack is enabled — orthogonal to
        // what this test verifies, so pin every optional integration off explicitly rather than letting a
        // local .env's HEPHAESTUS_INTEGRATION_SLACK_ENABLED=true break context load here.
        registry.add("hephaestus.integration.slack.enabled", () -> "false");
        registry.add("hephaestus.integration.outline.enabled", () -> "false");
    }

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the poll-based executor and the LLM proxy chain both wire on a worker-only, agent-enabled pod")
    void executorAndProxyChainWireOnWorkerOnlyPod() {
        assertThat(context.getBeansOfType(AgentJobExecutor.class))
            .as("AgentJobExecutor gates on agent.enabled AND worker.enabled — both true here")
            .isNotEmpty();

        assertThat(context.containsBean("llmProxyController"))
            .as("LlmProxyController — the ONLY LLM credential path (ADR 0006) — must wire wherever jobs execute")
            .isTrue();
        assertThat(context.containsBean("llmProxyFilterChain"))
            .as("the dedicated /internal/llm/** security chain must wire alongside the controller")
            .isTrue();
    }

    @Test
    @DisplayName("the silent-mode read port wires without the server-only audit logger")
    void silentModeQueryWiresOnWorkerOnlyPod() {
        // Every outbound delivery consults this port, so it must boot here — while AuthEventLogger, which
        // its implementation writes the toggle's audit row through, is @ConditionalOnServerRole and absent.
        assertThat(context.getBeansOfType(SilentModeQuery.class))
            .as("delivery cannot consult a brake whose bean failed to wire")
            .isNotEmpty();
        assertThat(context.getBeansOfType(AuthEventLogger.class))
            .as("the audit logger is server-only; the settings bean must tolerate its absence")
            .isEmpty();
        assertThat(context.getBean(SilentModeQuery.class).isSilentModeEngaged()).isFalse();
    }

    @Test
    @DisplayName("submission listeners wire on agent.enabled alone, independent of the server role")
    void submissionBeansWireWithoutServerRole() {
        assertThat(context.getBeansOfType(AgentJobEventListener.class))
            .as("PR/review event listener — must submit jobs on any agent.enabled pod, not just server-role ones")
            .isNotEmpty();
        assertThat(context.getBeansOfType(IssueAgentJobEventListener.class)).isNotEmpty();
        assertThat(context.getBeansOfType(BotCommandProcessor.class)).isNotEmpty();
    }

    @Test
    @DisplayName("the zombie sweeper does NOT wire on a worker-only pod — sweeping is a server-role duty")
    void recoverySweeperIsAbsentWithoutServerRole() {
        assertThat(context.getBeansOfType(AgentJobZombieSweeper.class))
            .as("orphan/zombie sweeper is @ConditionalOnServerRole; its @Scheduled ticks cannot fire here anyway")
            .isEmpty();
    }
}
