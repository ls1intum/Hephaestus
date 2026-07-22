package de.tum.cit.aet.hephaestus.agent.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Proves mentor infrastructure remains usable when practice-job execution is disabled. */
class MentorRoleGatingIntegrationTest extends BaseIntegrationTest {

    @DynamicPropertySource
    static void roleProperties(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.agent.enabled", () -> "false");
        registry.add("hephaestus.runtime.server.enabled", () -> "true");
        registry.add("hephaestus.runtime.worker.enabled", () -> "true");
        registry.add("hephaestus.runtime.webhook.enabled", () -> "false");
        registry.add("hephaestus.sandbox.docker-host", () -> "unix:///nonexistent/mentor-role-gating.sock");
        registry.add("hephaestus.agent.image.pull-policy", () -> "NEVER");
        registry.add("hephaestus.integration.slack.enabled", () -> "false");
        registry.add("hephaestus.integration.outline.enabled", () -> "false");
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void mentorSandboxAndProxyWireWithoutPracticeJobs() {
        assertThat(context.getBeansOfType(InteractiveSandboxService.class)).hasSize(1);
        assertThat(context.containsBean("llmProxyController")).isTrue();
        assertThat(context.containsBean("llmProxyFilterChain")).isTrue();
    }
}
