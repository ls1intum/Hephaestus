package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SlackManifestTemplateTest extends BaseUnitTest {

    private static final Path MANIFEST = Path.of("..", "docs", "admin", "slack-app-manifest-template.yml");

    @Test
    void manifestUsesAgentViewEventsNotLegacyAssistantViewEvents() throws Exception {
        String manifest = Files.readString(MANIFEST, StandardCharsets.UTF_8);

        assertThat(manifest)
            .contains("agent_view:")
            .contains("agent_description:")
            .contains("- app_home_opened")
            .contains("- app_context_changed")
            .contains("- message.im")
            .doesNotContain("- message.app_home")
            .doesNotContain("assistant_view:")
            .doesNotContain("assistant_thread_started")
            .doesNotContain("assistant_thread_context_changed")
            .doesNotContain("latest pull request")
            .doesNotContain("most recent pull request")
            .contains("What needs attention?"); // suggested-prompts anchor
    }
}
