package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Property-driven boot-matrix proof for a split-pod, worker-only deployment (#1368 fix wave,
 * adversarial review findings 1 + 3) — no Docker, no real WSS hub: this is entirely about which
 * BEANS wire under {@code hephaestus.runtime.server.enabled=false, hephaestus.runtime.worker.enabled
 * =true, hephaestus.agent.enabled=true}, mirroring the {@code prod,worker} production profile
 * (application-worker.yml) plus {@code AGENT_ENABLED=true}.
 *
 * <p><b>Finding 1 (worker can't serve the LLM proxy):</b> {@code application-worker.yml} used to set
 * {@code server.port=-1}, which disables the HTTP connector entirely — {@code LlmProxyController} and
 * its {@code llmProxyFilterChain} security chain existed as beans but could never actually be reached
 * over the network, so every sandboxed job this pod claimed would fail to reach its LLM. The profile
 * now sets a real {@code server.port}; this test proves the proxy chain's BEANS wire under the
 * property set the profile produces (a real TCP bind is an infra/Docker-Compose concern, out of scope
 * for a unit-tier Spring context test).
 *
 * <p><b>Finding 3 (split-role gating):</b> the job-submission listeners and the orphan-recovery
 * sweeper gate on {@code hephaestus.agent.enabled} alone — NOT on the worker role — so they wire here
 * even with {@code runtime.server.enabled=false}. This is what makes "set AGENT_ENABLED on every role
 * that needs it" (MIGRATION.md) actually correct: the flag alone, independent of which runtime role(s)
 * are on, is what the submission/recovery beans key on.
 */
@DisplayName("Worker-only role gating (#1368 fix wave)")
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
    @DisplayName(
        "submission listeners and the orphan-recovery sweeper wire on agent.enabled alone, independent of the server role"
    )
    void submissionAndRecoveryBeansWireWithoutServerRole() {
        assertThat(context.getBeansOfType(AgentJobEventListener.class))
            .as("PR/review event listener — must submit jobs on any agent.enabled pod, not just server-role ones")
            .isNotEmpty();
        assertThat(context.getBeansOfType(IssueAgentJobEventListener.class)).isNotEmpty();
        assertThat(context.getBeansOfType(BotCommandProcessor.class)).isNotEmpty();
        assertThat(context.getBeansOfType(AgentJobZombieSweeper.class))
            .as(
                "orphan-recovery sweeper — must be present so a server pod running only AGENT_ENABLED still recovers dead workers' jobs"
            )
            .isNotEmpty();
    }
}
